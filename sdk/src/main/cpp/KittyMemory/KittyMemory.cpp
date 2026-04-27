#include "KittyMemory.h"
#include <android/log.h>

#define TAG "OneCore-Kitty"

namespace KittyMemory {

    std::vector<ProcMap> getAllMaps() {
        std::vector<ProcMap> maps;
        FILE *f = fopen("/proc/self/maps", "r");
        if (!f) return maps;

        char line[512];
        while (fgets(line, sizeof(line), f)) {
            ProcMap m;
            char path[512] = {0};
            if (sscanf(line, "%lx-%lx %s %*s %*s %*s %s", &m.startAddress, &m.endAddress, m.permissions, path) >= 3) {
                m.length = m.endAddress - m.startAddress;
                strncpy(m.pathname, path, sizeof(m.pathname));
                maps.push_back(m);
            }
        }
        fclose(f);
        return maps;
    }

    ProcMap getLibraryMap(const char *libraryName) {
        auto maps = getAllMaps();
        for (auto &m : maps) {
            if (strstr(m.pathname, libraryName)) return m;
        }
        return {0, 0, 0, "", ""};
    }

    bool setAddressProtection(uintptr_t address, size_t length, int protection) {
        uintptr_t page_start = address & ~(getpagesize() - 1);
        uintptr_t page_end = (address + length + getpagesize() - 1) & ~(getpagesize() - 1);
        return mprotect((void *)page_start, page_end - page_start, protection) == 0;
    }

    bool writeReadOnlyMemory(void *address, void *buffer, size_t length) {
        if (!address || !buffer || length == 0) return false;

        if (!setAddressProtection((uintptr_t)address, length, PROT_READ | PROT_WRITE | PROT_EXEC)) {
            return false;
        }

        memcpy(address, buffer, length);

        setAddressProtection((uintptr_t)address, length, PROT_READ | PROT_EXEC);

        // Clear CPU cache (crucial for code patching)
        __builtin___clear_cache((char *)address, (char *)address + length);

        return true;
    }

    uintptr_t findSignature(uintptr_t start, uintptr_t end, const char* signature) {
        if (!start || !end || !signature || start >= end) return 0;
        
        // Convert signature string (e.g., "12 AB ?? FF") to byte array with mask
        std::vector<uint8_t> bytes;
        std::vector<bool> mask;
        
        char* sig = strdup(signature);
        char* token = strtok(sig, " ");
        while (token) {
            if (strcmp(token, "??") == 0) {
                bytes.push_back(0);
                mask.push_back(false);
            } else {
                bytes.push_back((uint8_t)strtol(token, nullptr, 16));
                mask.push_back(true);
            }
            token = strtok(nullptr, " ");
        }
        free(sig);

        for (uintptr_t i = start; i < end - bytes.size(); ++i) {
            bool found = true;
            for (size_t j = 0; j < bytes.size(); ++j) {
                if (mask[j] && *(uint8_t*)(i + j) != bytes[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return 0;
    }
}
