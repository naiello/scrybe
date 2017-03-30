package com.example.nick.testreceiver;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

/**
 * An {@link Activity} showing a tuggable "Hello World!" card.
 * <p>
 * The main content view is composed of a one-card {@link CardScrollView} that provides tugging
 * feedback to the user when swipe gestures are detected.
 * If your Glassware intends to intercept swipe gestures, you should set the content view directly
 * and use a {@link com.google.android.glass.touchpad.GestureDetector}.
 *
 * @see <a href="https://developers.google.com/glass/develop/gdk/touch">GDK Developer Guide</a>
 */
public class ReaderActivity extends Activity {

    private final String TAG = "ReaderActivity";
    private BluetoothDevice mDevice;
    private View mView;
    private boolean mIsConnected = false;
    private final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final String mServerAddr = "Nick Aiello (SM-P607T)";
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getName().equals(mServerAddr)) {
                    mDevice = device;
                    unregisterReceiver(this);
                    mBluetoothAdapter.cancelDiscovery();
                    updateView(getString(R.string.listening));

                    ReaderClientThread thread = new ReaderClientThread();
                    thread.start();
                }
            }
        }
    };

    private class ReaderClientThread extends Thread {
        private final BluetoothSocket mmSocket;

        public ReaderClientThread() {
            BluetoothSocket tmp = null;
            Log.d(TAG, "creating socket");
            try {
                tmp = mDevice.createInsecureRfcommSocketToServiceRecord(mUUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed");
            }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "attempting to connect to " + mDevice.getAddress());
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    Log.e(TAG, e.getMessage());
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "help help help help help");
                }
            }

            try {
                Log.d(TAG, "connected!");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(mmSocket.getInputStream()));
                String line = null;
                while (mmSocket.isConnected()) {
                    line = reader.readLine();
                    Log.d(TAG, line);
                    updateViewUiThread(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "read failed");
            }
        }

        public void updateViewUiThread(String s) {
            final String cp = s;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateView(cp);
                }
            });
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() failed");
            }
        }
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        mView = buildView(getString(R.string.waiting_pair));
        setContentView(mView);

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 0);
        }

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBroadcastReceiver, filter);

        mBluetoothAdapter.startDiscovery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //mCardScroller.activate();
    }

    @Override
    protected void onPause() {
        //mCardScroller.deactivate();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        /*if (mListener != null) {
            mListener.cancel();
        }*/
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
    }



    /**
     * Builds a Glass styled "Hello World!" view using the {@link CardBuilder} class.
     */
    private View buildView(String s) {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);
        card.setText(s);
        return card.getView();
    }

    private void updateView(String s) {
        mView = buildView(s);
        setContentView(mView);
    }

}
