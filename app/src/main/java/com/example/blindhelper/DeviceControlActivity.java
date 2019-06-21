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
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
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
    private EditText mFileName;
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

                final byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA );

                if (data != null) {
                    Log.v("FILE", "Received data length : " + data.length);
                    // Convert the received Byte array to a meaningful Integer array, and store it in file.
                    // Format :
                    // ax,ay,az,gx,gy,gz,time
                    if (PacketFormatActivity.button_10000.isChecked())
                        writeFile10000(data, SEQNBR_BYTES, SAMPLE_TIME, SAMPLE_BYTES);
                    else if (PacketFormatActivity.button_12800.isChecked())
                        writeFile12800(data, TIME_BYTES);
                } else Log.v("FILE", "data NULL");

                // displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // Stops recording received data into a file, but keeps connection and notifications open.
    private View.OnClickListener stopClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // close data stream to file
            stopRecording();

            // stops receiving notifications
            if (mNotifyCharacteristic != null) {
                mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
                mNotifyCharacteristic = null;
            }

            // set UI
            setInitialUI();
            mInstructions.setText(R.string.instruction_click_service);
            Toast.makeText(DeviceControlActivity.this,"Data saved to : " + mFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    };


    private View.OnClickListener packetClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent packet_activity = new Intent(DeviceControlActivity.this, PacketFormatActivity.class);
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

                        if ((PacketFormatActivity.button_10000 == null) && (PacketFormatActivity.button_12800 == null)){
                            Toast.makeText(DeviceControlActivity.this, "Please choose a packet format", Toast.LENGTH_LONG).show();
                            return true;
                        }

                        // create file for saving data
                        if (createDataFile() == null){
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
        stopRecording();
        setInitialUI();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent(); // returns the intent that started this activity
        // it is DeviceScanActivity, qui a mis en extra le nom et l'adresse du device sur lequel
        // on a cliqué
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        mInstructions = (TextView) findViewById(R.id.instruction);
        mFileName = (EditText) findViewById(R.id.file_name);
        mStopButton = (Button) findViewById(R.id.stopButton);
        mStopButton.setOnClickListener(stopClickListener);
        mPacketFormatButton = (Button) findViewById(R.id.packetFormatButton);
        mPacketFormatButton.setOnClickListener(packetClickListener);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Request permissions to access external storage (necessary for data recording)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        } else {
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            if (mBluetoothLeService != null) {
                final boolean result = mBluetoothLeService.connect(mDeviceAddress);
                Log.d(TAG, "Connect request result=" + result);
            }
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
    public String createDataFile(){
        String path = null;
        String file_name = mFileName.getText().toString();

        if(TextUtils.isEmpty(file_name)) {
            mFileName.setError("Required");
            return null;
        }

        // If there is external and writable storage
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
            try {
                path = Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + file_name + ".txt";
                mFile = new File(path);

                mFile.createNewFile(); // create new file if it does not already exists

                output = new FileOutputStream(mFile);
                Log.v("FILE", "File created");
                path = mFile.getPath();
                mDataField.setText("Saving to : " + path);

            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(DeviceControlActivity.this, R.string.error_no_ext_storage, Toast.LENGTH_LONG).show();
        }
        return path;
    }

    public void writeFile(StringBuilder stringData){
        try {
            //Log.v("FILE", stringData.toString());
            if (output != null)
                output.write(stringData.toString().getBytes());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeFile10000(byte[] data, int seqNbrBytes, int sampleTime, int sampleBytes){
        final StringBuilder stringData = new StringBuilder(data.length-seqNbrBytes + (data.length-seqNbrBytes)/2 + (seqNbrBytes+2)*sampleBytes); // memory for data, for comas, for time
        int storeData[] = new int[(data.length - seqNbrBytes)/2]; // to store integers (2 bytes)
        //float seqNbr =(float) ( ((data[0] & 0xFF) << 16) + ((data[1] & 0xFF) << 8) + (data[2] & 0xFF) );
        long seqNbr =(long) ( (((data[data.length-1] & 0xF0L)) << 12) + ((data[data.length-2] & 0xFFL) << 8) + (data[data.length-3] & 0xFFL) );

        int count = 0;

        //seqNbr = seqNbr - 1/(float)mSAMPLES_PER_PACKET;
        seqNbr = seqNbr - sampleTime;

        for(int i = 0; i < (data.length - seqNbrBytes); i += 2) {
            // Store unsigned int
            //storeData[i/2] = ( (data[i]& 0XFF) << 8 ) + ( data[i + 1] & 0xFF );
            storeData[i/2] = ( (data[i+1]& 0XFF) << 8 ) + ( data[i] & 0xFF );
            /*storeData[i/2] = ( (data[i+mHEADER_BYTES]& 0XFF) << 8 ) + ( data[i+mHEADER_BYTES + 1] & 0xFF );*/

            // Convert unsigned to signed
            if ((storeData[i/2] & (1 << 15)) != 0) { // 15 because we receive 16-bits signed integers
                storeData[i/2] = -1 * ((1 << 15) - (storeData[i/2] & ((1 << 15) - 1)));
            }
            stringData.append(storeData[i/2]);
            count++;

            // Add a coma or a new line
            if ( count%(sampleBytes/2) == 0 ){
                //seqNbr += 1/(float)mSAMPLES_PER_PACKET;
                seqNbr += sampleTime;
                stringData.append(',');
                stringData.append(seqNbr);
                stringData.append("\n");
            } else stringData.append(',');
        }

        writeFile(stringData);
    }

    public void writeFile12800(byte[] data, int timeBytes){
        if (data.length == 16) {
            final StringBuilder stringData = new StringBuilder(data.length + (data.length) / 2); // memory for data, for comas, for time
            int storeData[] = new int[(data.length) / 2]; // to store integers (2 bytes)
            long seqNbr = (long) ((((data[data.length - 1] & 0xFFL)) << 24) + ((data[data.length - 2] & 0xFFL) << 16) + ((data[data.length - 3] & 0xFFL) << 8) + (data[data.length - 4] & 0xFFL));

            for (int i = 0; i < (data.length - timeBytes); i += 2) {
                // Store unsigned int
                storeData[i / 2] = ((data[i + 1] & 0XFF) << 8) + (data[i] & 0xFF);

                // Convert unsigned to signed
                if ((storeData[i / 2] & (1 << 15)) != 0) { // 15 because we receive 16-bits signed integers
                    storeData[i / 2] = -1 * ((1 << 15) - (storeData[i / 2] & ((1 << 15) - 1)));
                }

                stringData.append(storeData[i / 2]);
                stringData.append(',');
            }
            stringData.append(seqNbr);
            stringData.append("\n");

            writeFile(stringData);
        }
    }

    public void stopRecording(){
        try {
            if (output != null)
                output.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        output = null;
    }

    public void setInitialUI(){
        mFileName.setText("");
        mDataField.setText(R.string.no_data);
        mPacketFormatButton.setEnabled(true);
        mFileName.setEnabled(true);
        mGattServicesList.setEnabled(true);
        mStopButton.setEnabled(false);
        mInstructions.setText(R.string.instruction_click_connect);
    }

    public void setRecordingUI(){
        mPacketFormatButton.setEnabled(false);
        mFileName.setEnabled(false);
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


}
