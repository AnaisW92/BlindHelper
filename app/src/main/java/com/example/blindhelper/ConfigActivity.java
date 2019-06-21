package com.example.blindhelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class ConfigActivity extends Activity {
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
                Intent secondeActivite = new Intent(ConfigActivity.this,
                        SearchActivity.class);

// Puis on lance l'intent !
                startActivity(secondeActivite);
            }
        });

    }
}
