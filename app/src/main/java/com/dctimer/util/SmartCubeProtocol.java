package com.dctimer.util;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

public interface SmartCubeProtocol {
    boolean start(BluetoothGatt gatt, BluetoothGattService service, String deviceName, String deviceAddress);

    void clear();

    void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status);

    void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status);

    void onCharacteristicChanged(BluetoothGattCharacteristic characteristic);
}
