package com.example.blindhelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;


public class ConfigShowActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cane_conf);

        try {

            FileInputStream input = null;
            input = openFileInput("R.string.cane_file");

            BufferedReader br = new BufferedReader(new InputStreamReader(input));
            TextView text = (TextView) findViewById(R.id.NameCane) ;
            text.setText(br.readLine());
            text = (TextView) findViewById(R.id.AddressCane) ;
            text.setText(br.readLine());
            br.close();
            input.close();
        } catch(Exception e){
            e.printStackTrace();
        }

    }

}
