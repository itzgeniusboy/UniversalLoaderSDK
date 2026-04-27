#include "MemoryPatch.h"

namespace KittyMemory {
    MemoryPatch::MemoryPatch() : _address(0), _size(0) {}

    MemoryPatch MemoryPatch::createWithBytes(uintptr_t absoluteAddress, const void *patchBytes, size_t patchSize) {
        MemoryPatch patch;
        if (absoluteAddress == 0 || !patchBytes || patchSize == 0) return patch;

        patch._address = absoluteAddress;
        patch._size = patchSize;
        patch._patchBytes.assign((uint8_t *)patchBytes, (uint8_t *)patchBytes + patchSize);
        
        patch._origBytes.resize(patchSize);
        memcpy(patch._origBytes.data(), (void *)absoluteAddress, patchSize);

        return patch;
    }

    bool MemoryPatch::Modify() {
        if (!isValid()) return false;
        return writeReadOnlyMemory((void *)_address, _patchBytes.data(), _size);
    }

    bool MemoryPatch::Restore() {
        if (!isValid()) return false;
        return writeReadOnlyMemory((void *)_address, _origBytes.data(), _size);
    }
}
