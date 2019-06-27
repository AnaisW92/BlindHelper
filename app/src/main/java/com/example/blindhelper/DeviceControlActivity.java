/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.blindhelper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import static com.example.blindhelper.PacketFormat.SAMPLE_BYTES;
import static com.example.blindhelper.PacketFormat.SAMPLE_TIME;
import static com.example.blindhelper.PacketFormat.SEQNBR_BYTES;
import static com.example.blindhelper.PacketFormat.TIME_BYTES;


/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private File mFile = null;
    private static FileOutputStream output = null;

    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    private static final int REQUEST_WRITE_STORAGE = 3;

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private Button mStopButton;
    private Button mPacketFormatButton;
    private TextView mInstructions;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


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
                updateInstructionState(R.string.instruction_wait_service);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                warningDeconnection();
                updateInstructionState(R.string.instruction_click_connect);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
                updateInstructionState(R.string.instruction_click_service);
                //mBluetoothLeService.requestMTU(); /*********************************************/
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                /*final byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA );

                if (data != null) {
                    Log.v("FILE", "Received data length : " + data.length);
                    // Convert the received Byte array to a meaningful Integer array, and store it in file.
                    // Format :
                    // ax,ay,az,gx,gy,gz,time
                    if (PacketActivity.button_10000.isChecked())
                        writeFile10000(data, SEQNBR_BYTES, SAMPLE_TIME, SAMPLE_BYTES);
                    else if (PacketActivity.button_12800.isChecked())
                        writeFile12800(data, TIME_BYTES);
                } else Log.v("FILE", "data NULL");

                // displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));*/
            }
        }
    };

    // Stops recording received data into a file, but keeps connection and notifications open.
    private View.OnClickListener stopClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // close data stream to file
            mBluetoothLeService.stopRecording();
            //Intent gattServiceIntent = new Intent(DeviceControlActivity.this, BluetoothLeService.class);
            //stopService(gattServiceIntent);

            // stops receiving notifications
            if (mNotifyCharacteristic != null) {
                mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
                mNotifyCharacteristic = null;
            }

            // set UI
            setInitialUI();
            mInstructions.setText(R.string.instruction_click_service);
            Toast.makeText(DeviceControlActivity.this,"Data recorded", Toast.LENGTH_LONG).show();
        }
    };


    private View.OnClickListener packetClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent packet_activity = new Intent(DeviceControlActivity.this, PacketActivity.class);
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
                            Toast.makeText(DeviceControlActivity.this, "Please choose a packet format", Toast.LENGTH_LONG).show();
                            return true;
                        }

                        // create file for saving data
                        if (mBluetoothLeService.createDataFile() == null){
                            Log.v("FILE", "Not created");
                            return true;
                        }
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
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                            // Log.w(TAG, "Characteristic support notify");

                        } else Log.w(TAG, "Characteristic does not support notify");
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mBluetoothLeService.stopRecording();
        setInitialUI();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        //final Intent intent = getIntent(); // returns the intent that started this activity
        // it is DeviceScanActivity, qui a mis en extra le nom et l'adresse du device sur lequel
        // on a cliqué

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
                            Intent home = new Intent(DeviceControlActivity.this, FirstActivity.class);
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
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                    registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
                    if (mBluetoothLeService != null) {
                        final boolean result = mBluetoothLeService.connect(mDeviceAddress);
                        Log.d(TAG, "Connect request result=" + result);
                    }
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
        //Toast.makeText(DeviceControlActivity.this,"EN PAUSE",Toast.LENGTH_LONG).show();
        /*** MOVED TO onDestroy to avoid stopping recording when leaving the app
         unregisterReceiver(mGattUpdateReceiver);*/
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /***/

        unregisterReceiver(mGattUpdateReceiver);
        /***/
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
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
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
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

    private void updateInstructionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mInstructions.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }



    // return the path to the file created
    // return null if no file name was provided or if the file cannot be created



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

    public void warningDeconnection() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            VibrationEffect effect = VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE);
            vibrator.vibrate(effect);
        } else vibrator.vibrate(1000);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DeviceControlActivity.this);

        // set title
        alertDialogBuilder.setTitle(R.string.disconnected);

        // set dialog message
        alertDialogBuilder
                .setNeutralButton("OK",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Si on a appuyé sur le retour arrière
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            Intent secondeActivite = new Intent(DeviceControlActivity.this,
                    FirstActivity.class);
            startActivity(secondeActivite);

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed(){
        Intent secondeActivite = new Intent(DeviceControlActivity.this,
                FirstActivity.class);
        startActivity(secondeActivite);
    }

}
