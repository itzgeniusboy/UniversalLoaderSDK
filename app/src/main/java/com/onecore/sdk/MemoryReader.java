package com.onecore.sdk;

import com.onecore.sdk.utils.Logger;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Handles reading and writing memory via /proc/[pid]/mem.
 * Requires appropriate permissions or root if accessing other processes.
 */
public class MemoryReader {
    private static final String TAG = "MemoryReader";
    private static MemoryReader instance;

    private MemoryReader() {}

    public static synchronized MemoryReader getInstance() {
        if (instance == null) {
            instance = new MemoryReader();
        }
        return instance;
    }

    /**
     * Powerful: Scans memory for a pattern (AOB Scanning).
     */
    public native long scanSignature(long start, long end, String signature);

    /**
     * Finds the base address of a loaded library (e.g., "libunity.so").
     */
    public native long findModuleBase(String moduleName);

    /**
     * Reads memory. Redirects to virtual memory for apps in the container.
     */
    public byte[] readMemory(int pid, long address, int size) {
        if (!SDKLicense.getInstance().isLicensed()) return null;
        
        // Use Virtual Memory if running in container
        return MemoryRedirector.getInstance().readVirtualMemory(pid, address, size);
    }

    /**
     * Writes memory. Redirects to virtual memory for apps in the container.
     */
    public boolean writeMemory(int pid, long address, byte[] data) {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        
        // Use Virtual Memory redirection
        MemoryRedirector.getInstance().writeVirtualMemory(pid, address, data);
        return true;
    }

    // Helper methods for primitive types
    public int readInt(int pid, long address) {
        byte[] data = readMemory(pid, address, 4);
        if (data == null) return 0;
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public boolean writeInt(int pid, long address, int value) {
        byte[] data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
        return writeMemory(pid, address, data);
    }
}
