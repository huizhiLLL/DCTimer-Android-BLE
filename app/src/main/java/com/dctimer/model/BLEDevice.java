package com.dctimer.model;

public class BLEDevice {
    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_GIIKER_CUBE = 0;
    public static final int TYPE_GANI_CUBE = 1;
    public static final int TYPE_GAN_TIMER = 2;
    public static final int TYPE_GAN_ROBOT = 3;
    public static final int TYPE_MOYU32_CUBE = 4;
    public static final int TYPE_QIYI_CUBE = 5;
    private String name;
    private String address;
    private int connected;
    //private int type;

    public BLEDevice(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getConnected() {
        return connected;
    }

    public void setConnected(int connected) {
        this.connected = connected;
    }
}
