package com.nan.transcrybe;

import com.google.android.glass.widget.Slider;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.Buffer;
import java.util.UUID;

/**
 * An {@link Activity} to connect to a Bluetooth transcribe host and begin receiving text.
 */
public class TranscriptionActivity extends Activity {
    private static final String TAG = "TranscriptionActivity";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Slider.Indeterminate mProgressSlider;
    private TextView mTranscriptionView;

    private TranscriptionListenerThread mListener;
    private boolean mIsEmpty = true;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Initialize UI to "Connecting..." state and start the progress slider
        setContentView(R.layout.transcribe_layout);
        View view = findViewById(R.id.transcribe_layout);
        mProgressSlider = Slider.from(view).startIndeterminate();
        mTranscriptionView = (TextView) findViewById(R.id.transcription_text);

        // Start the Bluetooth listener and attempt to connect
        BluetoothDevice device = bundle.getParcelable(getString(R.string.EXTRA_BLUETOOTH_DEVICE));
        mListener = new TranscriptionListenerThread(device);
        mListener.start();
    }

    private void onConnected() {
        // we're connected, so display the "listening..." text
        mTranscriptionView.setText(R.string.listening);
    }

    private void onConnectFail() {
        Log.e(TAG, "Connection failed.");
        finish();
    }

    private void onDisconnected() {
        Log.e(TAG, "Disconnected");
        finish();
    }

    private void onReceive(String line) {
        if (mIsEmpty) {
            // allow the first line received to overwrite the "listening..." text
            mTranscriptionView.setText(line);
            mIsEmpty = false;
        } else {
            mTranscriptionView.append("\n" + line);
        }
    }

    private class TranscriptionListenerThread extends Thread {
        private BluetoothDevice mmBluetoothDevice;

        public TranscriptionListenerThread(BluetoothDevice device) {
            mmBluetoothDevice = device;
            Log.d(TAG, "Bluetooth listener created for " + device.getName());
        }

        // TODO: Refactor this to make it shorter
        @Override
        public void run() {
            BluetoothSocket socket = null;
            try {
                socket = mmBluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Connection to SPP service failed");
            }

            if (socket == null) {
                // run onConnectFail handler
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onConnectFail();
                    }
                });
                return;
            }

            // Run onConnect handler
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onConnected();
                }
            });
            Log.d(TAG, "Device connection successful.");

            try {
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;

                while (socket.isConnected() && (line = reader.readLine()) != null) {
                    Log.v(TAG, "recv: " + line);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onReceive(line);
                        }
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Bad read");
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onDisconnected();
                }
            });
        }
    }

}