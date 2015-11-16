package com.localhost.bracelet;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class DeviceControlActivity extends AppCompatActivity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String ACTION_DEVICE_CONNECT = "ACTION_DEVICE_CONNECT";
    public static final String ACTION_DEVICE_DISCONNECT = "ACTION_DEVICE_DISCONNECT";
    public static final String ACTION_KILL_BLE_SERVICE = "ACTION_KILL_BLE_SERVICE";

    Context mContext;

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;

    // UI
    Button mButtonPokePartner;
    Button mButtonPokeSelf;
    Button mButtonStopBleService;

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };


    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    private void sendPartnerPoke(){
        // needs async task to prevent ui getting held up
        new AsyncTask<Void, Void, Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                BraceletServerApi.sendMessage(mContext, "POKE_PARTNER", BraceletServerApi.ASYNC);
                return null;
            }
        }.execute();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        setContentView(R.layout.activity_device_control);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        mButtonPokePartner = (Button) findViewById(R.id.button_poke_partner);
        mButtonPokePartner.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendPartnerPoke();
            }
        });
        mButtonPokeSelf = (Button) findViewById(R.id.button_poke_self);
        mButtonPokeSelf.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final Intent intent = new Intent(MyGcmListenerService.ACTION_GCM_MESSAGE_AVAILABLE);
                intent.putExtra(MyGcmListenerService.EXTRA_DATA, "POKE_SELF");
                sendBroadcast(intent);
            }
        });
        mButtonStopBleService = (Button) findViewById(R.id.button_stop_ble_service);
        mButtonStopBleService.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendBroadcast(new Intent(ACTION_KILL_BLE_SERVICE));
            }
        });



        getSupportActionBar().setTitle(mDeviceName);
        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (!isBleServiceRunning(BluetoothLeService.class)) {
            Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
            gattServiceIntent.putExtra(EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
            startService(gattServiceIntent);
            Log.w("devicecontrolac", "STARTING BLE SERVICE!");
        } else {
            // get current status
            sendBroadcast(new Intent(BluetoothLeService.REQUIRE_CONNECTION_STATE));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!mConnected) {
            sendBroadcast(new Intent(ACTION_KILL_BLE_SERVICE));
        }
        mBluetoothLeService = null;
    }

    @Override
     public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_device_control, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                sendBroadcast(new Intent(ACTION_DEVICE_CONNECT).putExtra(EXTRAS_DEVICE_ADDRESS, mDeviceAddress));
                return true;
            case R.id.menu_disconnect:
                KeyValueDB.setLastConnectedDeviceMac(mContext, null);
                sendBroadcast(new Intent(ACTION_DEVICE_DISCONNECT));
                final Intent intentb = new Intent(this, DeviceScanActivity.class);
                startActivity(intentb);
                return true;
            case R.id.action_settings:
                final Intent intenta = new Intent(this, SettingsActivity.class);
                startActivity(intenta);
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    private boolean isBleServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
