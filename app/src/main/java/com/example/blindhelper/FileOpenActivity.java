package com.example.blindhelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.app.ListActivity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class FileOpenActivity extends Activity {
    public static final String pathFile = null;
    private String path = null;
    private File mFile = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_open);
        TextView txtFileName=(TextView)findViewById(R.id.zoneText);
        txtFileName.setMovementMethod(new ScrollingMovementMethod());
        AssetManager assetManager=getAssets();
        FileInputStream input= null;
        final Intent intent = getIntent();
        path = intent.getStringExtra(pathFile);

        try {
            mFile = new File(path);
            input = new FileInputStream(mFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(input));
            int size=input.available();
            byte[] buffer=new byte[size];
            input.read(buffer);
            input.close();
            String text=new String(buffer);
            txtFileName.setText(text);

        } catch (Exception e){
            Toast.makeText(FileOpenActivity.this, "File can't be read", Toast.LENGTH_LONG).show();
        }
    }
}
