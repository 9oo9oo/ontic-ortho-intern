package com.example.myapplication;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Utility class for creating FloatBuffer and IntBuffer instances from float and int arrays.
 * This class facilitates the conversion of primitive arrays into buffers suitable for OpenGL operations.
 */
public class Buffer {
    private FloatBuffer floatBuffer;
    private IntBuffer intBuffer;

    /**
     * Constructs a Buffer instance with the given float array.
     * Initializes a FloatBuffer with the provided data.
     *
     * @param data The float array to be stored in the FloatBuffer.
     */
    public Buffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        floatBuffer = bb.asFloatBuffer();
        floatBuffer.put(data);
        floatBuffer.position(0);
    }

    /**
     * Constructs a Buffer instance with the given int array.
     * Initializes an IntBuffer with the provided data.
     *
     * @param data The int array to be stored in the IntBuffer.
     */
    public Buffer(int[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        intBuffer = bb.asIntBuffer();
        intBuffer.put(data);
        intBuffer.position(0);
    }

    /**
     * Returns the FloatBuffer containing the float data.
     *
     * @return The FloatBuffer instance, or null if not initialized.
     */
    public FloatBuffer getFloatBuffer() {
        return floatBuffer;
    }

    /**
     * Returns the IntBuffer containing the int data.
     *
     * @return The IntBuffer instance, or null if not initialized.
     */
    public IntBuffer getIntBuffer() {
        return intBuffer;
    }
}
