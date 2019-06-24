package com.example.blindhelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;


public class ConfigShowActivity extends Activity {
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
            Toast.makeText(ConfigShowActivity.this, "File not Found", Toast.LENGTH_LONG).show();
        }
        ConfigCane = (Button) findViewById(R.id.ChangeCane);
        ConfigCane.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
// Le premier paramètre est le nom de l'activité actuelle
// Le second est le nom de l'activité de destination
                Intent secondeActivite = new Intent(ConfigShowActivity.this,
                        SearchActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

        ValidCane = (Button) findViewById(R.id.ValidateCane);
        ValidCane.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
// Le premier paramètre est le nom de l'activité actuelle
// Le second est le nom de l'activité de destination
                Intent secondeActivite = new Intent(ConfigShowActivity.this,
                        ConfigActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

    }

}
