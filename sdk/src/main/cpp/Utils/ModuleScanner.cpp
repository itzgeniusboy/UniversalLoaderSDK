#include "ModuleScanner.h"
#include "../KittyMemory/KittyMemory.h"
#include <algorithm>

namespace OneCore {
    std::vector<ModuleInfo> getLoadedModules() {
        std::vector<ModuleInfo> modules;
        auto maps = KittyMemory::getAllMaps();
        
        for (const auto& m : maps) {
            if (m.pathname[0] == '\0') continue;
            
            // Avoid duplicates by checking base address
            auto it = std::find_if(modules.begin(), modules.end(), [&](const ModuleInfo& info) {
                return info.path == m.pathname;
            });

            if (it == modules.end()) {
                ModuleInfo info;
                info.path = m.pathname;
                info.base = m.startAddress;
                info.size = m.length;
                
                // Extract name from path
                size_t last_slash = info.path.find_last_of('/');
                info.name = (last_slash != std::string::npos) ? info.path.substr(last_slash + 1) : info.path;
                
                modules.push_back(info);
            } else {
                // Update size for contiguous segments
                it->size += m.length;
            }
        }
        return modules;
    }

    ModuleInfo findModule(const char* name) {
        auto modules = getLoadedModules();
        for (const auto& m : modules) {
            if (m.name == name || m.path == name) return m;
        }
        return {"", 0, 0, ""};
    }
}
