package com.onecore.sdk;

import com.onecore.sdk.utils.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides a virtualized memory space for apps running in the container.
 * Instead of modifying real process memory, reads and writes are redirected 
 * to this safe virtual space.
 */
public class MemoryRedirector {
    private static final String TAG = "MemoryRedirector";
    private static MemoryRedirector instance;
    
    // Map of PID to their virtual memory space (Address -> Value)
    private final Map<Integer, Map<Long, Byte>> virtualMemory = new HashMap<>();

    private MemoryRedirector() {}

    public static synchronized MemoryRedirector getInstance() {
        if (instance == null) {
            instance = new MemoryRedirector();
        }
        return instance;
    }

    public void writeVirtualMemory(int pid, long address, byte[] data) {
        Map<Long, Byte> processMem = virtualMemory.computeIfAbsent(pid, k -> new HashMap<>());
        for (int i = 0; i < data.length; i++) {
            processMem.put(address + i, data[i]);
        }
        Logger.v(TAG, "Virtual write at " + Long.toHexString(address) + " size: " + data.length);
    }

    public byte[] readVirtualMemory(int pid, long address, int size) {
        Map<Long, Byte> processMem = virtualMemory.get(pid);
        if (processMem == null) return new byte[size]; // Or fallback to real read?

        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) {
            Byte b = processMem.get(address + i);
            result[i] = (b != null) ? b : 0;
        }
        return result;
    }

    public void clearVirtualMemory(int pid) {
        virtualMemory.remove(pid);
        Logger.d(TAG, "Virtual memory cleared for PID: " + pid);
    }
}
