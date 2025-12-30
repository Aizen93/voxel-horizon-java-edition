package org.aouessar.renderer.world;

public final class LongKeyList {
    private long[] data;
    private int size;

    public LongKeyList(int initialCapacity) {
        this.data = new long[Math.max(16, initialCapacity)];
    }

    public void clear() { size = 0; }

    public int size() { return size; }

    public long get(int i) { return data[i]; }

    public void add(long v) {
        if (size == data.length) {
            long[] n = new long[data.length * 2];
            System.arraycopy(data, 0, n, 0, data.length);
            data = n;
        }
        data[size++] = v;
    }
}