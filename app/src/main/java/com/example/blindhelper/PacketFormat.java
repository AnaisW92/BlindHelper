package com.example.blindhelper;



/***
 *
 * Enter the parameters of the data packets here
 * So that data can be reconstructed
 *
 */

public class PacketFormat {

    /*************************
     * Values for 10 000 bps
     */

    // Bytes for separating the packets
    public final static int DATA_BYTES = 100;          // for BluetoothLeService class
    public final static int DELIMITER1 = 170;          // for BluetoothLeService class
    public final static int DELIMITER2 = 10;           // for BluetoothLeService class

    // Bytes for reconstructing the data
    public final static int SEQNBR_BYTES = 3;          // for DeviceControlActivity class
    public final static int SAMPLE_BYTES = 12;         // for DeviceControlActivity class
    public final static int SAMPLES_PER_PACKET = 8;    // for DeviceControlActivity class
    public final static int SAMPLE_TIME = 10; // milliseconds


    /*************************
     * Values for 12 800 bps
     */
    public final static int TIME_BYTES = 4;




}
