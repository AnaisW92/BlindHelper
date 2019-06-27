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
import java.util.List;
import java.util.UUID;

import static com.example.blindhelper.PacketFormat.DATA_BYTES;
import static com.example.blindhelper.PacketFormat.DELIMITER1;
import static com.example.blindhelper.PacketFormat.DELIMITER2;
import static com.example.blindhelper.PacketFormat.SAMPLE_BYTES;
import static com.example.blindhelper.PacketFormat.SAMPLE_TIME;
import static com.example.blindhelper.PacketFormat.SEQNBR_BYTES;
import static com.example.blindhelper.PacketFormat.TIME_BYTES;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    int store = -1;
    int counter = 0;
    int total = 0;
    float losses = 0;

    private byte mStoreBytes[] = new byte[DATA_BYTES - 1]; // we don't store the delimiter
    private int mBytesArrayIndex = 0;
    private final static int MTU_SIZE = 60; // not used
    private int mSequenceNumber = 0;
    private boolean mCheckNext = false;

    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private File caneFile = null;
    private FileOutputStream output=null;


    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HM_13 =
            UUID.fromString(SampleGattAttributes.HM_13);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) { /*******************************/
            super.onMtuChanged(gatt, mtu, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "New mtu size : " + mtu);
            }else Log.w(TAG, "MTU NOT CHANGED");
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    //requestMTU();
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "ON READ");
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        // callback for setCharacteristicNotification
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            //Log.w(TAG, "ON CHANGED");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

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

    // for ACTION_DATA_AVAILABLE (onCharacteristicRead, onCharacteristicChanged)
    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        Log.v("AndroidLE", "broadcastUpdate()");
        final byte[] data = characteristic.getValue();
        Log.v("AndroidLE", "data.Length: " + data.length);


        if (data != null && data.length > 0) {

            if (PacketActivity.button_10000.isChecked()) {
                for (int i = 0; i < data.length; i++) {
                    // check if we reached the delimiter and the data is complete
                    //if ( data[i] == mDELIMITER ) {
                    if (((data[i] & 0xFF) == DELIMITER1) && (i != 0) && ((data[i - 1] & 0x0F) == DELIMITER2)) {
                        //Log.v("DATA", "Delimiter found");
                        if (mBytesArrayIndex == DATA_BYTES - 1) {
                            // broadcast data
                            //mSequenceNumber = ((mStoreBytes[0] & 0xFF) << 16) + ((mStoreBytes[1] & 0xFF) << 8) + (mStoreBytes[2] & 0xFF);
                            //intent.putExtra(EXTRA_DATA, mStoreBytes);
                            //sendBroadcast(intent);
                            final byte[] newData = mStoreBytes;
                            if (newData != null) {
                                //Log.v("FILE", "Received data length : " + data.length);
                                // Convert the received Byte array to a meaningful Integer array, and store it in file.
                                // Format :
                                // ax,ay,az,gx,gy,gz,time
                                writeFile10000(data, SEQNBR_BYTES, SAMPLE_TIME, SAMPLE_BYTES);

                            } else Log.v("FILE", "data NULL");

                            // display data
             /*final StringBuilder stringData = new StringBuilder(mStoreBytes.length + mStoreBytes.length/2); // memory for data and for comas
             int storeData[] = new int[mStoreBytes.length/2]; // to store integers (2 bytes)
             for(int j = 0; j < mStoreBytes.length; j += 2) {
             storeData[j / 2] = (mStoreBytes[j] << 8) + mStoreBytes[j + 1];
             stringData.append(storeData[j / 2]);
             stringData.append(',');
             }Log.v("DATA", stringData.toString());*/

                            Log.v("DATA", "Broadcast byte array");
                            mBytesArrayIndex = 0;

                        } else {
                            // We have reached the delimiter but the data is not complete
                            // It is not the delimiter
                            // Or it is the delimiter and we have lost data -> this case is considered
                            Log.v("DATA", "Ambiguous : delimiter found but data not complete");
                            Log.v("DATA", "Action : abandoning the data");
                            mBytesArrayIndex = 0;
                        }
                    } else {
                        // store data
                        if (mBytesArrayIndex <= DATA_BYTES - 2) { // index starts from 0 and we don't store the delimiter
                            mStoreBytes[mBytesArrayIndex] = data[i];
                            mBytesArrayIndex++;

                            //Log.v("DATA", "OK");
                        } else {
                            Log.v("DATA", "Ambiguous : data complete but delimiter not found");
                            Log.v("DATA", "Action : abandoning the data");
                            mBytesArrayIndex = 0;
                        }
                    }
                }
            } else if (PacketActivity.button_12800.isChecked()) {
                if (data.length == 16) {
                    //intent.putExtra(EXTRA_DATA, data);
                    //sendBroadcast(intent);
                    final byte[] newData = data;
                    if (newData != null) {
                        //Log.v("FILE", "Received data length : " + data.length);
                        // Convert the received Byte array to a meaningful Integer array, and store it in file.
                        // Format :
                        // ax,ay,az,gx,gy,gz,time
                        writeFile12800(data, TIME_BYTES);
                    } else Log.v("FILE", "data NULL");
                }
            }
        }

    }


    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
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
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
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

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
        Log.w(TAG, "READ");
    }

    void showToast() {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BluetoothLeService.this, "Toast Message", Toast.LENGTH_SHORT).show();
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
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        Log.w(TAG, "NOTIF ENABLED : " + String.valueOf(enabled));
        Log.v(TAG, characteristic.getUuid().toString()); //beb...



        // This is specific to the remote device you are using.
        if (UUID_HM_13.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
            Log.w(TAG, "DESCRIPTOR OK");
        }

/***********************************************************************************************************/

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

    public String createDataFile(){
        String path = null;
        //String file_name = mFileName.getText().toString();

        String file_name = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        file_name = "cane_" + file_name;

        /*if(TextUtils.isEmpty(file_name)) {
            mFileName.setError("Required");
            return null;
        }*/

        // If there is external and writable storage
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
            try {
                path = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperDataCane/" + file_name + ".txt";
                caneFile = new File(path);

                caneFile.createNewFile(); // create new file if it does not already exists

                output = new FileOutputStream(caneFile);
                Log.v("FILE", "File created");
                //path = caneFile.getPath();
                //DeviceControlActivity.mDataField.setText("Saving to : " + path);


            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            //Toast.makeText(BluetoothLeService.this, R.string.error_no_ext_storage, Toast.LENGTH_LONG).show();
        }
        return path;
    }



    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
