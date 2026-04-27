#ifndef MEMORY_PATCH_H
#define MEMORY_PATCH_H

#include "KittyMemory.h"
#include <string>

namespace KittyMemory {
    class MemoryPatch {
    public:
        MemoryPatch();
        static MemoryPatch createWithBytes(uintptr_t absoluteAddress, const void *patchBytes, size_t patchSize);
        bool Modify();
        bool Restore();
        bool isValid() const { return _address != 0; }

    private:
        uintptr_t _address;
        size_t _size;
        std::vector<uint8_t> _origBytes;
        std::vector<uint8_t> _patchBytes;
    };
}

#endif // MEMORY_PATCH_H
