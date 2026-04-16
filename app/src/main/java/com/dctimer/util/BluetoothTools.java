package com.dctimer.util;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.dctimer.R;
import com.dctimer.activity.MainActivity;
import com.dctimer.aes.Decrypt;
import com.dctimer.model.BLEDevice;
import com.dctimer.model.SmartCube;

import java.util.*;

import static com.dctimer.APP.bleDeviceType;
import static com.dctimer.APP.enterTime;

public class BluetoothTools {
    private static final String UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb";
    public static final UUID SERVICE_UUID = UUID.fromString("0000180a" + UUID_SUFFIX);
    public static final UUID SERVICE_UUID_GAN = UUID.fromString("0000fff0" + UUID_SUFFIX);
    public static final UUID SERVICE_UUID_GIIKER = UUID.fromString("0000aadb" + UUID_SUFFIX);
    public static final UUID SERVICE_UUID_RW = UUID.fromString("0000aaaa" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_DATA = UUID.fromString("0000aadc" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_VERSION = UUID.fromString("00002a28" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_HARDWARE = UUID.fromString("00002a23" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_READ = UUID.fromString("0000aaab" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_WRITE = UUID.fromString("0000aaac" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_F2 = UUID.fromString("0000fff2" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_F3 = UUID.fromString("0000fff3" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_F5 = UUID.fromString("0000fff5" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_F6 = UUID.fromString("0000fff6" + UUID_SUFFIX);
    public static final UUID CHARACTER_UUID_F7 = UUID.fromString("0000fff7" + UUID_SUFFIX);
    private static final String UUID_GAN_V2_SUFFIX = "-cd67-11e9-a32f-2a2ae2dbcce4";
    public static final UUID SERVICE_UUID_GAN_V2 = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dc4179");
    public static final UUID SERVICE_UUID_GAN_V3 = UUID.fromString("8653000a-43e6-47b7-9cb0-5fc21d4ae340");
    public static final UUID SERVICE_UUID_GAN_V4 = UUID.fromString("00000010-0000-fff7-fff6-fff5fff4fff0");
    public static final UUID CHARACTER_UUID_V2_READ = UUID.fromString("28be4cb6" + UUID_GAN_V2_SUFFIX);
    public static final UUID CHARACTER_UUID_V2_WRITE = UUID.fromString("28be4a4a" + UUID_GAN_V2_SUFFIX);

    private MainActivity context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean mScanning;
    private Set<String> addressMap;
    private List<BLEDevice> cubeList;
    //private BLEDevice bleDevice;
    private int connectedIndex;
    private SmartCube smartCube;
    private SmartCube.StateChangedCallback stateChangedCallback;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService service;
    private SmartCubeProtocol smartCubeProtocol;
    private SmartTimerProtocol smartTimerProtocol;
    private SmartTimerProtocol.StateCallback smartTimerStateCallback;
    private List<Integer> preMoves = new ArrayList<>();
    private int prevMoveCnt = -1;
    private long lastTime = -1;
    
    private BluetoothGattService findServiceByCharacteristic(BluetoothGatt gatt, UUID readUuid, UUID writeUuid) {
        if (gatt == null) return null;
        List<BluetoothGattService> services = gatt.getServices();
        if (services == null) return null;
        for (BluetoothGattService srv : services) {
            BluetoothGattCharacteristic readChr = srv.getCharacteristic(readUuid);
            BluetoothGattCharacteristic writeChr = srv.getCharacteristic(writeUuid);
            if (readChr != null && writeChr != null) {
                return srv;
            }
        }
        return null;
    }

    private void logDiscoveredServices(BluetoothGatt gatt) {
        List<BluetoothGattService> services = gatt.getServices();
        if (services == null) {
            Log.w("dct", "services 列表为空");
            return;
        }
        for (BluetoothGattService srv : services) {
            Log.w("dct", "service " + srv.getUuid());
            List<BluetoothGattCharacteristic> characteristics = srv.getCharacteristics();
            if (characteristics == null) continue;
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                Log.w("dct", "  characteristic " + characteristic.getUuid());
            }
        }
    }

    public BluetoothTools(MainActivity context) {
        this.context = context;
        cubeList = new ArrayList<>();
    }

    private boolean isSmartCubeType(int deviceType) {
        return deviceType == BLEDevice.TYPE_MOYU32_CUBE
                || deviceType == BLEDevice.TYPE_QIYI_CUBE
                || deviceType == BLEDevice.TYPE_GANI_CUBE;
    }

    private boolean isSmartTimerType(int deviceType) {
        return deviceType == BLEDevice.TYPE_QIYI_TIMER;
    }

    private boolean isSmartCubeMode() {
        return enterTime == 3;
    }

    private boolean isSmartTimerMode() {
        return enterTime == 4;
    }

    private boolean shouldShowDeviceType(int deviceType) {
        if (isSmartCubeMode()) {
            return isSmartCubeType(deviceType);
        }
        if (isSmartTimerMode()) {
            return isSmartTimerType(deviceType);
        }
        return false;
    }

    private int guessDeviceType(String deviceName, ScanRecord scanRecord) {
        if (deviceName != null && deviceName.startsWith("WCU_MY3")) {
            return BLEDevice.TYPE_MOYU32_CUBE;
        }
        if (isQiyiCubeName(deviceName)) {
            return BLEDevice.TYPE_QIYI_CUBE;
        }
        if (GanCubeProtocol.matchesDeviceName(deviceName)) {
            return BLEDevice.TYPE_GANI_CUBE;
        }
        if (scanRecord != null) {
            if (hasServiceUuid(scanRecord, QiyiSmartTimerProtocol.SERVICE_UUID)
                    || hasManufacturerData(scanRecord, QiyiSmartTimerProtocol.MANUFACTURER_ID)) {
                return BLEDevice.TYPE_QIYI_TIMER;
            }
        }
        return BLEDevice.TYPE_UNKNOWN;
    }

    private boolean isQiyiCubeName(String deviceName) {
        if (deviceName == null) return false;
        String normalized = deviceName.trim().toUpperCase(Locale.US);
        return normalized.startsWith("QY-QYSC") || normalized.startsWith("XMD-TORNADOV4-I");
    }

    private int resolveDeviceType(BluetoothGatt gatt, String deviceName) {
        int guessedType = guessDeviceType(deviceName, null);
        if (guessedType == BLEDevice.TYPE_MOYU32_CUBE
                || guessedType == BLEDevice.TYPE_QIYI_CUBE
                || guessedType == BLEDevice.TYPE_GANI_CUBE
                || guessedType == BLEDevice.TYPE_QIYI_TIMER) {
            return guessedType;
        }
        if (gatt == null) {
            return guessedType;
        }
        if (gatt.getService(QiyiSmartTimerProtocol.SERVICE_UUID) != null
                || findServiceByCharacteristic(gatt, QiyiSmartTimerProtocol.READ_UUID, QiyiSmartTimerProtocol.WRITE_UUID) != null) {
            return BLEDevice.TYPE_QIYI_TIMER;
        }
        if (gatt.getService(Moyu32CubeProtocol.SERVICE_UUID) != null
                || findServiceByCharacteristic(gatt, Moyu32CubeProtocol.READ_UUID, Moyu32CubeProtocol.WRITE_UUID) != null) {
            return BLEDevice.TYPE_MOYU32_CUBE;
        }
        if (gatt.getService(QiyiCubeProtocol.SERVICE_UUID) != null
                || findServiceByCharacteristic(gatt, QiyiCubeProtocol.CUBE_UUID, QiyiCubeProtocol.CUBE_UUID) != null) {
            return BLEDevice.TYPE_QIYI_CUBE;
        }
        if (GanCubeProtocol.findPrimaryService(gatt) != null) {
            return BLEDevice.TYPE_GANI_CUBE;
        }
        return guessedType;
    }

    private SmartCubeProtocol createSmartCubeProtocol(int deviceType, SmartCube cube) {
        if (deviceType == BLEDevice.TYPE_MOYU32_CUBE) {
            return new Moyu32CubeProtocol(context, cube);
        }
        if (deviceType == BLEDevice.TYPE_QIYI_CUBE) {
            return new QiyiCubeProtocol(context, cube);
        }
        if (deviceType == BLEDevice.TYPE_GANI_CUBE) {
            return new GanCubeProtocol(context, cube);
        }
        return null;
    }

    private SmartTimerProtocol createSmartTimerProtocol(int deviceType) {
        if (deviceType == BLEDevice.TYPE_QIYI_TIMER) {
            return new QiyiSmartTimerProtocol(smartTimerStateCallback);
        }
        return null;
    }

    private void clearSmartCubeProtocol() {
        if (smartCubeProtocol != null) {
            smartCubeProtocol.clear();
            smartCubeProtocol = null;
        }
    }

    private void clearSmartTimerProtocol() {
        if (smartTimerProtocol != null) {
            smartTimerProtocol.clear();
            smartTimerProtocol = null;
        }
    }

    private void clearConnectedDeviceState() {
        clearSmartCubeProtocol();
        clearSmartTimerProtocol();
        smartCube = null;
        service = null;
        prevMoveCnt = -1;
        lastTime = -1;
        preMoves.clear();
    }

    public boolean initBluetoothAdapter() {
        if (bluetoothAdapter == null) getBluetoothAdapter();
        return bluetoothAdapter != null;
    }

    public void getBluetoothAdapter() {
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                boolean isEnable = bluetoothAdapter.enable();
                if (!isEnable) {
                    Log.e("dct", "蓝牙打开失败");
                }
            }
        } catch (SecurityException e) {
            Log.e("dct", "获取蓝牙适配器失败", e);
            bluetoothAdapter = null;
        }
        mScanning = false;
        bluetoothLeScanner = null;
    }



    public SmartCube getCube() {
        return smartCube;
    }

    public void notifyLocalCubeReset(String cubeState) {
        if (smartCubeProtocol != null) {
            smartCubeProtocol.onLocalCubeReset(cubeState);
        }
    }

    public void setCubeStateChangedCallback(SmartCube.StateChangedCallback callback) {
        this.stateChangedCallback = callback;
    }

    public void setTimerStateCallback(SmartTimerProtocol.StateCallback callback) {
        this.smartTimerStateCallback = callback;
    }

    @TargetApi(18)
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            onDeviceFound(bluetoothDevice);
        }
    };

    @TargetApi(21)
    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result != null) {
                onDeviceFound(result.getDevice(), result.getScanRecord());
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (results == null) return;
            for (ScanResult result : results) {
                if (result != null) {
                    onDeviceFound(result.getDevice(), result.getScanRecord());
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("dct", "BLE 扫描失败 errorCode=" + errorCode);
            mScanning = false;
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    context.showScanButton();
                }
            });
        }
    };

    private void onDeviceFound(BluetoothDevice bluetoothDevice) {
        onDeviceFound(bluetoothDevice, null);
    }

    @TargetApi(21)
    private void onDeviceFound(BluetoothDevice bluetoothDevice, ScanRecord scanRecord) {
        if (bluetoothDevice == null) return;
        try {
            final String deviceName = bluetoothDevice.getName();
            final String deviceAddress = bluetoothDevice.getAddress();
            if (deviceName == null || deviceAddress == null) return;
            final int deviceType = guessDeviceType(deviceName, scanRecord);
            if (!shouldShowDeviceType(deviceType)) {
                return;
            }
            String protocolAddress = extractProtocolAddress(deviceName, deviceAddress, scanRecord);
            BLEDevice existingDevice = findDeviceByAddress(deviceAddress);
            if (existingDevice != null) {
                if (!TextUtils.isEmpty(protocolAddress) && !protocolAddress.equalsIgnoreCase(existingDevice.getProtocolAddress())) {
                    existingDevice.setProtocolAddress(protocolAddress);
                    Log.w("dct", "更新设备协议地址 " + deviceName + " -> " + protocolAddress);
                }
                if (existingDevice.getType() != deviceType) {
                    existingDevice.setType(deviceType);
                }
                return;
            }
            Log.w("dct", "发现设备 " + deviceName);
            BLEDevice device = new BLEDevice(deviceName, deviceAddress);
            device.setType(deviceType);
            if (!TextUtils.isEmpty(protocolAddress)) {
                device.setProtocolAddress(protocolAddress);
                if (!protocolAddress.equalsIgnoreCase(deviceAddress)) {
                    Log.w("dct", "设备 " + deviceName + " 使用广播 MAC " + protocolAddress + " 作为协议地址");
                }
            }
            addressMap.add(deviceAddress);
            cubeList.add(device);
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    context.refreshCubeList(cubeList);
                }
            });
        } catch (SecurityException e) {
            Log.e("dct", "读取扫描设备信息失败", e);
        }
    }

    private BLEDevice findDeviceByAddress(String deviceAddress) {
        if (deviceAddress == null || cubeList == null) return null;
        for (BLEDevice device : cubeList) {
            if (deviceAddress.equalsIgnoreCase(device.getAddress())) {
                return device;
            }
        }
        return null;
    }

    @TargetApi(21)
    private String extractProtocolAddress(String deviceName, String deviceAddress, ScanRecord scanRecord) {
        if (!isQiyiCubeName(deviceName) || scanRecord == null) {
            return deviceAddress;
        }
        SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
        if (manufacturerData == null || manufacturerData.size() == 0) {
            return deviceAddress;
        }
        byte[] macBytes = manufacturerData.get(0x0504);
        if (macBytes == null || macBytes.length < 6) {
            return deviceAddress;
        }
        StringBuilder builder = new StringBuilder(17);
        for (int i = 5; i >= 0; i--) {
            if (builder.length() > 0) builder.append(':');
            builder.append(String.format(Locale.US, "%02X", macBytes[i] & 0xff));
        }
        return builder.toString();
    }

    private String resolveProtocolAddress(BluetoothGatt gatt) {
        if (connectedIndex >= 0 && connectedIndex < cubeList.size()) {
            String protocolAddress = cubeList.get(connectedIndex).getProtocolAddress();
            if (!TextUtils.isEmpty(protocolAddress)) {
                return protocolAddress;
            }
        }
        return gatt != null && gatt.getDevice() != null ? gatt.getDevice().getAddress() : null;
    }

    @TargetApi(21)
    private boolean hasServiceUuid(ScanRecord scanRecord, UUID serviceUuid) {
        if (scanRecord == null || serviceUuid == null) {
            return false;
        }
        List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
        if (serviceUuids == null) {
            return false;
        }
        for (ParcelUuid uuid : serviceUuids) {
            if (uuid != null && serviceUuid.equals(uuid.getUuid())) {
                return true;
            }
        }
        return false;
    }

    @TargetApi(21)
    private boolean hasManufacturerData(ScanRecord scanRecord, int manufacturerId) {
        if (scanRecord == null) {
            return false;
        }
        SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
        return manufacturerData != null && manufacturerData.get(manufacturerId) != null;
    }

    public void startScan() {
        cubeList = new ArrayList<>();
        if (bluetoothAdapter != null && !mScanning) {
            Log.w("dct", "搜索设备");
            addressMap = new HashSet<>();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    if (bluetoothLeScanner != null) {
                        ScanSettings settings = new ScanSettings.Builder()
                                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                                .build();
                        bluetoothLeScanner.startScan(null, settings, mScanCallback);
                        mScanning = true;
                        return;
                    }
                    Log.w("dct", "BluetoothLeScanner 为空，回退旧扫描链路");
                }
                bluetoothAdapter.startLeScan(mLeScanCallback);
                mScanning = true;
            } catch (SecurityException e) {
                Log.e("dct", "启动 BLE 扫描失败", e);
                mScanning = false;
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, context.getString(R.string.permission_deny), Toast.LENGTH_SHORT).show();
                        context.showScanButton();
                    }
                });
            }
        }
    }

    public void stopScan() {
        if (bluetoothAdapter != null && mScanning) {
            Log.w("dct", "停止搜索");
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && bluetoothLeScanner != null) {
                    bluetoothLeScanner.stopScan(mScanCallback);
                } else {
                    bluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            } catch (SecurityException e) {
                Log.e("dct", "停止 BLE 扫描失败", e);
            }
            mScanning = false;
            bluetoothLeScanner = null;
        }
    }

    @TargetApi(18)
    public void connectDevice(int pos) {
        if (mScanning) {
            stopScan();
            context.showScanButton();
        }
        connectedIndex = pos;
        BLEDevice bleDevice = cubeList.get(pos);
        bleDeviceType = bleDevice.getType();
        if (bleDeviceType == BLEDevice.TYPE_UNKNOWN) {
            bleDeviceType = guessDeviceType(bleDevice.getName(), null);
        }
        Log.w("dct", "连接设备 " + bleDevice.getName() + " 使用类型 " + bleDeviceType
                + " 协议地址 " + bleDevice.getProtocolAddress());
        String address = bleDevice.getAddress();
        int connect = bleDevice.getConnected();
        if (connect == 0) {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                bleDevice.setConnected(2);
                clearConnectedDeviceState();
                context.refreshCubeList();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mBluetoothGatt = device.connectGatt(context, false, mBluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
                } else mBluetoothGatt = device.connectGatt(context, false, mBluetoothGattCallback);
            } catch (SecurityException e) {
                bleDevice.setConnected(0);
                Log.e("dct", "连接蓝牙设备失败", e);
                context.refreshCubeList();
                Toast.makeText(context, context.getString(R.string.permission_deny), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @TargetApi(18)
    public void disconnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt = null;
        }
        clearConnectedDeviceState();
    }

    @TargetApi(18)
    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.w("dct", "设备已连接");
                context.dismissDialog();
                BLEDevice bleDevice = cubeList.get(connectedIndex);
                bleDevice.setConnected(1);
                gatt.discoverServices();
            }
            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.w("dct", "连接断开");
                gatt.close();
                //mBluetoothGatt = null;
                BLEDevice bleDevice = cubeList.get(connectedIndex);
                bleDevice.setConnected(0);
                if (smartTimerProtocol != null && smartTimerStateCallback != null) {
                    smartTimerStateCallback.onTimerDisconnected();
                }
                clearConnectedDeviceState();
                context.disconnectHint(bleDevice);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            bleDeviceType = resolveDeviceType(gatt, gatt.getDevice().getName());
            Log.w("dct", "onServicesDiscovered bleDeviceType=" + bleDeviceType);
            if (connectedIndex >= 0 && connectedIndex < cubeList.size()) {
                cubeList.get(connectedIndex).setType(bleDeviceType);
            }
            logDiscoveredServices(gatt);
            clearConnectedDeviceState();
            if (bleDeviceType == BLEDevice.TYPE_GANI_CUBE) {
                smartCube = new SmartCube();
                smartCube.setType(bleDeviceType);
                smartCube.setDeviceName(gatt.getDevice().getName());
                smartCube.setStateChangedCallback(stateChangedCallback);
                smartCubeProtocol = createSmartCubeProtocol(bleDeviceType, smartCube);
                service = GanCubeProtocol.findPrimaryService(gatt);
            } else if (bleDeviceType == BLEDevice.TYPE_MOYU32_CUBE) {
                smartCube = new SmartCube();
                smartCube.setType(bleDeviceType);
                smartCube.setDeviceName(gatt.getDevice().getName());
                smartCube.setStateChangedCallback(stateChangedCallback);
                smartCubeProtocol = createSmartCubeProtocol(bleDeviceType, smartCube);
                service = gatt.getService(Moyu32CubeProtocol.SERVICE_UUID);
            } else if (bleDeviceType == BLEDevice.TYPE_QIYI_CUBE) {
                smartCube = new SmartCube();
                smartCube.setType(bleDeviceType);
                smartCube.setDeviceName(gatt.getDevice().getName());
                smartCube.setStateChangedCallback(stateChangedCallback);
                smartCubeProtocol = createSmartCubeProtocol(bleDeviceType, smartCube);
                service = gatt.getService(QiyiCubeProtocol.SERVICE_UUID);
            } else if (bleDeviceType == BLEDevice.TYPE_QIYI_TIMER) {
                smartTimerProtocol = createSmartTimerProtocol(bleDeviceType);
                service = gatt.getService(QiyiSmartTimerProtocol.SERVICE_UUID);
            } else {
                service = null;
            }
            if (service == null && bleDeviceType == BLEDevice.TYPE_MOYU32_CUBE) {
                service = findServiceByCharacteristic(gatt, Moyu32CubeProtocol.READ_UUID, Moyu32CubeProtocol.WRITE_UUID);
                if (service != null) {
                    Log.w("dct", "MoYu32 通过特征兜底定位到 service: " + service.getUuid());
                }
            } else if (service == null && bleDeviceType == BLEDevice.TYPE_QIYI_CUBE) {
                service = findServiceByCharacteristic(gatt, QiyiCubeProtocol.CUBE_UUID, QiyiCubeProtocol.CUBE_UUID);
                if (service != null) {
                    Log.w("dct", "QiYi 通过特征兜底定位到 service: " + service.getUuid());
                }
            } else if (service == null && bleDeviceType == BLEDevice.TYPE_QIYI_TIMER) {
                service = findServiceByCharacteristic(gatt, QiyiSmartTimerProtocol.READ_UUID, QiyiSmartTimerProtocol.WRITE_UUID);
                if (service != null) {
                    Log.w("dct", "QiYi Smart Timer 通过特征兜底定位到 service: " + service.getUuid());
                }
            }
            if (service == null) {
                Log.e("dct", "service为null");
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, bleDeviceType == BLEDevice.TYPE_UNKNOWN
                                ? context.getString(R.string.ble_device_not_supported)
                                : context.getString(R.string.connect_fail), Toast.LENGTH_SHORT).show();
                    }
                });
                gatt.disconnect();
            } else if (bleDeviceType == BLEDevice.TYPE_MOYU32_CUBE
                    || bleDeviceType == BLEDevice.TYPE_QIYI_CUBE
                    || bleDeviceType == BLEDevice.TYPE_GANI_CUBE) {
                if (smartCubeProtocol == null || !smartCubeProtocol.start(gatt, service, gatt.getDevice().getName(), resolveProtocolAddress(gatt))) {
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, context.getString(R.string.connect_fail), Toast.LENGTH_SHORT).show();
                        }
                    });
                    gatt.disconnect();
                }
            } else if (bleDeviceType == BLEDevice.TYPE_QIYI_TIMER) {
                if (smartTimerProtocol == null || !smartTimerProtocol.start(gatt, service, gatt.getDevice().getName(), resolveProtocolAddress(gatt))) {
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, context.getString(R.string.connect_fail), Toast.LENGTH_SHORT).show();
                        }
                    });
                    gatt.disconnect();
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // 以下 legacy 逻辑仅作旧代码备份，当前产品主链不会再进入这些分支。
            UUID uuid = characteristic.getUuid();
            byte[] value = characteristic.getValue();
            //Log.w("dct", "uuid "+uuid.toString()+" value "+Arrays.toString(value));
            if (uuid.equals(CHARACTER_UUID_DATA)) {
                Log.w("dct", "value "+ Arrays.toString(value));
                byte[] valhex = Decrypt.toHexValue(value);
                Log.w("dct", "valhex "+Arrays.toString(valhex));
                String cubeState = Utils.parseGiikerState(valhex);
                Log.w("dct", "state " + cubeState);
                smartCube.setCubeState(cubeState);
                if (gatt.setCharacteristicNotification(characteristic, true)) {
                    for (BluetoothGattDescriptor descriptor: characteristic.getDescriptors()) {
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        } else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        }
                        gatt.writeDescriptor(descriptor);
                    }
                } else Log.e("dct", "无法监听");
            } else if (uuid.equals(CHARACTER_UUID_READ)) {
                Log.w("dct", "read "+Arrays.toString(value));
                service = gatt.getService(SERVICE_UUID_GIIKER);
                if (service == null) Log.e("dct", "service为null");
                else {
                    BluetoothGattCharacteristic chr = service.getCharacteristic(CHARACTER_UUID_DATA);
                    gatt.readCharacteristic(chr);
                }
            }
            if (uuid.equals(CHARACTER_UUID_VERSION)) {
                int version = (value[0] & 0xff) << 16 | (value[1] & 0xff) << 8 | (value[2] & 0xff);
                Log.w("dct", "版本号 "+version);
                smartCube.setVersion(version);
                if (version > 0x10007 && (version & 0xfffe00) == 0x010000) {
                    //BluetoothGattService service = mBluetoothGatt.getService(SERVICE_UUID);
                    if (service == null) Log.e("dct", "service为null");
                    else {
                        BluetoothGattCharacteristic chhw = service.getCharacteristic(CHARACTER_UUID_HARDWARE);
                        gatt.readCharacteristic(chhw);
                    }
                } else Log.e("dct", "不支持的版本");
            } else if (uuid.equals(CHARACTER_UUID_HARDWARE)) {
                byte[] key = Decrypt.getKey(smartCube.getVersion(), value);
                if (key == null) Log.e("dct", "不支持的硬件");
                else {
                    Log.w("dct", "key "+ StringUtils.binaryArray(key));
                    Decrypt.initAES(key);
                    service = gatt.getService(SERVICE_UUID_GAN);
                    if (service == null) Log.e("dct", "service为null");
                    else {
                        BluetoothGattCharacteristic chf2 = service.getCharacteristic(CHARACTER_UUID_F2);
                        gatt.readCharacteristic(chf2);
                    }
                }
            } else if (uuid.equals(CHARACTER_UUID_F2)) {
                //Log.w("dct", "f2 value: " + StringUtils.binaryArray(value));
                if (bleDeviceType == BLEDevice.TYPE_GANI_CUBE) {
                    value = Decrypt.decode(value);
                    Log.w("dct", "cube decode "+StringUtils.binaryArray(value));
                    String state = Utils.getCubeState(value);
                    Log.w("dct", "state "+state);
                    int check = smartCube.setCubeState(state);
                    Log.w("dct", "check "+check);
                    if (check != 0) {
                        smartCube.setCubeState("UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB");
                    }
                    Log.w("dct", "facelet " + smartCube.getCubeState());
                    if (service == null) Log.e("dct", "service为null");
                    else {
                        BluetoothGattCharacteristic chf7 = service.getCharacteristic(CHARACTER_UUID_F7);
                        gatt.readCharacteristic(chf7);
                    }
                }
            } else if (uuid.equals(CHARACTER_UUID_F3)) {
                //Log.w("dct", "f3 data "+StringUtils.binaryArray(value));
                value = Decrypt.decode(value);
                Log.w("dct", "f3 decode "+StringUtils.binaryArray(value));
                if (service == null) Log.e("dct", "service为null");
                else {
                    BluetoothGattCharacteristic chf5 = service.getCharacteristic(CHARACTER_UUID_F5);
                    gatt.readCharacteristic(chf5);
                }
            }
            else if (uuid.equals(CHARACTER_UUID_F5)) {
                //Log.w("dct", "gyro state "+StringUtils.binaryArray(value));
                value = Decrypt.decode(value);
                //Log.w("dct", "gyro decode "+StringUtils.binaryArray(value));
                int moveCnt = value[12] & 0xff;
                if (prevMoveCnt < 0) prevMoveCnt = moveCnt;
                if (moveCnt == prevMoveCnt) {
                    if (service == null) Log.e("dct", "service为null");
                    else {
                        long timePassed = System.currentTimeMillis() - lastTime;
                        if (timePassed >= 60000) {  //获取电量
                            lastTime = System.currentTimeMillis();
                            BluetoothGattCharacteristic chf7 = service.getCharacteristic(CHARACTER_UUID_F7);
                            gatt.readCharacteristic(chf7);
                        } else {
                            try {
                                Thread.sleep(200);
                            } catch (Exception e) { }
                            BluetoothGattCharacteristic chf5 = service.getCharacteristic(CHARACTER_UUID_F5);
                            gatt.readCharacteristic(chf5);
                        }
                    }
                } else {
                    int moves = moveCnt - prevMoveCnt;
                    if (moves < 0) moves += 256;
                    prevMoveCnt = moveCnt;
                    StringBuilder sb = new StringBuilder();
                    for (int i=0; i<6; i++) {
                        int m = value[13 + i];
                        //Log.w("dct", "move: "+m);
                        if (m >= 0) {
                            sb.append("URFDLB".charAt(m/3)).append(" 2'".charAt(m%3)).append(" ");
                            if (i >= 6 - moves) {
                                preMoves.add(m);
                            }
                        }
                    }
                    //Log.w("dct", "move data "+sb.toString());
                    //BluetoothGattService service = mBluetoothGatt.getService(SERVICE_UUID_GAN);
                    if (service == null) Log.e("dct", "service为null");
                    else {
                        BluetoothGattCharacteristic chf6 = service.getCharacteristic(CHARACTER_UUID_F6);
                        gatt.readCharacteristic(chf6);
                    }
                }
            } else if (uuid.equals(CHARACTER_UUID_F6)) {
                //Log.w("dct", "move data "+StringUtils.binaryArray(value));
                value = Decrypt.decode(value);
                //Log.w("dct", "move decode "+StringUtils.binaryArray(value));
                int[] timeOffset = new int[9];
                for (int i = 0; i < 9; i++) {
                    timeOffset[i] = (value[i * 2 + 1] & 0xff) | (value[i * 2 + 2] & 0xff) << 8;
                }
                //Log.w("dct", "time off "+Arrays.toString(timeOffset));
                if (preMoves.size() > 0) {
                    int start = 9 - preMoves.size();
                    for (int i = 0; i < preMoves.size(); i++) {
                        int move = preMoves.get(i);
                        int time = timeOffset[start++];
                        context.moveCube(smartCube, move, time);
                    }
                    preMoves.clear();
                }
                //BluetoothGattService service = mBluetoothGatt.getService(SERVICE_UUID_GAN);
                if (service == null) Log.e("dct", "service为null");
                else {
                    BluetoothGattCharacteristic chf5 = service.getCharacteristic(CHARACTER_UUID_F5);
                    gatt.readCharacteristic(chf5);
                }
            } else if (uuid.equals(CHARACTER_UUID_F7)) {
                //Log.w("dct", "f7 data "+StringUtils.binaryArray(value));
                lastTime = System.currentTimeMillis();
                byte[] decode = Decrypt.decode(value);
                //Log.w("dct", "f7 decode "+StringUtils.binaryArray(decode));
                //String address = gatt.getDevice().getAddress();
                Log.w("dct", "电池电量 "+decode[7]);
                if (smartCube != null)
                    smartCube.setBatteryValue(decode[7]);
                else Log.e("dct", "cube为null");
                //BluetoothGattService service = mBluetoothGatt.getService(SERVICE_UUID_GAN);
                if (service == null) Log.e("dct", "service为null");
                else {
                    BluetoothGattCharacteristic chf5 = service.getCharacteristic(CHARACTER_UUID_F5);
                    gatt.readCharacteristic(chf5);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            UUID uuid = characteristic.getUuid();
            Log.w("dct", "write "+uuid.toString()+" status "+status + " value "+Arrays.toString(characteristic.getValue()));
            if (smartCubeProtocol != null) {
                smartCubeProtocol.onCharacteristicWrite(characteristic, status);
                return;
            }
            if (smartTimerProtocol != null) {
                smartTimerProtocol.onCharacteristicWrite(characteristic, status);
                return;
            }
            if (uuid.equals(CHARACTER_UUID_WRITE)) {
//                BluetoothGattCharacteristic chr = service.getCharacteristic(CHARACTER_UUID_READ);
//                if (chr == null) {
//                    Log.e("dct", "获取设备信息失败");
//                } else {
//                    Log.w("dct", "获取电量");
//                    gatt.readCharacteristic(chr);
//                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            UUID uuid = characteristic.getUuid();
            byte[] value = characteristic.getValue();
            if (smartCubeProtocol != null) {
                smartCubeProtocol.onCharacteristicChanged(characteristic);
                return;
            }
            if (smartTimerProtocol != null) {
                smartTimerProtocol.onCharacteristicChanged(characteristic);
                return;
            }
            Log.w("dct", "value changed "+uuid.toString()+" value "+Arrays.toString(value));
            if (uuid.equals(CHARACTER_UUID_DATA)) {
                byte[] valhex = Decrypt.toHexValue(value);
                //Log.w("dct", "valhex "+Arrays.toString(valhex));
                String cubeState = Utils.parseGiikerState(valhex);
                Log.w("dct", "state " + cubeState);
                int check = smartCube.setCubeState(cubeState);
                if (check != 0) smartCube.setCubeState("UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB");
//                StringBuilder sb = new StringBuilder();
//                for (int i = 32; i < Math.min(40, valhex.length); i += 2) {
//                    sb.append("BDLURF".charAt(valhex[i] - 1)).append(" 2'".charAt((valhex[i + 1] - 1) % 7)).append(' ');
//                }
//                Log.w("dct", "move data "+sb.toString());
                int[] moveIdx = {5, 3, 4, 0, 1, 2};
                int move = moveIdx[valhex[32] - 1] * 3 + (valhex[33] - 1) % 7;
                long timeNow = System.currentTimeMillis();
                int time;
                if (lastTime == -1) {
                    time = 65535;
                } else {
                    time = (int) (timeNow - lastTime);
                    if (time > 65535) time = 65535;
                }
                context.moveCube(smartCube, move, time);
                lastTime = timeNow;
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (smartCubeProtocol != null) {
                smartCubeProtocol.onDescriptorWrite(descriptor, status);
            } else if (smartTimerProtocol != null) {
                smartTimerProtocol.onDescriptorWrite(descriptor, status);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.w("dct", "onMtuChanged mtu=" + mtu + " status=" + status);
            if (smartCubeProtocol != null) {
                smartCubeProtocol.onMtuChanged(mtu, status);
            } else if (smartTimerProtocol != null) {
                smartTimerProtocol.onMtuChanged(mtu, status);
            }
        }
    };
}
