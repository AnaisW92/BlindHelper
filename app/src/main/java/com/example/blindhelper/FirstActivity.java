package com.example.blindhelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;


public class FirstActivity extends Activity {
    private Button Config = null;
    private Button IMU = null;
    private Button Camera = null;
    private Button Files = null;
    private File FileDir = null;
    private File FileCane = null;
    private File FileTight = null;
    private File FileCam = null;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);
        Config = (Button) findViewById(R.id.Config);
        IMU = (Button) findViewById(R.id.IMU);
        Camera = (Button) findViewById(R.id.Camera);
        Files = (Button) findViewById(R.id.Files);

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
// Le premier paramÃ¨tre est le nom de l'activitÃ© actuelle
// Le second est le nom de l'activitÃ© de destination
                Intent secondeActivite = new Intent(FirstActivity.this,
                        ConfigActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

        IMU.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
// Le premier paramÃ¨tre est le nom de l'activitÃ© actuelle
// Le second est le nom de l'activitÃ© de destination
                Intent secondeActivite = new Intent(FirstActivity.this,
                        DeviceControlActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
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

}


