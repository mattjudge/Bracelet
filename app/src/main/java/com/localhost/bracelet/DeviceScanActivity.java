package com.localhost.bracelet;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.ArrayList;

public class DeviceScanActivity extends AppCompatActivity {
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    Context mContext;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;


    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final String TAG = "GcmStart";

    private BroadcastReceiver mRegistrationBroadcastReceiver;

    private RecyclerView mDevicesRecyclerView;
    private RecyclerView.LayoutManager mDevicesLayoutManager;
    private DevicesRecyclerAdapter mDevicesRecyclerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        setContentView(R.layout.activity_device_scan);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mHandler = new Handler();

        mDevicesRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mDevicesRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mDevicesLayoutManager = new LinearLayoutManager(this);
        mDevicesRecyclerView.setLayoutManager(mDevicesLayoutManager);

        // specify an adapter (see also next example)
        mDevicesRecyclerAdapter = new DevicesRecyclerAdapter();
        mDevicesRecyclerView.setAdapter(mDevicesRecyclerAdapter);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // gcm
        mRegistrationBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean sentToken = KeyValueDB.getHasSentTokenToServer(context);
                if (sentToken) {
                    Toast.makeText(getApplicationContext(), R.string.gcm_send_message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), R.string.token_error_message, Toast.LENGTH_SHORT).show();
                }
            }
        };

        if (checkPlayServices()) {
            // Start IntentService to register this application with GCM.
            Intent intent = new Intent(this, RegistrationIntentService.class);
            startService(intent);
        }

        String lastAddress = KeyValueDB.getLastConnectedDeviceMac(mContext);
        if (lastAddress != null) {
            connectToNameAddress(KeyValueDB.getLastConnectedDeviceName(mContext), lastAddress);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu_device_control; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_device_scan, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_scan:
                mDevicesRecyclerAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
            case R.id.action_settings:
                final Intent intenta = new Intent(this, SettingsActivity.class);
                startActivity(intenta);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();

        // gcm
        LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                new IntentFilter("REGISTRATION_COMPLETE"));

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        // gcm
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegistrationBroadcastReceiver);
        // end gcm
        super.onPause();
        scanLeDevice(false);
        mDevicesRecyclerAdapter.clear();
    }

    private void connectToDevice(BluetoothDevice device){
        if (device == null) return;
        connectToNameAddress(device.getName(), device.getAddress());
    }

    private void connectToNameAddress(String name, String address){
        Log.w("connectToNameAddr", "name:" + name + ", add:" + address);
        final Intent intent = new Intent(this, DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, name);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, address);
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        startActivity(intent);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    public class DevicesRecyclerAdapter extends RecyclerView.Adapter<DevicesRecyclerAdapter.ViewHolder> {
        private ArrayList<BluetoothDevice> mLeDevices;

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DevicesRecyclerAdapter.ViewHolder vholder = (DevicesRecyclerAdapter.ViewHolder) view.getTag();
                int pos = vholder.getPosition();

                BluetoothDevice device = mLeDevices.get(pos);
                connectToDevice(device);
            }
        };

        // Provide a reference to the views for each data item
        // Complex data items may need more than one view per item, and
        // you provide access to all the views for a data item in a view holder
        public class ViewHolder extends RecyclerView.ViewHolder {
            // each data item is just a string in this case
            public TextView mTextViewAddress;
            public TextView mTextViewName;
            public ViewHolder(View view) {
                super(view);
                this.mTextViewAddress = (TextView) view.findViewById(R.id.device_address);
                this.mTextViewName = (TextView) view.findViewById(R.id.device_name);

            }
        }

        public DevicesRecyclerAdapter() {
            mLeDevices = new ArrayList<BluetoothDevice>();
        }

        public void addDevice(BluetoothDevice device) {
            Log.w("adddevice", device.toString());
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
                notifyItemInserted(mLeDevices.size() - 1);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getItemCount() {
            return mLeDevices.size();
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        // Create new views (invoked by the layout manager)
        @Override
        public DevicesRecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            // create a new view
            View v = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.content_device_scan, null);
            // set the view's size, margins, paddings and layout parameters
            DevicesRecyclerAdapter.ViewHolder vh = new DevicesRecyclerAdapter.ViewHolder(v);
            return vh;
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(DevicesRecyclerAdapter.ViewHolder holder, int position) {
            // - get element from your dataset at this position
            // - replace the contents of the view with that element
            BluetoothDevice device = mLeDevices.get(position);
            holder.mTextViewAddress.setText(device.getAddress());
            holder.mTextViewName.setText(device.getName());

            //Handle click event on both title and image click
            holder.mTextViewAddress.setOnClickListener(clickListener);
            holder.mTextViewName.setOnClickListener(clickListener);

            holder.mTextViewAddress.setTag(holder);
            holder.mTextViewName.setTag(holder);

        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String lastConnectedMac = KeyValueDB.getLastConnectedDeviceMac(getApplicationContext());
                            if (lastConnectedMac != null && lastConnectedMac.equals(device.getAddress())) {
                                Toast.makeText(getApplicationContext(), "found same mac" + device.getAddress(), Toast.LENGTH_SHORT).show();
                                connectToDevice(device);
                            }
                            mDevicesRecyclerAdapter.addDevice(device);
                        }
                    });
                }
            };

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }
}
