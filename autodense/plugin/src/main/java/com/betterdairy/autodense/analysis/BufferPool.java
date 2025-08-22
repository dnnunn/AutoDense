package com.betterdairy.autodense.analysis;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe buffer pool for reusing arrays in analysis operations.
 * Avoids repeated allocations in inner loops for performance.
 */
public final class BufferPool {
    private BufferPool() {}
    
    // Thread-local buffer pools for different array types
    private static final ThreadLocal<ConcurrentHashMap<Integer, ConcurrentLinkedQueue<float[]>>> floatBuffers = 
        ThreadLocal.withInitial(() -> new ConcurrentHashMap<>());
    
    private static final ThreadLocal<ConcurrentHashMap<Integer, ConcurrentLinkedQueue<double[]>>> doubleBuffers =
        ThreadLocal.withInitial(() -> new ConcurrentHashMap<>());
        
    private static final ThreadLocal<ConcurrentHashMap<Integer, ConcurrentLinkedQueue<int[]>>> intBuffers =
        ThreadLocal.withInitial(() -> new ConcurrentHashMap<>());
    
    /**
     * Get a reusable float array of the specified size.
     * Returns a recycled buffer if available, otherwise creates a new one.
     */
    public static float[] getFloatBuffer(int size) {
        ConcurrentHashMap<Integer, ConcurrentLinkedQueue<float[]>> buffers = floatBuffers.get();
        ConcurrentLinkedQueue<float[]> queue = buffers.computeIfAbsent(size, k -> new ConcurrentLinkedQueue<>());
        
        float[] buffer = queue.poll();
        if (buffer == null) {
            buffer = new float[size];
        }
        return buffer;
    }
    
    /**
     * Return a float buffer to the pool for reuse.
     * The buffer will be cleared (filled with zeros).
     */
    public static void returnFloatBuffer(float[] buffer) {
        if (buffer == null) return;
        
        // Clear the buffer for reuse
        java.util.Arrays.fill(buffer, 0.0f);
        
        ConcurrentHashMap<Integer, ConcurrentLinkedQueue<float[]>> buffers = floatBuffers.get();
        ConcurrentLinkedQueue<float[]> queue = buffers.get(buffer.length);
        if (queue != null && queue.size() < 10) { // Limit pool size to prevent memory bloat
            queue.offer(buffer);
        }
    }
    
    /**
     * Get a reusable double array of the specified size.
     */
    public static double[] getDoubleBuffer(int size) {
        ConcurrentHashMap<Integer, ConcurrentLinkedQueue<double[]>> buffers = doubleBuffers.get();
        ConcurrentLinkedQueue<double[]> queue = buffers.computeIfAbsent(size, k -> new ConcurrentLinkedQueue<>());
        
        double[] buffer = queue.poll();
        if (buffer == null) {
            buffer = new double[size];
        }
        return buffer;
    }
    
    /**
     * Return a double buffer to the pool for reuse.
     */
    public static void returnDoubleBuffer(double[] buffer) {
        if (buffer == null) return;
        
        java.util.Arrays.fill(buffer, 0.0);
        
        ConcurrentHashMap<Integer, ConcurrentLinkedQueue<double[]>> buffers = doubleBuffers.get();
        ConcurrentLinkedQueue<double[]> queue = buffers.get(buffer.length);
        if (queue != null && queue.size() < 10) {
            queue.offer(buffer);
        }
    }
    
    /**
     * Get a reusable int array of the specified size.
     */
    public static int[] getIntBuffer(int size) {
        ConcurrentHashMap<Integer, ConcurrentLinkedQueue<int[]>> buffers = intBuffers.get();
        ConcurrentLinkedQueue<int[]> queue = buffers.computeIfAbsent(size, k -> new ConcurrentLinkedQueue<>());
        
        int[] buffer = queue.poll();
        if (buffer == null) {
            buffer = new int[size];
        }
        return buffer;
    }
    
    /**
     * Return an int buffer to the pool for reuse.
     */
    public static void returnIntBuffer(int[] buffer) {
        if (buffer == null) return;
        
        java.util.Arrays.fill(buffer, 0);
        
        ConcurrentHashMap<Integer, ConcurrentLinkedQueue<int[]>> buffers = intBuffers.get();
        ConcurrentLinkedQueue<int[]> queue = buffers.get(buffer.length);
        if (queue != null && queue.size() < 10) {
            queue.offer(buffer);
        }
    }
    
    /**
     * Clear all buffers for the current thread.
     * Call this when done with a long analysis session to free memory.
     */
    public static void clearThreadBuffers() {
        floatBuffers.get().clear();
        doubleBuffers.get().clear(); 
        intBuffers.get().clear();
    }
}