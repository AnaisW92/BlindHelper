package com.example.blindhelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;


public class TightConfigShowActivity extends Activity {
    private Button ConfigTight = null;
    private Button ValidTight = null;
    String path = null;
    File mFile = null;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tight_conf);


        try {

            FileInputStream input = null;

            mFile = new File(Environment.getExternalStorageDirectory().getPath() + "/Android/data/BlindHelperConfig/" + "TightSensor.txt");

            input = new FileInputStream(mFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(input));
            TextView text = (TextView) findViewById(R.id.NameTight) ;
            br.mark(100);

            if (br.read()==-1){
                text.setText("Not configured");
                text = (TextView) findViewById(R.id.AddressTight);
                text.setText("Not configured");
            }
            else {
                br.reset();
                text.setText(br.readLine());
                text = (TextView) findViewById(R.id.AddressTight);
                text.setText(br.readLine());
                br.reset();
                br.close();
                input.close();
            }
        } catch(Exception e){
            //e.printStackTrace();
            Toast.makeText(TightConfigShowActivity.this, "File not Found", Toast.LENGTH_LONG).show();
        }
        ConfigTight = (Button) findViewById(R.id.ChangeTight);
        ConfigTight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent secondeActivite = new Intent(TightConfigShowActivity.this,
                        SearchActivity.class);
                secondeActivite.putExtra("com.example.blindhelper.TYPE","TIGHT");
// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

        ValidTight = (Button) findViewById(R.id.ValidateTight);
        ValidTight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent secondeActivite = new Intent(TightConfigShowActivity.this,
                        ConfigActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

    }

}
