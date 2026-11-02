package com.makeasaying.data.save;

import com.makeasaying.engine.save.SaveLocation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory virtual SaveLocation for deterministic unit testing and replay verification.
 * Supports configurable write delays and crash simulation.
 */
public final class MemorySaveLocation implements SaveLocation {
    private final Map<String, String> files = new HashMap<>();
    private long writeDelayMs = 0;
    private boolean crashBeforeRename = false;
    private boolean crashMidWrite = false;

    public void setWriteDelayMs(long delayMs) {
        this.writeDelayMs = delayMs;
    }

    public void setCrashBeforeRename(boolean crash) {
        this.crashBeforeRename = crash;
    }

    public void setCrashMidWrite(boolean crash) {
        this.crashMidWrite = crash;
    }

    public synchronized Map<String, String> getFilesSnapshot() {
        return new HashMap<>(files);
    }

    @Override
    public synchronized boolean existsMain(String slot) {
        return files.containsKey(slot + ".json");
    }

    @Override
    public synchronized boolean existsBak(String slot) {
        return files.containsKey(slot + ".bak");
    }

    @Override
    public synchronized String readMain(String slot) throws IOException {
        String content = files.get(slot + ".json");
        if (content == null) throw new FileNotFoundException("Main save file not found: " + slot + ".json");
        return content;
    }

    @Override
    public synchronized String readBak(String slot) throws IOException {
        String content = files.get(slot + ".bak");
        if (content == null) throw new FileNotFoundException("Backup save file not found: " + slot + ".bak");
        return content;
    }

    @Override
    public synchronized void writeTemp(String slot, String content) throws IOException {
        if (writeDelayMs > 0) {
            try {
                Thread.sleep(writeDelayMs);
            } catch (InterruptedException ignored) {}
        }

        if (crashMidWrite) {
            files.put(slot + ".tmp", content.substring(0, Math.min(content.length() / 2, 20)));
            throw new IOException("Simulated mid-write crash / power failure!");
        }

        files.put(slot + ".tmp", content);
    }

    @Override
    public synchronized void copyToBak(String slot) throws IOException {
        String main = files.get(slot + ".json");
        if (main != null) {
            files.put(slot + ".bak", main);
        }
    }

    @Override
    public synchronized void atomicMoveTempToMain(String slot) throws IOException {
        if (crashBeforeRename) {
            throw new IOException("Simulated crash right before atomic rename!");
        }

        String tmp = files.remove(slot + ".tmp");
        if (tmp != null) {
            files.put(slot + ".json", tmp);
        }
    }

    @Override
    public synchronized void quarantineCorrupt(String slot) throws IOException {
        String main = files.remove(slot + ".json");
        if (main == null) return;

        String target = slot + ".corrupt";
        int counter = 1;
        while (files.containsKey(target)) {
            target = slot + ".corrupt." + counter;
            counter++;
        }
        files.put(target, main);
    }

    public synchronized void putRaw(String filename, String content) {
        files.put(filename, content);
    }

    public synchronized String getRaw(String filename) {
        return files.get(filename);
    }
}
