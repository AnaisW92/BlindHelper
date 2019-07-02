package com.example.blindhelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.widget.Button;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;


public class ConfigCaneActivity extends Activity {
    private Button ConfigCane = null;
    private Button ValidCane = null;
    String path = null;
    File mFile = null;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cane_conf);


        try {

            FileInputStream input = null;

            mFile = new File(Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperConfig/" + "CaneSensor.txt");

            input = new FileInputStream(mFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(input));
            TextView text = (TextView) findViewById(R.id.NameCane) ;
            br.mark(100);

            if (br.read()==-1){
                text.setText("Not configured");
                text = (TextView) findViewById(R.id.AddressCane);
                text.setText("Not configured");
            }
            else {
                br.reset();
                text.setText(br.readLine());
                text = (TextView) findViewById(R.id.AddressCane);
                text.setText(br.readLine());
                br.reset();
                br.close();
                input.close();
            }
        } catch(Exception e){
            //e.printStackTrace();
            Toast.makeText(ConfigCaneActivity.this, "File not Found", Toast.LENGTH_LONG).show();
        }
        ConfigCane = (Button) findViewById(R.id.ChangeCane);
        ConfigCane.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
// Le premier paramÃ¨tre est le nom de l'activitÃ© actuelle
// Le second est le nom de l'activitÃ© de destination
                Intent secondeActivite = new Intent(ConfigCaneActivity.this,
                        SearchActivity.class);
                secondeActivite.putExtra("com.example.blindhelper.TYPE","CANE");

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Si on a appuyé sur le retour arrière
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            Intent secondeActivite = new Intent(ConfigCaneActivity.this,
                    ConfigMenuActivity.class);
            startActivity(secondeActivite);

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}