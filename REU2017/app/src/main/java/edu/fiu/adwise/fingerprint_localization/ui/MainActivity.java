package edu.fiu.adwise.fingerprint_localization.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

import edu.fiu.adwise.fingerprint_localization.localization.ClientThread;
import edu.fiu.adwise.fingerprint_localization.localization.KeyMaster;
import edu.fiu.adwise.homomorphic_encryption.misc.HomomorphicException;
import edu.fiu.adwise.fingerprint_localization.sensors.WifiReceiver;

import static edu.fiu.reu2017.R.*;


public class MainActivity extends AppCompatActivity {
    public final static String SQLDatabase = "160.39.57.71";
    public final static int portNumber = 9254;
    public final static int TIMEOUT = 2 * 1000;
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 1001;
    public static int VECTOR_SIZE = -1;
    //public final static int KEY_SIZE = 1024;
    public final static long BILLION = 1000000000; // 10^9
    public final static long FACTOR = 10; // Applies for DMA/MCA ONLY!
    public final static int k = 2; //Number of Minimum distances used!
    protected WifiReceiver mWifiManager;
    protected Button StartScan;
    protected Button TrainData;
    protected Button Update;
    protected Button Localize;
    protected Button UNDO;
    protected Button RESET;
    private NumberPicker LocalizationSelect;
    protected TextView output;
    protected int LOCALIZATION_SCHEME = -1;
    protected String[] options = new String[] {
            "1: PlainText/Min.",
            "2: DGK/Min.",
            "3: Paillier/Min.",
            "4: PlainText/MCA",
            "5: DGK/MCA",
            "6: Paillier/MCA",
            "7: PlainText/DMA",
            "8: DGK/DMA",
            "9: Paillier/DMA",
            "10: ElGamal/Min.",
            "11: ElGamal/MCA",
            "12: ElGamal/DMA"
    };

    // Print Process DB Results
    public static Toast process_good;
    public static Toast process_bad;
    public static Toast Wifi_needed;
    public static Toast good_train;
    public static Toast bad_train;
    public static HashMap<String, String> AP_map = new HashMap<>();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        if (!KeyMaster.finished) {
            try {
                KeyMaster.init();
            } catch (HomomorphicException e) {
                throw new RuntimeException(e);
            }
        }
        KeyMaster.last_device = getDeviceName();

        good_train = Toast.makeText(getApplicationContext(), "Training Successful!", Toast.LENGTH_SHORT);
        bad_train = Toast.makeText(getApplicationContext(), "Training NOT Successful!", Toast.LENGTH_SHORT);
        process_good = Toast.makeText(getApplicationContext(), "Process DB successful!", Toast.LENGTH_SHORT);
        process_bad = Toast.makeText(getApplicationContext(), "Process DB failed!", Toast.LENGTH_SHORT);
        Wifi_needed = Toast.makeText(getApplicationContext(), "Are you connected to Wi-Fi?", Toast.LENGTH_LONG);

        // Connect Variables to GUI
        StartScan = findViewById(id.scan);
        Localize = findViewById(id.Localize);
        Update = findViewById(id.Process);
        RESET = findViewById(id.RESET);
        UNDO = findViewById(id.UNDO);
        TrainData = findViewById(id.train);
        output = findViewById(id.output);
        LocalizationSelect = findViewById(id.numberPicker);

        /*
         * This is only for scanning Wifi!
         * Use this if you need functioning code for Android to
         * scan APs and Wifi Signal Strength
         */
        mWifiManager = new WifiReceiver(this, null);
        LocalizationSelect.setMinValue(1);
        LocalizationSelect.setMaxValue(options.length);
        LocalizationSelect.setDisplayedValues(options);

