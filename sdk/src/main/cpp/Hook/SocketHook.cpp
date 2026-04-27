#include "SocketHook.h"
#include <dlfcn.h>
#include <sys/socket.h>
#include <android/log.h>
#include "../dobby.h"
#include "../Utils/RecursionGuard.h"

#define TAG "OneCore-NetHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

typedef ssize_t (*sendto_t)(int sockfd, const void *buf, size_t len, int flags, const struct sockaddr *dest_addr, socklen_t addrlen);
static sendto_t orig_sendto = nullptr;

typedef ssize_t (*recvfrom_t)(int sockfd, void *buf, size_t len, int flags, struct sockaddr *src_addr, socklen_t *addrlen);
static recvfrom_t orig_recvfrom = nullptr;

ssize_t my_sendto(int sockfd, const void *buf, size_t len, int flags, const struct sockaddr *dest_addr, socklen_t addrlen) {
    if (g_in_hook) return orig_sendto(sockfd, buf, len, flags, dest_addr, addrlen);
    g_in_hook = true;
    ssize_t res = orig_sendto(sockfd, buf, len, flags, dest_addr, addrlen);
    g_in_hook = false;
    return res;
}

ssize_t my_recvfrom(int sockfd, void *buf, size_t len, int flags, struct sockaddr *src_addr, socklen_t *addrlen) {
    if (g_in_hook) return orig_recvfrom(sockfd, buf, len, flags, src_addr, addrlen);
    g_in_hook = true;
    ssize_t result = orig_recvfrom(sockfd, buf, len, flags, src_addr, addrlen);
    g_in_hook = false;
    return result;
}

namespace OneCore {
    void installSocketHooks() {
        void* libc = dlopen("libc.so", RTLD_NOW);
        if (libc) {
            void* send_ptr = dlsym(libc, "sendto");
            void* recv_ptr = dlsym(libc, "recvfrom");
            
            if (send_ptr) DobbyHook(send_ptr, (void*)my_sendto, (void**)&orig_sendto);
            if (recv_ptr) DobbyHook(recv_ptr, (void*)my_recvfrom, (void**)&orig_recvfrom);
            
            LOGI("Network Mirror Layer: OPERATIONAL");
            dlclose(libc);
        }
    }
}
