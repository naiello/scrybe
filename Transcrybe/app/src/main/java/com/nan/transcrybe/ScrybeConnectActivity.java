package com.nan.transcrybe;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.glass.view.MenuUtils;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An {@link Activity} to allow the Scrybe client to discover and connect to
 * Scrybe servers via Bluetooth.
 */

public class ScrybeConnectActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "ScrybeConnect";
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothListener mBluetoothListener = new BluetoothListener();

    // Used to save the state of the device list so it doesn't get modified while the user
    // is in the menu screen.
    private BluetoothDevice[] mDeviceCache;

    /**
     * A {@link BroadcastReceiver} to maintain a list of Bluetooth devices discovered by the OS
     */
    private class BluetoothListener extends BroadcastReceiver {
        private int mmDeviceCount = 0;
        private ConcurrentLinkedQueue<BluetoothDevice> mmBluetoothDevices
                = new ConcurrentLinkedQueue<BluetoothDevice>();

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mmBluetoothDevices.add(device);
                postUpdate(device);
                mmDeviceCount++;
                Log.d(TAG, "Discovered " + device.getName());
            }
        }

        private void postUpdate(final BluetoothDevice device) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleNewDevice(device);
                }
            });
        }

        public int getDeviceCount() {
            return mmDeviceCount;
        }

        public BluetoothDevice[] getDevices() {
            return mmBluetoothDevices.toArray(new BluetoothDevice[0]);
        }
    }

    private void handleNewDevice(BluetoothDevice device) {
        TextView statusText = (TextView)findViewById(R.id.devices_found);
        statusText.setText(mBluetoothListener.getDeviceCount() +  " "
                + getString(R.string.devices_found));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_connect);

        RelativeLayout layout = (RelativeLayout)findViewById(R.id.activity_connect_layout);
        layout.setOnClickListener(this);
        layout.setFocusable(true);
        layout.setFocusableInTouchMode(true);

        // Request to enable bluetooth if it isn't already
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 0);
        }

        // Setup our listener to detect newly discovered devices
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBluetoothListener, filter);
        mBluetoothAdapter.startDiscovery();
    }

    @Override
    protected void onPause() {
        //mBluetoothAdapter.cancelDiscovery();
        super.onPause();
    }

    @Override
    protected void onResume() {
        //mBluetoothAdapter.startDiscovery();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mBluetoothListener);
        mBluetoothAdapter.cancelDiscovery();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        openOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        BluetoothDevice device = mDeviceCache[item.getItemId()];
        Log.d(TAG, "Selected " + device.getName() + " at " + device.getAddress());

        Intent intent = new Intent(getBaseContext(), TranscriptionActivity.class);
        intent.putExtra(getString(R.string.EXTRA_BLUETOOTH_DEVICE), device);

        startActivity(intent);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int iDevice = 0;
        menu.clear();
        mDeviceCache = mBluetoothListener.getDevices();
        for (BluetoothDevice device : mDeviceCache) {
            String name = device.getName();
            if (name == null) {
                name = "Unknown Device";
            }
            MenuItem item = menu.add(Menu.NONE, iDevice, iDevice, name);
            MenuUtils.setDescription(item, R.string.tap_to_connect);
            iDevice++;
        }
        return true;
    }

}
