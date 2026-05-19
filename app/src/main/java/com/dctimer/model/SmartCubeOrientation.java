package com.dctimer.model;

import java.io.Serializable;

public class SmartCubeOrientation implements Serializable {
    public static final int SOURCE_UNKNOWN = 0;
    public static final int SOURCE_GYRO = 1;
    public static final int SOURCE_QUATERNION = 2;

    private final float w;
    private final float x;
    private final float y;
    private final float z;
    private final long timestamp;
    private final int source;

    public SmartCubeOrientation(float w, float x, float y, float z, long timestamp, int source) {
        float length = (float) Math.sqrt(w * w + x * x + y * y + z * z);
        if (length == 0f) {
            this.w = 1f;
            this.x = 0f;
            this.y = 0f;
            this.z = 0f;
        } else {
            this.w = w / length;
            this.x = x / length;
            this.y = y / length;
            this.z = z / length;
        }
        this.timestamp = timestamp;
        this.source = source;
    }

    public static SmartCubeOrientation identity() {
        return new SmartCubeOrientation(1f, 0f, 0f, 0f, System.currentTimeMillis(), SOURCE_UNKNOWN);
    }

    public float getW() {
        return w;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getSource() {
        return source;
    }

    public float[] toMatrix() {
        float[] matrix = new float[16];
        float xx = x * x;
        float yy = y * y;
        float zz = z * z;
        float xy = x * y;
        float xz = x * z;
        float yz = y * z;
        float wx = w * x;
        float wy = w * y;
        float wz = w * z;

        matrix[0] = 1f - 2f * (yy + zz);
        matrix[1] = 2f * (xy + wz);
        matrix[2] = 2f * (xz - wy);
        matrix[3] = 0f;

        matrix[4] = 2f * (xy - wz);
        matrix[5] = 1f - 2f * (xx + zz);
        matrix[6] = 2f * (yz + wx);
        matrix[7] = 0f;

        matrix[8] = 2f * (xz + wy);
        matrix[9] = 2f * (yz - wx);
        matrix[10] = 1f - 2f * (xx + yy);
        matrix[11] = 0f;

        matrix[12] = 0f;
        matrix[13] = 0f;
        matrix[14] = 0f;
        matrix[15] = 1f;
        return matrix;
    }
}
