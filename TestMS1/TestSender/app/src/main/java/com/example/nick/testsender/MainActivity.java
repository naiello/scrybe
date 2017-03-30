package com.example.nick.testsender;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.util.UUID;


public class MainActivity extends ActionBarActivity {
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothSocket mSocket = null;
    private boolean mIsConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        ListenThread thread = new ListenThread();
        thread.start();
    }

    @Override
    protected void onDestroy() {
        try {
            if (mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            Log.e("MainActivity", "close() failed");
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    public void handleSendPress(View v) {
        EditText textBox = (EditText) findViewById(R.id.editText);
        if (mSocket != null) {
            try {
                String msg = textBox.getText().toString() + "\n";
                mSocket.getOutputStream().write(msg.getBytes());
                textBox.setText("");
            } catch (IOException e) {
                Log.e("MainActivity", e.getMessage());
            }
        } else {
            textBox.setText("No connection");
        }
    }

    private void updateStatus(String s) {
        TextView text = (TextView)findViewById(R.id.statusView);
        text.setText(s);
    }

    private class ListenThread extends Thread {
        private final BluetoothServerSocket mServerSocket;
        private UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        private final String TAG = "LISTENTHREAD";

        public ListenThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("TestSender", mUUID);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed");
            }
            mServerSocket = tmp;
        }

        public void updateStatusUiThread(String s) {
            final String cp = s;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateStatus(cp);
                }
            });
        }

        public void run() {
            Log.d(TAG, "waiting for connection....................");
            while (true) {
                try {
                    Log.d(TAG, "call accept");
                    mSocket = mServerSocket.accept();
                    Log.d(TAG, "accepted");
                    if (mSocket != null) {
                        //mServerSocket.close();
                        mIsConnected = true;
                        updateStatusUiThread("Connection established.");
                        Log.d(TAG, "CONNECTION SUCCESSFUL~~~~~~~~~~~~");
                        break;
                    } else {
                        Log.d(TAG, "null socket");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed");
                    break;
                }
            }
        }
    }
}
