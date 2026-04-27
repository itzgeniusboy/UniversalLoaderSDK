#ifndef RUNTIME_HOOK_H
#define RUNTIME_HOOK_H

#include <jni.h>
#include <string>
#include <sys/types.h>

namespace OneCore {
    namespace RuntimeHelper {
        bool isSuspiciousPath(const char* path);
        void spoofSystemProperty(const char* name, char* value);
        bool shouldBlockDlopen(const char* filename);
        bool handlePtrace(int request, pid_t pid, void* addr, void* data, long* out_result);
    }
}

#endif // RUNTIME_HOOK_H
