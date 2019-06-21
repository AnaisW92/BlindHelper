package com.example.blindhelper;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.widget.Button;

import java.util.Set;

public class IMUActivity extends Activity {

private Button mStartButton ;
private Button mStopButton ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.imu);
    }


}
