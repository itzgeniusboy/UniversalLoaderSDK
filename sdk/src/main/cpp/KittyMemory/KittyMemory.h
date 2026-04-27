#ifndef KITTY_MEMORY_H
#define KITTY_MEMORY_H

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/mman.h>
#include <vector>

namespace KittyMemory {
    struct ProcMap {
        uintptr_t startAddress;
        uintptr_t endAddress;
        uintptr_t length;
        char permissions[5];
        char pathname[512];
    };

    std::vector<ProcMap> getAllMaps();
    ProcMap getLibraryMap(const char *libraryName);
    bool setAddressProtection(uintptr_t address, size_t length, int protection);
    bool writeReadOnlyMemory(void *address, void *buffer, size_t length);
    uintptr_t findSignature(uintptr_t start, uintptr_t end, const char* signature);
}

#endif // KITTY_MEMORY_H
