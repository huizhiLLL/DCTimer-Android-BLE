package com.dctimer.util;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

public interface SmartTimerProtocol {
    boolean start(BluetoothGatt gatt, BluetoothGattService service, String deviceName, String deviceAddress);

    void clear();

    void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status);

    void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status);

    void onCharacteristicChanged(BluetoothGattCharacteristic characteristic);

    default void onMtuChanged(int mtu, int status) {
    }

    interface StateCallback {
        void onTimerIdle(int time);

        default void onTimerInspection(int time) {
        }

        void onTimerReady(int time);

        void onTimerRunning(int time);

        void onTimerStopped(int time);

        void onTimerDisconnected();
    }
}
