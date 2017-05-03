package com.nan.transcrybe;

import com.google.android.glass.widget.Slider;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
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
        mTranscriptionView.setMovementMethod(new ScrollingMovementMethod());

        // Start the Bluetooth listener and attempt to connect
        BluetoothDevice device = getIntent().getExtras().getParcelable(getString(R.string.EXTRA_BLUETOOTH_DEVICE));
        mListener = new TranscriptionListenerThread(device);
        mListener.start();
    }

    private void onConnected() {
        // we're connected, so display the "listening..." text
        mTranscriptionView.setText(R.string.listening);
        mProgressSlider.hide();
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
        mTranscriptionView.setText(line);
    }

    private class TranscriptionListenerThread extends Thread {
        private BluetoothDevice mmBluetoothDevice;

        public TranscriptionListenerThread(BluetoothDevice device) {
            mmBluetoothDevice = device;
            Log.d(TAG, "Bluetooth listener created for " + device.getName());
        }

        private void onReceive(final String line) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TranscriptionActivity.this.onReceive(line);
                }
            });
        }

        // TODO: Refactor this to make it shorter
        @Override
        public void run() {
            BluetoothSocket socket = null;
            try {
                socket = mmBluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
                socket.connect();
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
                String text;

                while (socket.isConnected()) {
                    text = reader.readLine();
                    if (text != null) {
                        Log.v(TAG, "recv: " + text);
                        onReceive(text);
                    }
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
