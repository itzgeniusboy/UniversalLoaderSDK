#include "RuntimeHook.h"
#include <string.h>
#include <sys/ptrace.h>
#include <android/log.h>
#include "../Utils/RecursionGuard.h"

#define TAG "OneCore-RuntimeHelper"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace OneCore {
    namespace RuntimeHelper {
        bool isSuspiciousPath(const char* path) {
            if (!path) return false;
            
            // Check for root/hooking tools
            static const char* suspicious[] = {
                "/system/xbin/su",
                "/sbin/su",
                "/system/bin/su",
                "/su",
                "magisk",
                "xposed",
                "frida"
            };
            
            for (auto s : suspicious) {
                if (strstr(path, s)) {
                    return true;
                }
            }
            return false;
        }

        void spoofSystemProperty(const char* name, char* value) {
            if (!name || !value) return;
            
            if (strcmp(name, "ro.debuggable") == 0) {
                strcpy(value, "0");
            } else if (strcmp(name, "ro.secure") == 0) {
                strcpy(value, "1");
            } else if (strcmp(name, "ro.build.tags") == 0) {
                strcpy(value, "release-keys");
            }
        }

        bool shouldBlockDlopen(const char* filename) {
            if (!filename) return false;
            // Block direct injection of known tools
            return strstr(filename, "frida-agent") || strstr(filename, "libxposed");
        }

        bool handlePtrace(int request, pid_t pid, void* addr, void* data, long* out_result) {
            // Anti-ptrace bypass: Always claim we are not being traced or allow the call to succeed fakely
            if (request == PTRACE_TRACEME) {
                LOGW("ptrace(PTRACE_TRACEME) detected - Faking success to bypass anti-debug");
                *out_result = 0;
                return true;
            }
            return false;
        }
    }
}
