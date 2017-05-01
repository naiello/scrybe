package com.nan.scrybelistener;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;


public class ListenerActivity extends ActionBarActivity {
    private static final String TAG = "ListenerActivity";


    // UUID for the Bluetooth Serial Port Profile (SPP)
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String BT_SERVICE_NAME = "ScrybeListener";
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private List<BluetoothSocket> mActiveSockets = new LinkedList<BluetoothSocket>();

    private TextView mStatusText;
    private TextView mTranscriptText;
    private EditText mMessageText;
    private Button mSendButton;

    // Thread to listen for incoming bluetooth connections
    private NewConnectionListenerThread mListener;
    // Service to communicate with the Google Speech UI
    private SpeechService mSpeechService;

    private VoiceRecorder mVoiceRecorder;
    /**
     * Adapter class to pipe raw audio from the VoiceRecorder to the SpeechService.
     */
    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {
        @Override
        public void onVoiceStart() {
            if (mSpeechService != null) {
                mSpeechService.startRecognizing(mVoiceRecorder.getSampleRate());
            }
        }

        @Override
        public void onVoice(byte[] data, int size) {
            if (mSpeechService != null) {
                mSpeechService.recognize(data, size);
            }
        }

        @Override
        public void onVoiceEnd() {
            if (mSpeechService != null) {
                mSpeechService.finishRecognizing();
            }
        }
    };

    /**
     * Callback function to listen for responses from the Google API
     */
    private final SpeechService.Listener mSpeechServiceListener = new SpeechService.Listener() {
        @Override
        public void onSpeechRecognized(String text, boolean isFinal) {
            if (isFinal) {
                mVoiceRecorder.dismiss();
            }
            if (text != null && !TextUtils.isEmpty(text)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onTextReceived(text);
                    }
                });
            }
        }
    };

    /**
     * Manages connection to the SpeechService
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSpeechService = SpeechService.from(service);
            mSpeechService.addListener(mSpeechServiceListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSpeechService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listener);

        mStatusText = (TextView) findViewById(R.id.status_text);
        mTranscriptText = (TextView) findViewById(R.id.text_transcript);
        mMessageText = (EditText) findViewById(R.id.message_entry);
        mSendButton = (Button) findViewById(R.id.button_send);

        mSendButton.setEnabled(false);

        // Request to turn on bluetooth if needed
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        // Request to make device discoverable
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        mListener = new NewConnectionListenerThread();
        mListener.start();
    }

    @Override
    public void onDestroy() {
        for (BluetoothSocket socket : mActiveSockets) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "error closing socket");
            }
        }
        mListener.cancel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_listener, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Send button click handler.
     * @param view The send button.
     */
    public void onSendClick(View view) {
        String message = mMessageText.getText().toString();
        mTranscriptText.append(message + "\n");
        broadcastMessage(message);
        mMessageText.setText("");
    }

    /**
     * Called when text is received from the Google Cloud API
     * @param text
     */
    public void onTextReceived(String text) {
        mTranscriptText.append(text + "\n");
        broadcastMessage(text);
    }

    public void onRecordToggleClick(View view) {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        } else {
            mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
            mVoiceRecorder.start();
        }
    }

    /**
     * Broadcasts a message to all active sockets, removing any "dead" sockets found.
     * @param message The string to broadcast.
     */
    private void broadcastMessage(String message) {
        ListIterator<BluetoothSocket> iter = mActiveSockets.listIterator();
        boolean foundDeadSockets = false;
        Log.d(TAG, "send: " + message);

        // Broadcast message to all active sockets
        while (iter.hasNext()) {
            BluetoothSocket socket = iter.next();
            if (socket.isConnected()) {
                try {
                    String lineMessage = message + "\n";
                    socket.getOutputStream().write(lineMessage.getBytes());
                } catch (IOException e) {
                    Log.e(TAG, "bad write");
                }
            } else {
                Log.d(TAG, "Disconnected from " + socket.getRemoteDevice().getAddress());
                // socket is dead/disconnected, so remove from active socket list
                iter.remove();
                foundDeadSockets = true;
            }
        }

        if (foundDeadSockets) {
            updateSocketCount();
        }
    }

    /**
     * Add a new client listener.
     * @param socket The socket the client is connected on.
     */
    private void addActiveSocket(BluetoothSocket socket) {
        mActiveSockets.add(socket);
        updateSocketCount();
    }

    /**
     * Update the status text to accurately reflect the number of clients connected. Called
     * whenever a new client connects, or a client disconnects.
     */
    private void updateSocketCount() {
        int nActiveSockets = mActiveSockets.size();
        if (nActiveSockets == 0) {
            mSendButton.setEnabled(false);
            mStatusText.setText(R.string.waiting_for_connect);
        } else if (nActiveSockets == 1) {
            mSendButton.setEnabled(true);
            String deviceName = mActiveSockets.get(0).getRemoteDevice().getName();
            if (deviceName == null) {
                deviceName = "unknown device.";
            }
            mStatusText.setText(getString(R.string.connected_to) + " " + deviceName);
        } else {
            mSendButton.setEnabled(true);
            mStatusText.setText(getString(R.string.connected_to) + " "
                    + nActiveSockets + " devices.");
        }
    }

    /**
     * A {@link Thread} to listen for new connections and generate a {@link BluetoothSocket} for
     * each.
     */
    private class NewConnectionListenerThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private boolean mmIsListening = false;

        public NewConnectionListenerThread() {
            BluetoothServerSocket socket = null;
            try {
                socket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(BT_SERVICE_NAME, BT_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create RFCOMM socket.");
            }
            mmServerSocket = socket;
            mmIsListening = true;
        }

        @Override
        public void run() {
            while (mmIsListening) {
                try {
                    BluetoothSocket socket = mmServerSocket.accept();
                    Log.d(TAG, "Accepted connection from " + socket.getRemoteDevice().getAddress());
                } catch (IOException e) {
                    Log.e(TAG, "Failed to accept connection.");
                }
            }

            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "error closing server socket");
            }
        }

        public void cancel() {
            mmIsListening = false;
        }

        public void addActiveSocket(final BluetoothSocket socket) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ListenerActivity.this.addActiveSocket(socket);
                }
            });
        }
    }
}
