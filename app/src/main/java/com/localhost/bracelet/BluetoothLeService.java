package com.localhost.bracelet;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    Context mContext;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static String REQUIRE_CONNECTION_INFO =
            "com.example.bluetooth.le.REQUIRE_CONNECTION_INFO";
    public final static String CONNECTION_INFO =
            "com.example.bluetooth.le.CONNECTION_INFO";
    public final static String REQUIRE_CONNECTION_STATE =
            "com.example.bluetooth.le.REQUIRE_CONNECTION_STATE";

    public final static UUID UUID_SERIAL_COMMUNICATION =
            UUID.fromString(KnownGattAttributes.SERIAL_COMMUNICATION);
    public final static UUID UUID_SERIAL_COMMUNICATION_SERVICE =
            UUID.fromString(KnownGattAttributes.SERIAL_COMMUNICATION_SERVICE);
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                setCharacteristicNotification(getSerialCharacteristic(), true);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            if (UUID_SERIAL_COMMUNICATION.equals(characteristic.getUuid())) {
                BraceletServerApi.sendMessage(getApplicationContext(),
                        characteristic.getStringValue(0));
            }
        }
    };

    private final BroadcastReceiver mGcmUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (MyGcmListenerService.ACTION_GCM_MESSAGE_AVAILABLE.equals(action)) {
                writeToSerialCharacteristic(
                        intent.getStringExtra(MyGcmListenerService.EXTRA_DATA));
            }
        }
    };

    private final BroadcastReceiver mDeviceControlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DeviceControlActivity.ACTION_DEVICE_CONNECT.equals(action)) {
                connect(intent.getStringExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS));
            }else if (DeviceControlActivity.ACTION_DEVICE_DISCONNECT.equals(action)) {
                disconnect();
            }else if (DeviceControlActivity.ACTION_KILL_BLE_SERVICE.equals(action)) {
                disconnect();
                stopSelf();
            }else if (REQUIRE_CONNECTION_INFO.equals(action)) {
                Intent conn_info_intent = new Intent(CONNECTION_INFO);
                conn_info_intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, mBluetoothDeviceAddress.toString());
                conn_info_intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, mBluetoothGatt.getDevice().getName().toString());
                sendBroadcast(conn_info_intent);
            }else if (REQUIRE_CONNECTION_STATE.equals(action)){
                broadcastCurrentStateConnection();
            }
        }
    };


    private void broadcastCurrentStateConnection(){
        String intentAction;
        if (mConnectionState == BluetoothProfile.STATE_CONNECTED) {
            intentAction = ACTION_GATT_CONNECTED;
            broadcastUpdate(intentAction);
        } else if (mConnectionState == BluetoothProfile.STATE_DISCONNECTED) {
            intentAction = ACTION_GATT_DISCONNECTED;
            broadcastUpdate(intentAction);
        }
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        if (!initialize()) {
            Log.e(TAG, "Unable to initialize Bluetooth");
            stopSelf();
        }
        // Automatically connects to the device upon successful start-up initialization.
        if (intent != null) {
            String deviceAddress = intent.getStringExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS);
            connect(deviceAddress);
        } else {
            // try and reconnect
            connect(KeyValueDB.getLastConnectedMac(this));
        }
        startForeground(1, new Notification());
        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        unregisterReceiver(mDeviceControlReceiver);
        unregisterReceiver(mGcmUpdateReceiver);
        Log.w("BleService", "KILLING BLE SERVICE");
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        registerReceiver(mGcmUpdateReceiver, makeGcmUpdateIntentFilter());
        registerReceiver(mDeviceControlReceiver, makeDeviceControlIntentFilter());

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address." + address + "," + mBluetoothAdapter.toString());
            return false;
        }

        KeyValueDB.setLastConnectedMac(this, address);
        Toast.makeText(getApplicationContext(), "saved mac" + address, Toast.LENGTH_SHORT).show();

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        //mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public void writeCharacteristicValueImmediate(BluetoothGattCharacteristic characteristic,
                                                  String value){
        if (characteristic == null)
            return;
        characteristic.setValue(value);
        writeCharacteristic(characteristic);
    }

    public BluetoothGattCharacteristic getSerialCharacteristic(){
        BluetoothGattService serialService = mBluetoothGatt.getService(UUID_SERIAL_COMMUNICATION_SERVICE);
        if (serialService == null)
            return null;
        return serialService.getCharacteristic(UUID_SERIAL_COMMUNICATION);
    }

    public void writeToSerialCharacteristic(String value){
        writeCharacteristicValueImmediate(getSerialCharacteristic(), value);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    private static IntentFilter makeGcmUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyGcmListenerService.ACTION_GCM_MESSAGE_AVAILABLE);
        return intentFilter;
    }

    private static IntentFilter makeDeviceControlIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DeviceControlActivity.ACTION_DEVICE_CONNECT);
        intentFilter.addAction(DeviceControlActivity.ACTION_DEVICE_DISCONNECT);
        intentFilter.addAction(DeviceControlActivity.ACTION_KILL_BLE_SERVICE);
        intentFilter.addAction(REQUIRE_CONNECTION_INFO);
        intentFilter.addAction(REQUIRE_CONNECTION_STATE);
        return intentFilter;
    }

}
