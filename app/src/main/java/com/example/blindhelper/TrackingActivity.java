package com.example.blindhelper;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import static android.content.ContentValues.TAG;

public class TrackingActivity extends Activity {
    private String mDeviceName = null;
    private String mDeviceAddress = null;
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    private static final int REQUEST_WRITE_STORAGE = 3;

    private TextView mConnectionState;
    private TextView mDataField;
    private ExpandableListView mGattServicesList;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private Button mStopButton;
    private Button mPacketFormatButton;
    private TextView mInstructions;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        // We get the name and address saved in the file after configuration
        try {
            FileInputStream input = null;
            File mFile = new File(Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperConfig/" + "CaneSensor.txt");
            input = new FileInputStream(mFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(input));

            mDeviceName = br.readLine();
            mDeviceAddress = br.readLine();
            br.close();
            input.close();
        } catch(Exception e){
            e.printStackTrace();
            //Check if the configuration has been done
            AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
            builder1.setMessage(R.string.err_config_file);
            builder1.setCancelable(true);

            builder1.setPositiveButton(
                    "Back",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            Intent home = new Intent(TrackingActivity.this, FirstActivity.class);
                            startActivity(home);
                        }
                    });

            AlertDialog alert11 = builder1.create();
            alert11.show();
        }


        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        mInstructions = (TextView) findViewById(R.id.instruction);

        mStopButton = (Button) findViewById(R.id.stopButton);
        mStopButton.setOnClickListener(stopClickListener);
        mPacketFormatButton = (Button) findViewById(R.id.packetFormatButton);
        mPacketFormatButton.setOnClickListener(packetClickListener);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);


        // Request permissions to access external storage (necessary for data recording)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        } else {
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            Intent firstIntent = new Intent();
            firstIntent.putExtra("order","connect");
            firstIntent.putExtra("address",mDeviceAddress);
            startService(firstIntent);

        }
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RecordingService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                updateInstructionState(R.string.instruction_wait_service);
                invalidateOptionsMenu();
            } else if (RecordingService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                updateInstructionState(R.string.instruction_click_connect);
                invalidateOptionsMenu();
                clearUI();
            } /*else if (RecordingService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
                updateInstructionState(R.string.instruction_click_service);
            }*/
        }
    };


    // Stops recording received data into a file, but keeps connection and notifications open.
    private View.OnClickListener stopClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // close data stream to file
            Intent stopIntent =new Intent();
            stopIntent.putExtra("order","stop");

            // stops receiving notifications
            if (mNotifyCharacteristic != null) {
                //RecordingService.setCharacteristicNotification(mNotifyCharacteristic, false);
                mNotifyCharacteristic = null;
            }

            // set UI
            setInitialUI();
            mInstructions.setText(R.string.instruction_click_service);
            Toast.makeText(TrackingActivity.this,"Data recorded", Toast.LENGTH_LONG).show();
        }
    };


    private View.OnClickListener packetClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent packet_activity = new Intent(TrackingActivity.this, PacketActivity.class);
            startActivity(packet_activity);
        }
    };


    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    /*** The data is being received from the moment we click on this -> we create the file NOW ***/
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {

                        if ((PacketActivity.button_10000 == null) && (PacketActivity.button_12800 == null)){
                            Toast.makeText(TrackingActivity.this, "Please choose a packet format", Toast.LENGTH_LONG).show();
                            return true;
                        }

                        Intent createIntent= new Intent();
                        createIntent.putExtra("order","recording");
                        startService(createIntent);
                        // set UI
                        setRecordingUI();

                        // discover characteristics
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                //mBluetoothLeService.setCharacteristicNotification(
                                        //mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            //mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            //mBluetoothLeService.setCharacteristicNotification(
                                    //characteristic, true);
                            // Log.w(TAG, "Characteristic support notify");
                        } else Log.w(TAG, "Characteristic does not support notify");
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        Intent stopIntent = new Intent();
        stopIntent.putExtra("order","stop");
        startService(stopIntent);
        setInitialUI();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                    registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
                    Intent stopIntent = new Intent();
                    stopIntent.putExtra("order","connect");
                    stopIntent.putExtra("address",mDeviceAddress);
                    startService(stopIntent);
                } else {
                    finish();
                }
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* MOVED TO onRequestPermissionResult*/
        /***
         registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
         if (mBluetoothLeService != null) {
         final boolean result = mBluetoothLeService.connect(mDeviceAddress);
         Log.d(TAG, "Connect request result=" + result);
         }
         */
    }

    @Override
    protected void onPause() {
        super.onPause();
        /*** MOVED TO onDestroy to avoid stopping recording when leaving the app
         unregisterReceiver(mGattUpdateReceiver);*/
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /***/

        unregisterReceiver(mGattUpdateReceiver);
        /***/
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
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
                Intent connectIntent = new Intent();
                connectIntent.putExtra("order","connect");
                connectIntent.putExtra("address",mDeviceAddress);
                startService(connectIntent);
                return true;
            case R.id.menu_disconnect:
                Intent disconnectIntent = new Intent();
                disconnectIntent.putExtra("order","disconnect");
                startService(disconnectIntent);
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void setInitialUI(){
        // mFileName.setText("");
        mDataField.setText(R.string.no_data);
        mPacketFormatButton.setEnabled(true);
        //mFileName.setEnabled(true);
        mGattServicesList.setEnabled(true);
        mStopButton.setEnabled(false);
        mInstructions.setText(R.string.instruction_click_connect);
    }

    public void setRecordingUI(){
        mPacketFormatButton.setEnabled(false);
        //mFileName.setEnabled(false);
        mGattServicesList.setEnabled(false);
        mStopButton.setEnabled(true);
        updateInstructionState(R.string.recording);
    }


    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void updateInstructionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mInstructions.setText(resourceId);
            }
        });
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RecordingService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(RecordingService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(RecordingService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(RecordingService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
