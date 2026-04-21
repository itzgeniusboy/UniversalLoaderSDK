#include <jni.h>
#include <string>
#include <vector>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/user.h>
#include <sys/uio.h>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <dlfcn.h>
#include <android/log.h>
#include <elf.h>

#define TAG "OneCoreInjector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#ifndef NT_PRSTATUS
#define NT_PRSTATUS 1
#endif

/**
 * Ptrace-based Native Injection for OneCore SDK.
 * Supports ARM64 architecture (Standard for BGMI/PUBG).
 */

// Prototypes
long ptrace_read(pid_t pid, unsigned long addr, void* buf, int len);
long ptrace_write(pid_t pid, unsigned long addr, void* buf, int len);
void* get_module_base(pid_t pid, const char* module_name);
void* get_remote_addr(pid_t pid, const char* module_name, void* local_addr);

extern "C" JNIEXPORT jint JNICALL
Java_com_onecore_sdk_NativeInjector_injectSo(JNIEnv* env, jclass clazz, jint pid, jstring path) {
    const char* libPath = env->GetStringUTFChars(path, nullptr);
    pid_t target_pid = (pid_t)pid;

    LOGI("Starting remote injection: PID=%d, LIB=%s", target_pid, libPath);

    // 1. Attach to process
    if (ptrace(PTRACE_ATTACH, target_pid, NULL, NULL) < 0) {
        LOGE("ptrace_attach failed");
        env->ReleaseStringUTFChars(path, libPath);
        return -1;
    }
    waitpid(target_pid, NULL, WUNTRACED);
    LOGI("Attached to %d", target_pid);

    // 2. Get Registers
    struct user_pt_regs regs;
    struct iovec iov;
    iov.iov_base = &regs;
    iov.iov_len = sizeof(regs);
    if (ptrace(PTRACE_GETREGSET, target_pid, (void*)NT_PRSTATUS, &iov) < 0) {
        LOGE("ptrace_getregs failed");
        ptrace(PTRACE_DETACH, target_pid, NULL, NULL);
        env->ReleaseStringUTFChars(path, libPath);
        return -2;
    }

    struct user_pt_regs old_regs = regs;

    // 3. Find dlopen address in remote process
    // On modern Android, dlopen is in libdl.so or linker64
    void* remote_dlopen = get_remote_addr(target_pid, "libdl.so", (void*)dlopen);
    if (!remote_dlopen) {
        remote_dlopen = get_remote_addr(target_pid, "linker64", (void*)dlopen);
    }

    if (!remote_dlopen) {
        LOGE("Could not find remote dlopen");
        ptrace(PTRACE_DETACH, target_pid, NULL, NULL);
        env->ReleaseStringUTFChars(path, libPath);
        return -3;
    }
    LOGI("Remote dlopen found at %p", remote_dlopen);

    // 4. Map memory for libPath in remote process (or use existing heap)
    // Simplified for OneCore Loader: We reuse a safe area or use mmap
    // For this implementation, we'll write the string after the current SP - 1024
    unsigned long remote_str_addr = regs.sp - 1024;
    ptrace_write(target_pid, remote_str_addr, (void*)libPath, strlen(libPath) + 1);

    // 5. Setup call parameters for ARM64 (x0-x7)
    // dlopen(path, RTLD_NOW)
    regs.regs[0] = remote_str_addr; // x0 = const char *filename
    regs.regs[1] = RTLD_NOW;        // x1 = int flag
    regs.pc = (unsigned long)remote_dlopen;
    regs.regs[30] = 0;              // lr = 0 to trigger crash/exit upon completion

    // 6. Execute
    ptrace(PTRACE_SETREGSET, target_pid, (void*)NT_PRSTATUS, &iov);
    ptrace(PTRACE_CONT, target_pid, NULL, NULL);
    waitpid(target_pid, NULL, WUNTRACED);

    // 7. Cleanup
    ptrace(PTRACE_SETREGSET, target_pid, (void*)NT_PRSTATUS, &iov); // Restore regs
    ptrace(PTRACE_DETACH, target_pid, NULL, NULL);

    LOGI("Injection sequence complete.");
    env->ReleaseStringUTFChars(path, libPath);
    return 0;
}

// Utility Implementations
void* get_module_base(pid_t pid, const char* module_name) {
    char path[256];
    char line[1024];
    unsigned long addr = 0;

    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE* fp = fopen(path, "r");
    if (fp) {
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, module_name)) {
                addr = strtoul(line, NULL, 16);
                break;
            }
        }
        fclose(fp);
    }
    return (void*)addr;
}

void* get_remote_addr(pid_t pid, const char* module_name, void* local_addr) {
    void* local_base = get_module_base(getpid(), module_name);
    void* remote_base = get_module_base(pid, module_name);
    if (!local_base || !remote_base) return NULL;
    return (void*)((unsigned long)remote_base + ((unsigned long)local_addr - (unsigned long)local_base));
}

long ptrace_read(pid_t pid, unsigned long addr, void* buf, int len) {
    unsigned long* p = (unsigned long*)buf;
    int words = len / sizeof(unsigned long);
    for (int i = 0; i < words; i++, p++, addr += sizeof(unsigned long)) {
        *p = ptrace(PTRACE_PEEKDATA, pid, addr, NULL);
    }
    return 0;
}

long ptrace_write(pid_t pid, unsigned long addr, void* buf, int len) {
    unsigned long* p = (unsigned long*)buf;
    int words = len / sizeof(unsigned long);
    for (int i = 0; i < words; i++, p++, addr += sizeof(unsigned long)) {
        ptrace(PTRACE_POKEDATA, pid, addr, *p);
    }
    return 0;
}
