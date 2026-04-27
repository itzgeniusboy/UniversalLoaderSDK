#ifndef MODULE_SCANNER_H
#define MODULE_SCANNER_H

#include <jni.h>
#include <string>
#include <vector>

namespace OneCore {
    struct ModuleInfo {
        std::string name;
        uintptr_t base;
        size_t size;
        std::string path;
    };

    std::vector<ModuleInfo> getLoadedModules();
    ModuleInfo findModule(const char* name);
}

#endif // MODULE_SCANNER_H
