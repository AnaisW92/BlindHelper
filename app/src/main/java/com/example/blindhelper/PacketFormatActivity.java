package com.example.blindhelper;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.Nullable;

import static com.example.blindhelper.PacketFormat.DATA_BYTES;
import static com.example.blindhelper.PacketFormat.DELIMITER1;
import static com.example.blindhelper.PacketFormat.DELIMITER2;
import static com.example.blindhelper.PacketFormat.SAMPLE_BYTES;
import static com.example.blindhelper.PacketFormat.SAMPLE_TIME;
import static com.example.blindhelper.PacketFormat.SEQNBR_BYTES;
import static com.example.blindhelper.PacketFormat.TIME_BYTES;
import static com.example.blindhelper.PacketFormat.DATA_BYTES;
import static com.example.blindhelper.PacketFormat.DELIMITER1;

public class PacketFormatActivity extends Activity {
    TextView packet_information;
    public static RadioButton button_10000;
    public static RadioButton button_12800;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.packet_information);
        /*

        button_10000 = (RadioButton) findViewById(R.id.button_10000);
        button_12800 = (RadioButton) findViewById(R.id.button_12800);

        if (button_10000.isChecked()) {
            String values = ("DATA_BYTES : " + DATA_BYTES + "\r\n"
                    + "DELIMITER1 : " + DELIMITER1 + "\r\n"
                    + "DELIMITER2 : " + DELIMITER2 + "\r\n"
                    + "SEQNBR_BYTES : " + SEQNBR_BYTES + "\r\n"
                    + "SAMPLE_BYTES : " + SAMPLE_BYTES + "\r\n"
                    + "SAMPLE_TIME : " + SAMPLE_TIME + "\r\n");
            // to continue
            packet_information = (TextView) findViewById(R.id.packet_format_values);
            packet_information.setText(values);
        } else if (button_12800.isChecked()) {
            String values = ("TIME_BYTES : " + TIME_BYTES);
            packet_information = (TextView) findViewById(R.id.packet_format_values);
            packet_information.setText(values);
        }

        button_12800.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String values = ("TIME_BYTES : " + TIME_BYTES);
                packet_information = (TextView) findViewById(R.id.packet_format_values);
                packet_information.setText(values);
            }
        });

        button_10000.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String values = ("DATA_BYTES : " + DATA_BYTES + "\r\n"
                        + "DELIMITER1 : " + DELIMITER1 + "\r\n"
                        + "DELIMITER2 : " + DELIMITER2 + "\r\n"
                        + "SEQNBR_BYTES : " + SEQNBR_BYTES + "\r\n"
                        + "SAMPLE_BYTES : " + SAMPLE_BYTES + "\r\n"
                        + "SAMPLE_TIME : " + SAMPLE_TIME + "\r\n");
                // to continue
                packet_information = (TextView) findViewById(R.id.packet_format_values);
                packet_information.setText(values);
            }
        });*/

    }
}
