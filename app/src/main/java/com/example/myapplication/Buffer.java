package com.example.myapplication;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;


public class Buffer {
    private FloatBuffer floatBuffer;
    private IntBuffer intBuffer;


    public Buffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        floatBuffer = bb.asFloatBuffer();
        floatBuffer.put(data);
        floatBuffer.position(0);
    }

    public Buffer(int[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        intBuffer = bb.asIntBuffer();
        intBuffer.put(data);
        intBuffer.position(0);
    }

    public FloatBuffer getFloatBuffer() {
        return floatBuffer;
    }

    public IntBuffer getIntBuffer() {
        return intBuffer;
    }
}
