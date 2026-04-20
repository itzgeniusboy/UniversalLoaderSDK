package com.loader.sdk;

import com.loader.sdk.utils.Logger;
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
     * Reads memory at the specified address.
     * @param pid Process ID (use 0 for self).
     * @param address Hex address to read.
     * @param size Number of bytes to read.
     * @return Byte array of read memory.
     */
    public byte[] readMemory(int pid, long address, int size) {
        String path = pid == 0 ? "/proc/self/mem" : "/proc/" + pid + "/mem";
        byte[] buffer = new byte[size];

        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            raf.seek(address);
            raf.readFully(buffer);
            return buffer;
        } catch (Exception e) {
            Logger.e(TAG, "Read memory failed at " + Long.toHexString(address), e);
            return null;
        }
    }

    /**
     * Writes memory at the specified address.
     * @param pid Process ID.
     * @param address Hex address to write.
     * @param data Data to write.
     * @return True if successful.
     */
    public boolean writeMemory(int pid, long address, byte[] data) {
        String path = pid == 0 ? "/proc/self/mem" : "/proc/" + pid + "/mem";
        try (RandomAccessFile raf = new RandomAccessFile(path, "rw")) {
            raf.seek(address);
            raf.write(data);
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Write memory failed at " + Long.toHexString(address), e);
            return false;
        }
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
