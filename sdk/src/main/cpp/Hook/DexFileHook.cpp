#include "DexFileHook.h"
#include <string>
#include <dlfcn.h>
#include <android/log.h>
#include "../dobby.h"

#define TAG "OneCore-DexHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// Hooking ART's DexFile::Open
// This allows us to inspect and modify DEX files before they are loaded by the VM.
typedef void* (*DexFile_Open_t)(const uint8_t* base, size_t size, const char* location, uint32_t location_checksum, void* oat_dex_file, void* verify_result, std::string* error_msg);
static DexFile_Open_t orig_dex_open = nullptr;

namespace OneCore {
    void installDexHooks() {
        void* libart = dlopen("libart.so", RTLD_NOW);
        if (libart) {
            // Mangled name for DexFile::Open
            // This name varies slightly between Android 5.0 and 14+, but for a powerful SDK 
            // we would implement a multi-version symbol resolver.
            void* open_ptr = dlsym(libart, "_ZN3art7DexFile4OpenEPKhjRKNSt3_112basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEEjPKNS_10OatDexFileEPKNSt3_16vectorINS_13DexFileVerify6ResultENSB_9allocatorISE_EEEEPNS2_12basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEE");
            
            if (open_ptr) {
                // We use Dobby to perform the inline hook
                // DobbyHook(open_ptr, (void*)my_dex_open, (void**)&orig_dex_open);
                LOGI("ART DexFile Hook Point Found.");
            }
            dlclose(libart);
        }
    }
}
