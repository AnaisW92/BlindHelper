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
import android.bluetooth.BluetoothGatt;
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
import java.util.Map;


/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class TrackingActivity extends Activity {
    private final static String TAG = TrackingActivity.class.getSimpleName();
    public static String HM_13 = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

    private TextView mConnectionStateCane;
    private TextView mConnectionStateTight;
    private boolean mConnectedCane = false;
    private boolean mConnectedTight = false;
    private boolean mServiceCane = false;
    private boolean mServiceTight = false;

    private Map<String, String> mSensorsMap;
    private RecordingService mRecordingService;
    private BluetoothGattCharacteristic mNotifyCharacteristicCane;
    private BluetoothGattCharacteristic mNotifyCharacteristicTight;
    private Button mStartButton;
    private Button mStopButton;
    private TextView mInstructions;

    public final static String CANE = "cane";
    public final static String TIGHT = "tight";
    private String PATH_CONFIG_CANE = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperConfig/" + "CaneSensor.txt";
    private String PATH_CONFIG_TIGHT = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperConfig/" + "TightSensor.txt" ;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mRecordingService = ((RecordingService.LocalBinder) service).getService();
            if (!mRecordingService.initialize(mSensorsMap)) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            if(!mRecordingService.connect(mSensorsMap.get(CANE))){

            }
            mRecordingService.connect(mSensorsMap.get(TIGHT));
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mRecordingService = null;
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
            String sensor = intent.getStringExtra(RecordingService.EXTRA_DATA);
            if (RecordingService.ACTION_GATT_CONNECTED.equals(action)) {
                if (sensor.equals(CANE)){
                    mConnectedCane = true;
                }
                else{
                    mConnectedTight = true;
                }
                updateConnectionState(R.string.connected,sensor);
                if (mConnectedCane &&mConnectedTight) {
                    updateInstructionState(R.string.instruction_wait_service);
                    invalidateOptionsMenu();
                }
            } else if (RecordingService.ACTION_GATT_DISCONNECTED.equals(action)) {
                if (sensor.equals(CANE)){
                    mConnectedCane = false;
                }
                else{
                    mConnectedTight = false;
                }
                updateConnectionState(R.string.disconnected,sensor);
                invalidateOptionsMenu();
                warningDeconnection(sensor);
                if(!(mConnectedCane||mConnectedTight)) {
                    updateInstructionState(R.string.instruction_click_connect);
                    clearUI();
                }
            } else if (RecordingService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                String address = mSensorsMap.get(sensor);
                ArrayList<ArrayList<BluetoothGattCharacteristic>> gattCharas =
                        getGattServices(mRecordingService.getSupportedGattServices(address));
                BluetoothGattCharacteristic mCharacterictic = null;
                if(gattCharas!=null) {
                    for (ArrayList<BluetoothGattCharacteristic> arrayChara : gattCharas) {
                        for (BluetoothGattCharacteristic chara : arrayChara) {
                            if(chara!=null) {
                                if (chara.getUuid().toString().equals(HM_13)) {
                                    mCharacterictic = chara;
                                }
                            }
                        }
                    }
                }
                if (mCharacterictic==null){
                    Toast.makeText(TrackingActivity.this,"No interesting service found",Toast.LENGTH_LONG).show();
                }
                else{
                    if(sensor.equals(CANE)){
                        mServiceCane=true;
                        mNotifyCharacteristicCane=mCharacterictic;
                    }
                    else{
                        mServiceTight = true;
                        mNotifyCharacteristicTight = mCharacterictic;
                    }
                    if (mServiceCane&&mServiceTight){
                        mStartButton.setEnabled(true);
                        updateInstructionState(R.string.instruction_click_service);
                    }
                }
            }
        }
    };

    // Stops recording received data into a file, but keeps connection and notifications open.
    private View.OnClickListener stopClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // close data stream to file
            mRecordingService.stopRecording();

            // stops receiving notifications
            if (mNotifyCharacteristicCane != null) {
                mRecordingService.setCharacteristicNotification(mNotifyCharacteristicCane, false, mSensorsMap.get(CANE));
            }
            if (mNotifyCharacteristicTight != null) {
                mRecordingService.setCharacteristicNotification(mNotifyCharacteristicTight, false, mSensorsMap.get(TIGHT));
            }
            // set UI
            setPostRecordingUI();
            Toast.makeText(TrackingActivity.this,"Data recorded", Toast.LENGTH_LONG).show();
        }
    };

    // Start recording
    private View.OnClickListener startClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // create file for saving data
            if (!mRecordingService.createDataFile()){
                Log.v("FILE", "Not created");
                Toast.makeText(TrackingActivity.this,"File not created",Toast.LENGTH_LONG).show();
            }
            // set UI
            setRecordingUI();

            // set Notification
            mRecordingService.setCharacteristicNotification(
                    mNotifyCharacteristicCane, true, mSensorsMap.get(CANE));
            mRecordingService.setCharacteristicNotification(
                    mNotifyCharacteristicTight, true, mSensorsMap.get(TIGHT));

        }
    };


    private void clearUI() {
        mRecordingService.stopRecording();
        setInitialUI();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tracking);
        mSensorsMap = new HashMap<String,String>();
        FileInputStream input = null;
        File mFile = null;

        // We get the name and address saved in the file after configuration
        try { //for the cane sensor
            mFile = new File(PATH_CONFIG_CANE);
            input = new FileInputStream(mFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(input));
            br.readLine();
            mSensorsMap.put(CANE,br.readLine());
            br.close();
            input.close();
        } catch(Exception e){
            e.printStackTrace();
            //Check if the configuration has been done
            AlertDialog.Builder buildCane = new AlertDialog.Builder(this);
            buildCane.setMessage(R.string.err_config_cane);
            buildCane.setCancelable(true);

            buildCane.setPositiveButton(
                    "Back",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            Intent home = new Intent(TrackingActivity.this, FirstActivity.class);
                            startActivity(home);
                        }
                    });
            AlertDialog alertCane = buildCane.create();
            alertCane.show();
        }
        try { //for the tight sensor
            mFile = new File(PATH_CONFIG_TIGHT);
            input = new FileInputStream(mFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(input));
            br.readLine();
            mSensorsMap.put(TIGHT,br.readLine());
            br.close();
            input.close();
        } catch(Exception e){
            e.printStackTrace();
            //Check if the configuration has been done
            AlertDialog.Builder buildTight = new AlertDialog.Builder(this);
            buildTight.setMessage(R.string.err_config_cane);
            buildTight.setCancelable(true);

            buildTight.setPositiveButton(
                    "Back",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            Intent home = new Intent(TrackingActivity.this, FirstActivity.class);
                            startActivity(home);
                        }
                    });
            AlertDialog alertTight = buildTight.create();
            alertTight.show();
        }

        // Sets up UI references.
        mConnectionStateCane = (TextView) findViewById(R.id.cane_device_state);
        mConnectionStateTight = (TextView) findViewById(R.id.tight_device_state);

        mInstructions = (TextView) findViewById(R.id.instruction);

        mStopButton = (Button) findViewById(R.id.stopButton);
        mStopButton.setOnClickListener(stopClickListener);
        mStartButton = (Button) findViewById(R.id.playButton);
        mStartButton.setOnClickListener(startClickListener);

        getActionBar().setTitle("IMU record");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, RecordingService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mRecordingService != null) {
            final boolean resultCane = mRecordingService.connect(mSensorsMap.get(CANE));
            final boolean resultTight = mRecordingService.connect(mSensorsMap.get(TIGHT));
            Log.d(TAG, "Connect request result with cane sensor =" + resultCane);
            Log.d(TAG, "Connect request result with tight sensor =" + resultTight);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        mRecordingService = null;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnectedCane&&mConnectedTight) {
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
                mRecordingService.connect(mSensorsMap.get(CANE));
                mRecordingService.connect(mSensorsMap.get(TIGHT));
                return true;
            case R.id.menu_disconnect:
                mRecordingService.disconnect();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId, final String sensor) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (sensor.equals(CANE)) {
                    mConnectionStateCane.setText(resourceId);
                }
                else{
                    mConnectionStateTight.setText(resourceId);
                }
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


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> getGattServices(List<BluetoothGattService> gattServices) {
        ArrayList<ArrayList<BluetoothGattCharacteristic>> gattChara = null;
        if (gattServices == null) return null;
        gattChara = new ArrayList<>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
            }
            gattChara.add(charas);
        }
        return gattChara;

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RecordingService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(RecordingService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(RecordingService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }



    // return the path to the file created
    // return null if no file name was provided or if the file cannot be created



    public void setInitialUI(){
        mStartButton.setEnabled(false);
        mStopButton.setEnabled(false);
        mInstructions.setText(R.string.instruction_click_connect);
    }

    public void setRecordingUI(){
        mStartButton.setEnabled(false);
        mStopButton.setEnabled(true);
        updateInstructionState(R.string.recording);
    }
    public void setPostRecordingUI(){
        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);
        updateInstructionState(R.string.instruction_click_service);
    }

    public void warningDeconnection(String sensor) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            VibrationEffect effect = VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE);
            vibrator.vibrate(effect);
        } else vibrator.vibrate(1000);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(TrackingActivity.this);

        // set title
        alertDialogBuilder.setTitle(R.string.disconnected);

        // set dialog message
        if(sensor.equals(CANE)){
            alertDialogBuilder.setMessage(R.string.disconnection_cane);
        }
        else{
            alertDialogBuilder.setMessage(R.string.disconnection_tight);
        }
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
            Intent secondeActivite = new Intent(TrackingActivity.this,
                    FirstActivity.class);
            startActivity(secondeActivite);

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}
