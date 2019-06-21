package com.example.blindhelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;

import java.io.File;


public class FirstActivity extends Activity {
    private Button Config = null;
    private Button IMU = null;
    private Button Camera = null;
    private Button Files = null;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);
        Config = (Button) findViewById(R.id.Config);
        IMU = (Button) findViewById(R.id.IMU);
        Camera = (Button) findViewById(R.id.Camera);
        Files = (Button) findViewById(R.id.Files);
        File cane_file = new File(getFilesDir(), "R.string.cane_file");
        File tight_file = new File(getFilesDir(), "R.string.tight_file");
        File camera_file = new File(getFilesDir(), "R.string.camera_file");


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
                Intent secondeActivite = new Intent(FirstActivity.this,
                        DeviceControlActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });
    }
}


