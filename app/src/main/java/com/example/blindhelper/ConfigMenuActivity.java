package com.example.blindhelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class ConfigMenuActivity extends Activity {
    private Button Cane = null;
    private Button Tight = null;
    private Button CamConnect = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.config_menu);
        Cane = (Button) findViewById(R.id.cane);
        Tight = (Button) findViewById(R.id.tight);
        CamConnect = (Button) findViewById(R.id.camConnect);

        Cane.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
// Le premier paramètre est le nom de l'activité actuelle
// Le second est le nom de l'activité de destination
                Intent secondeActivite = new Intent(ConfigMenuActivity.this,
                        ConfigCaneActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

        Tight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
// Le premier paramètre est le nom de l'activité actuelle
// Le second est le nom de l'activité de destination
                Intent secondeActivite = new Intent(ConfigMenuActivity.this,
                        ConfigTightActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Si on a appuyé sur le retour arrière
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            Intent secondeActivite = new Intent(ConfigMenuActivity.this,
                    FirstActivity.class);
            startActivity(secondeActivite);

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}
