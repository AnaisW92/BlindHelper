package com.example.blindhelper;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;

import static com.example.blindhelper.PacketFormat.TIME_BYTES;

public class RecordingService extends IntentService {
    private String intentExtra = null;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private final static String TAG = RecordingService.class.getSimpleName();

    private File caneFile = null;
    private FileOutputStream output = null;
    private String mBluetoothDeviceAddress = null;

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

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }
    };

    /**
     * Constructeur
     */
    RecordingService() {
        super("RecordingService");
    }

    /**
     * Methode obligatoire : traitement des intent reçus
     *
     * @param intent
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        intentExtra = intent.getStringExtra("order");
        if (intentExtra.equals("connect")) {
            initialize();
            connect(intent.getStringExtra("address"));

        } else if (intentExtra.equals("disconnect")) {
            disconnect();

        } else if (intentExtra.equals("record")) {
            createDataFile();
        } else if (intentExtra.equals("stop")) {
            stopRecording();
        } else if (intentExtra.equals("close")) {

        }
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

            if (PacketActivity.button_12800.isChecked()) {
                if (data.length == 16) {
                    final byte[] newData = data;
                    if (newData != null) {
                        writeFile12800(data, TIME_BYTES);
                    } else Log.v("FILE", "data NULL");
                }
            }
        }

    }

    private boolean connect(final String address) {
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
    private void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    public void writeFile(StringBuilder stringData) {
        try {
            //Log.v("FILE", stringData.toString());
            if (output != null)
                output.write(stringData.toString().getBytes());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeFile12800(byte[] data, int timeBytes) {
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

    public void stopRecording() {
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

    public String createDataFile() {
        String path = null;
        //String file_name = mFileName.getText().toString();

        String file_name = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        file_name = "cane_" + file_name;

        /*if(TextUtils.isEmpty(file_name)) {
            mFileName.setError("Required");
            return null;
        }*/

        // If there is external and writable storage
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
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
     * @param enabled        If true, enable notification.  False otherwise.
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
    }


}
