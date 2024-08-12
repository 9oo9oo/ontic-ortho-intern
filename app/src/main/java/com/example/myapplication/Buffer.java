package com.example.myapplication;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Buffer {
    private final FloatBuffer buffer;

    public Buffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        buffer = bb.asFloatBuffer();
        buffer.put(data);
        buffer.position(0);
    }

    public FloatBuffer getBuffer() {
        return buffer;
    }
}