        /*
         * This is critical! You should only need this once!
         * But you need to give the app runtime application permission!
         * Otherwise this will NOT work for Android 6.0 and up.
         */
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }

        StartScan.setOnClickListener(new start_scan());

        /*
         * Use this Button to go to Training Option
         * Open the map and Train data points!
         */
        TrainData.setOnClickListener(new train_data());

        /*
        Input: Command to force Database to recompute Lookup Table
        Return: NOTHING. Kill Thread after
        */
        Update.setOnClickListener(new process_db());

        /*
        Client choose to use the REU 2015 method
        This method is having the phone
        decrypt and sort the distances
         */
        Localize.setOnClickListener(new localize());

        RESET.setOnClickListener(new reset());

        UNDO.setOnClickListener(new undo());

        // Get Mapping
        // this.MAC_to_AP_Name();

        String msg = getString(string.key_gen) + KeyMaster.duration/BILLION + " " + getString(string.seconds);
        //msg += " " + KEY_SIZE;
        msg += " K =" + k;
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        output.setText(msg);
    }

    private static class process_db implements View.OnClickListener {
        public void onClick(View v) {
            test_connection t = new test_connection();
            Thread th = new Thread(t);
            th.start();
            try {
                th.join();
            }
            catch (InterruptedException e) {
                Log.e(this.getClass().getName(), "Thread Interrupted", e);
            }
            if(t.connected) {
                new Thread(new ClientThread()).start();
            }
            else {
                Wifi_needed.show();
            }
        }
    }

    private class train_data implements View.OnClickListener {
        public void onClick(View v) {
            test_connection t = new test_connection();
            Thread th = new Thread(t);
            th.start();
            try {
                th.join();
            }
            catch (InterruptedException e) {
                Log.e(this.getClass().getName(), "Thread Interrupted", e);
            }
            if(t.connected) {
                if(KeyMaster.map_name.isEmpty() || KeyMaster.map == null) {
                    Toast.makeText(getApplicationContext(), "PICK A MAP!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Loading Map: " + KeyMaster.map_name, Toast.LENGTH_LONG).show();
                    Intent train = new Intent(MainActivity.this, TrainActivity.class);
                    startActivity(train);
                }
            }
        }
    }

    private class localize implements View.OnClickListener {
        public void onClick(View v) {
            test_connection t = new test_connection();
            Thread th = new Thread(t);
            th.start();
            try {
                th.join();
            }
            catch (InterruptedException e) {
                Log.e(this.getClass().getName(), "Thread Interrupted", e);
            }
            if(t.connected) {
                LOCALIZATION_SCHEME = LocalizationSelect.getValue();
                if(KeyMaster.map == null || KeyMaster.map_name.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "PICK A MAP!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Loading Map: " + KeyMaster.map_name, Toast.LENGTH_LONG).show();
                    Intent Localize = new Intent(MainActivity.this, LocalizeActivity.class);
                    Localize.putExtra("localization", LOCALIZATION_SCHEME);
                    startActivity(Localize);
                }
            }
        }
    }

    private class reset implements View.OnClickListener, Runnable {
        public void onClick(View v) {
            new Thread(this).start();
        }

        public void run() {
            try (Socket ClientSocket = new Socket()) {
                ClientSocket.connect(new InetSocketAddress(SQLDatabase, portNumber), TIMEOUT);
                try (
                        ObjectOutputStream toServer = new ObjectOutputStream(ClientSocket.getOutputStream());
                        ObjectInputStream fromServer = new ObjectInputStream(ClientSocket.getInputStream());
                ) {
                    toServer.writeObject("RESET");
                    toServer.flush();
                    if (fromServer.readBoolean()) {
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "RESET COMPLETE!", Toast.LENGTH_LONG).show());
                    } else {
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "RESET FAILED!", Toast.LENGTH_LONG).show());
                    }
                } catch (IOException e) {
                    Wifi_needed.show();
                }
            } catch (IOException e) {
                Wifi_needed.show();
            }
        }
    }

    private class undo implements View.OnClickListener, Runnable {
        public void onClick(View v) {
            new Thread(this).start();
        }

        public void run() {
            try (Socket ClientSocket = new Socket()) {
                ClientSocket.connect(new InetSocketAddress(SQLDatabase, portNumber), TIMEOUT);

                try (
                        ObjectOutputStream toServer = new ObjectOutputStream(ClientSocket.getOutputStream());
                        ObjectInputStream fromServer = new ObjectInputStream(ClientSocket.getInputStream());
                ) {
                    toServer.writeObject("UNDO");
                    // Need to send Coordinates, Phone and DeviceName
                    toServer.writeObject(KeyMaster.last_coordinates);
                    toServer.writeObject(KeyMaster.map_name);
                    toServer.writeObject(KeyMaster.last_device);
                    toServer.flush();

                    if (fromServer.readBoolean()) {
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(),"UNDO COMPLETE!", Toast.LENGTH_LONG).show());
                    }
                    else {
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(),"UNDO FAILED!", Toast.LENGTH_LONG).show());
                    }
                } catch (IOException e) {
                    Wifi_needed.show();
                }
            } catch (IOException e) {
                Wifi_needed.show();
            }
        }
    }

    private class start_scan implements View.OnClickListener {
        public void onClick(View v) {
            Intent map = new Intent(MainActivity.this, AddMapActivity.class);
            startActivity(map);
        }
    }

    public void onResume() {
        super.onResume();
        mWifiManager.registerReceiver(this);
    }

    public void onPause() {
        super.onPause();
        mWifiManager.unregisterReceiver(this);
    }

    public static String [] getPhoneData() {
        String [] phones = new String[4];
        phones[0] = System.getProperty("os.version");  // OS version
        phones[1] = android.os.Build.DEVICE;           // Device
        phones[2] = getDeviceName();                   // Manufacturer/Model
        phones[3] = android.os.Build.PRODUCT;          // Product
        return phones;
    }

    // source: https://stackoverflow.com/questions/1995439/get-android-phone-model-programmatically
    // Better way to get model
    protected static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return capitalize(model);
        }
        else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        }
        else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }
}