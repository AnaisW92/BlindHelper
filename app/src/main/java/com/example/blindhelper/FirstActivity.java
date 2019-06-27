package com.example.blindhelper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.view.View;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;

import static android.content.ContentValues.TAG;


public class FirstActivity extends Activity {
    private Button Config = null;
    private Button IMU = null;
    private Button Camera = null;
    private Button Files = null;
    private File FileCane = null;
    private File FileTight = null;
    private File FileCam = null;
    private static final int REQUEST_WRITE_STORAGE = 3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);
        Config = (Button) findViewById(R.id.Config);
        IMU = (Button) findViewById(R.id.IMU);
        Camera = (Button) findViewById(R.id.Camera);
        Files = (Button) findViewById(R.id.Files);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        }

        String path = null;
        File FileDir = null;

        path = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperConfig/";
        FileDir= new File(path);
        if (!FileDir.exists()) {
            FileDir.mkdir();
        }
        path = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperDataCane/";
        FileDir= new File(path);
        if (!FileDir.exists()) {
            FileDir.mkdir();
        }

        path = Environment.getExternalStorageDirectory().getPath() +  "/Android/data/BlindHelperDataTight/";
        FileDir= new File(path);
        if (!FileDir.exists()) {
            FileDir.mkdir();
        }
        path = Environment.getExternalStorageDirectory().getPath() +  "/Android/data/BlindHelperDataCam/";
        FileDir= new File(path);
        if (!FileDir.exists()) {
            FileDir.mkdir();
        }

        path = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperConfig/" + "CaneSensor.txt";
        FileCane = new File(path);
        path = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperConfig/" + "TightSensor.txt";
        FileTight = new File(path);
        path = Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperConfig/" + "CamSensor.txt";
        FileCam = new File(path);

        try{
        FileCane.createNewFile();
        FileTight.createNewFile();
        FileCam.createNewFile();
        }
        catch(Exception e){
            e.printStackTrace();
            Toast.makeText(FirstActivity.this, "Files not created", Toast.LENGTH_LONG).show();
        }


        Config.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
// Le premier paramètre est le nom de l'activité actuelle
// Le second est le nom de l'activité de destination
                Intent secondeActivite = new Intent(FirstActivity.this,
                        ConfigActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

        IMU.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
// Le premier paramètre est le nom de l'activité actuelle
// Le second est le nom de l'activité de destination
                Intent openIMUActivity= new Intent(FirstActivity.this, DeviceControlActivity.class);
                openIMUActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivityIfNeeded(openIMUActivity, 0);
            }
        });

        Files.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent secondeActivite = new Intent(FirstActivity.this,
                        FilesActivity.class);

                startActivity(secondeActivite);
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
                    builder1.setMessage(R.string.error_permission_denied);

                    AlertDialog alert11 = builder1.create();
                    alert11.show();

                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            finish();
                        }

                    }, 4000); // 5000ms delay


                }
                return;
            }
        }
    }

}


