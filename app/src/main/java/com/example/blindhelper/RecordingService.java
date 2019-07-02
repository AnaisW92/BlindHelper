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

import android.app.AlertDialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.example.blindhelper.TrackingActivity.CANE;
import static com.example.blindhelper.TrackingActivity.TIGHT;


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class RecordingService extends Service {
    public final static int TIME_BYTES = 4;

    private final static String TAG = RecordingService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private File caneFile = null;
    private File tightFile = null;
    private FileOutputStream output_cane=null;
    private FileOutputStream output_tight=null;

    private Map<String, BluetoothGatt> connectedDeviceMap;
    private Map<String, String> mSensorsMap;

    private String PATH_FILE_CANE = null;
    private String PATH_FILE_TIGHT = null;


    public final static String ACTION_GATT_CONNECTED =
            "ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED=
            "ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "EXTRA_DATA";

    public final static UUID UUID_HM_13 =
            UUID.fromString(SampleGattAttributes.HM_13);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction=null;
            String sensor = null;
            BluetoothDevice device = gatt.getDevice();
            String address = device.getAddress();
            if(mSensorsMap.containsValue(address)){
                if (mSensorsMap.get(CANE).equals(address)){
                    sensor = CANE;
                }
                else{
                    sensor=TIGHT;
                }
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                if (!connectedDeviceMap.containsKey(address)) {
                    connectedDeviceMap.put(address, gatt);
                }
                broadcastUpdate(intentAction,sensor);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        gatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                if (connectedDeviceMap.containsKey(address)) {
                    BluetoothGatt bluetoothGatt = connectedDeviceMap.get(address);
                    if (bluetoothGatt != null) {
                        bluetoothGatt.close();
                    }
                    connectedDeviceMap.remove(address);
                    broadcastUpdate(intentAction,sensor);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothDevice device = gatt.getDevice();
                String address = device.getAddress();
                String sensor=null;
                if(mSensorsMap.containsValue(address)){
                    if (mSensorsMap.get(CANE).equals(address)){
                        sensor = CANE;
                    }
                    else{
                        sensor=TIGHT;
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED,sensor);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }


        @Override
        // callback for setCharacteristicNotification
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            //Log.w(TAG, "ON CHANGED");
            BluetoothDevice device = gatt.getDevice();
            String address = device.getAddress();
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, address);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
//
        return START_STICKY;
    }

    // for ACTION_GATT_SERVICE_DISCOVERED, ACTION_GATT_CONNECTED, ACTION_GATT_DISCONNECTED
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    // for ACTION_GATT_SERVICE_DISCOVERED
    private void broadcastUpdate(final String action,final String sensor) {
        if (sensor==null){
            broadcastUpdate(action);
        }
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA,sensor);
        sendBroadcast(intent);
    }

    // for ACTION_DATA_AVAILABLE (onCharacteristicChanged)
    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic,String address) {
        final Intent intent = new Intent(action);
        Log.v("AndroidLE", "broadcastUpdate()");
        final byte[] data = characteristic.getValue();
        Log.v("AndroidLE", "data.Length: " + data.length);

        if (data != null && data.length > 0) {
            if (data.length == 16) {
                if(mSensorsMap.get(CANE).equals(address)){
                    writeFile(data, TIME_BYTES,CANE);
                }
                else{
                    writeFile(data, TIME_BYTES, TIGHT);
                }
            } else Log.v("FILE", "data NULL");

        }
    }

    public class LocalBinder extends Binder {
        RecordingService getService() {
            return RecordingService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize(Map<String,String> sensors) {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            connectedDeviceMap = new HashMap<String, BluetoothGatt>();
            mSensorsMap = sensors;
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        //Device already known or not ?
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        int connectionState = mBluetoothManager.getConnectionState(device, BluetoothProfile.GATT);
        if(connectionState == BluetoothProfile.STATE_DISCONNECTED ){
            device.connectGatt(this, false, mGattCallback);
            Log.d(TAG, "Trying to create a new connection.");
        }
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || connectedDeviceMap.isEmpty()) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        connectedDeviceMap.get(mSensorsMap.get(CANE)).disconnect();
        connectedDeviceMap.get(mSensorsMap.get(TIGHT)).disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (connectedDeviceMap.isEmpty()) {
            return;
        }
        String address = null;
        address = mSensorsMap.get(CANE);
        if(connectedDeviceMap.containsKey(address)){
            connectedDeviceMap.get(address).close();
            connectedDeviceMap.remove(address);
        }
        address = mSensorsMap.get(TIGHT);
        if(connectedDeviceMap.containsKey(address)){
            connectedDeviceMap.get(address).close();
            connectedDeviceMap.remove(address);
        }
    }



    void showToast(final int message) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(RecordingService.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }


    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled, String address) {
        BluetoothGatt gatt = connectedDeviceMap.get(address);
        if (mBluetoothAdapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        gatt.setCharacteristicNotification(characteristic, enabled);
        Log.w(TAG, "NOTIF ENABLED : " + String.valueOf(enabled));
        Log.v(TAG, characteristic.getUuid().toString()); //beb...


        // This is specific to the remote device you are using.
        if (UUID_HM_13.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            Log.w(TAG, "DESCRIPTOR OK");
        }

/***********************************************************************************************************/

    }


    public void writeFile(byte[] data, int timeBytes,String sensors){
        if (data.length == 16) {
            final StringBuilder stringData = new StringBuilder(data.length + (data.length) / 2); // memory for data, for comas, for time
            int storeData[] = new int[(data.length) / 2]; // to store integers (2 bytes)
            long seqNbr = (((data[data.length - 1] & 0xFFL)) << 24) + ((data[data.length - 2] & 0xFFL) << 16) + ((data[data.length - 3] & 0xFFL) << 8) + (data[data.length - 4] & 0xFFL);

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

            if (sensors.equals(CANE)) {
                if (output_cane != null) {
                    try {
                        output_cane.write(stringData.toString().getBytes());

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            else{
                if(output_tight !=null) {
                    try {
                        output_tight.write(stringData.toString().getBytes());

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void stopRecording(){
        try {
            if (output_cane != null)
                output_cane.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (output_tight != null)
                output_tight.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        output_cane = null;
        output_tight = null;
    }

    public boolean createDataFile(){
        String path_cane = null;
        String path_tight = null;

        String file_name_cane = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        file_name_cane = "cane_" + file_name_cane;
        String file_name_tight = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        file_name_tight = "tight_" + file_name_tight;

        // If there is external and writable storage
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
            try {
                path_cane = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperDataCane/" + file_name_cane+".txt";
                path_tight = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperDataTight/" + file_name_tight+".txt"; ;
                caneFile = new File(path_cane);
                caneFile.createNewFile(); // create new file if it does not already exists
                tightFile = new File(path_tight);
                tightFile.createNewFile(); // create new file if it does not already exists

                output_cane = new FileOutputStream(caneFile);
                output_tight = new FileOutputStream(tightFile);
                Log.v("FILE", "File created");

            } catch (IOException e) {
                return false;
            }
        } else {
            showToast(R.string.error_no_ext_storage);
            return false;
        }
        return true;
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices(String address) {
        BluetoothGatt gatt = connectedDeviceMap.get(address);
        if (gatt == null) return null;

        return gatt.getServices();
    }
}
