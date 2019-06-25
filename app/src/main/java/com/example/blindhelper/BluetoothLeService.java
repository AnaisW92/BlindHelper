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
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import static com.example.blindhelper.PacketFormat.DATA_BYTES;
import static com.example.blindhelper.PacketFormat.DELIMITER1;
import static com.example.blindhelper.PacketFormat.DELIMITER2;

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

            if(PacketActivity.button_10000.isChecked()){
                for (int i = 0; i < data.length; i++) {
                    // check if we reached the delimiter and the data is complete
                    //if ( data[i] == mDELIMITER ) {
                    if ( ((data[i] & 0xFF) == DELIMITER1) && (i!=0) && ( (data[i-1] & 0x0F) == DELIMITER2 ) ) {
                        //Log.v("DATA", "Delimiter found");
                        if (mBytesArrayIndex == DATA_BYTES - 1) {
                            // broadcast data
                            //mSequenceNumber = ((mStoreBytes[0] & 0xFF) << 16) + ((mStoreBytes[1] & 0xFF) << 8) + (mStoreBytes[2] & 0xFF);
                            intent.putExtra(EXTRA_DATA, mStoreBytes);
                            sendBroadcast(intent);

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
            }


            else if (PacketActivity.button_12800.isChecked()) {
                if (data.length == 16) {
                    intent.putExtra(EXTRA_DATA, data);
                    sendBroadcast(intent);
                }
            }


            // Test for first byte
            /*
            if(store != -1){
                if ((data[0] != store + 1) && (store != 127)) {
                    counter++;
                    Log.v(TAG, "erreurs : " + counter);
                }
            }
            store = data[0];

            total ++;
            losses = ((float) counter/ (float) total) * (float) 100;
            Log.v(TAG, String.valueOf(losses));*/





            // To store the incoming bytes in an array of integers and in a string
             /*       final StringBuilder stringData = new StringBuilder(data.length + data.length/2); // memory for data and for comas
                    int storeData[] = new int[ (mDATA_BYTES -1)/2 ]; // to store integers (2 bytes)
            for(int i = 0; i < data.length; i += 2) {
                storeData[i / 2] = (data[i] << 8) + data[i + 1];
                stringData.append(storeData[i / 2]);
                stringData.append(',');
                //if ((i != 0) && (storeData[i / 2] != storeData[i/2 - 1] + 1))
                 //   Log.v("AndroidLE", "ERRRRRRRRRRRREEEEEEEEEEEEEEEEEEEEEEEEEEUUUUUUUUUUUUUUURRRRRRRRRRRRRR");
            }
            Log.v("AndroidLE", stringData.toString());*/

            // Test for 6 integers
           /*int nbr1 = (data[0] << 8) + data[1];
           if (data.length > 12) {
               int nbr2 = (data[12] << 8) + data[13];
               if (nbr1 != (nbr2 -1)){
                   Log.v("AndroidLE", "ERROR !!!");
               }
           } else Log.v("AndroidLE", "DATA LENGTH <= 12 !!!");*/

            // Test for bytes
            /*final StringBuilder stringData = new StringBuilder(data.length * 2);
            for (int i = 0; i < data.length - 1; i++) {
                stringData.append(data[i]);
                stringData.append(',');
                if ((data[i] != data[i + 1] - 1) && (data[i] != 127)) {
                    Log.v("AndroidLE", "ERROR !!!");
                    //Log.v("AndroidLE", stringData.toString());
                }
            }Log.v("AndroidLE", stringData.toString());

            intent.putExtra(EXTRA_DATA, stringData + "\n");*/

            /*final StringBuilder stringBuilder = new StringBuilder(data.length);
            // to ameliorate (bad for memory to allocate a new space each time)
            for (byte byteChar : data) {
                stringBuilder.append(String.format("%02X ", byteChar));
                //Log.v("AndroidLE", String.format("%02X ", byteChar));
            }
            intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());*/

            //intent.putExtra(EXTRA_DATA, data);



            //sendBroadcast(intent);
        }

        //sendBroadcast(intent);
        /***********************************************************************/
    }

    public void requestMTU() {
        //gatt is a BluetoothGatt instance and MAX_MTU is 512
        if (Build.VERSION.SDK_INT >= 21) {
            mBluetoothGatt.requestMtu(MTU_SIZE);
            Log.w(TAG, "MTU REQUESTED");
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
