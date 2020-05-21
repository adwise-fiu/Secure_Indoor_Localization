package ui;

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
import java.net.Socket;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.HashMap;

import Localization.ClientThread;
import Localization.LOCALIZATION_SCHEME;
import security.DGK.DGKKeyPairGenerator;
import security.DGK.DGKPrivateKey;
import security.DGK.DGKPublicKey;
import security.paillier.PaillierKeyPairGenerator;
import security.paillier.PaillierPrivateKey;
import security.paillier.PaillierPublicKey;
import sensors.WifiReceiver;

//import security.elgamal.ElGamalKeyPairGenerator;
import security.elgamal.ElGamalPrivateKey;
import security.elgamal.ElGamalPublicKey;

import static Localization.LOCALIZATION_SCHEME.EL_GAMAL_DMA;
import static Localization.LOCALIZATION_SCHEME.EL_GAMAL_MCA;
import static Localization.LOCALIZATION_SCHEME.EL_GAMAL_MIN;
import static edu.fiu.reu2017.R.*;


public class MainActivity extends AppCompatActivity implements Runnable
{
    public final static String SQLDatabase = "192.168.1.208";
    public final static int portNumber = 9254;
    public final static boolean multi_phone = true;
    private final static String TAG = "MAIN_ACTIVITY";
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 1001;
    public static int VECTOR_SIZE = -1;

    public final static int KEY_SIZE = 2048;
    //public final static int KEY_SIZE = 1024;
    public final static long BILLION = 1000000000; // 10^9
    public final static long FACTOR = 10; // Applies for DMA/MCA ONLY!
    public final static int k = 2;//Number of Minimum distances used!
    private long startTime, endTime;

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

    // Key Master
    public static DGKPrivateKey DGKsk;
    public static DGKPublicKey DGKpk;
    public static PaillierPublicKey pk;
    public static PaillierPrivateKey sk;
    public static ElGamalPublicKey e_pk = null;
    public static ElGamalPrivateKey e_sk = null;

    // IP Communication
    private Socket ClientSocket;
    private ObjectInputStream fromServer;
    private ObjectOutputStream toServer;

    // Print Process DB Results
    public static Toast process_good;
    public static Toast process_bad;
    public static Toast Wifi_needed;
    public static Toast good_train;
    public static Toast bad_train;
    public static HashMap<String, String> AP_map = new HashMap<>();

    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        Thread keyGen;
        (keyGen = new Thread(this)).start();

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
        {
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
        //this.MAC_to_AP_Name();

        // Build El Gamal Keys
        // this.El_Gamal();

        try
        {
            keyGen.join();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        String msg = getString(string.key_gen) + (endTime - startTime)/BILLION + " " + getString(string.seconds);
        //msg += " " + KEY_SIZE;
        msg += " K =" + k;
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        output.setText(msg);
    }

    private class process_db implements View.OnClickListener, Runnable
    {
        boolean connected = false;
        public void onClick(View v)
        {
            Thread t = new Thread(this);
            t.start();
            try
            {
                t.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            if(connected)
            {
                new Thread(new ClientThread()).start();
            }
        }

        public void run()
        {
            try
            {
                ClientSocket = new Socket(SQLDatabase, portNumber);
                toServer = new ObjectOutputStream(ClientSocket.getOutputStream());
                toServer.writeObject("Hello");
                toServer.flush();
                connected = true;
            }
            catch (IOException e)
            {
                Wifi_needed.show();
                connected = false;
            }
        }
    }

    private class train_data implements View.OnClickListener, Runnable
    {
        boolean connected = false;
        public void onClick(View v)
        {
            Thread conn = new Thread(this);
            conn.start();
            try
            {
                conn.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            if(connected)
            {
                Intent train = new Intent(MainActivity.this, TrainActivity.class);
                //train.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(train);
            }
        }

        public void run()
        {
            try
            {
                ClientSocket = new Socket(SQLDatabase, portNumber);
                toServer = new ObjectOutputStream(ClientSocket.getOutputStream());
                toServer.writeObject("Hello");
                toServer.flush();
                connected = true;
            }
            catch (IOException e)
            {
                Wifi_needed.show();
                connected = false;
            }
        }
    }

    private class localize implements View.OnClickListener, Runnable
    {
        boolean connected = false;
        public void onClick(View v)
        {
            Thread conn = new Thread(this);
            conn.start();
            try
            {
                conn.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            if(connected)
            {
                LOCALIZATION_SCHEME = LocalizationSelect.getValue();
                // Check if elgamal is set and keys are null
                switch (LOCALIZATION_SCHEME)
                {
                    // These map to EL-Gamal Min, MCA, DMA
                    case 10:
                    case 11:
                    case 12:
                        if (e_pk == null || e_sk == null)
                        {
                            Toast.makeText(getApplicationContext(), "El Gamal Keys not generated!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        break;
                }
                Intent Localize = new Intent(MainActivity.this, LocalizeActivity.class);
                Localize.putExtra("Localization", LOCALIZATION_SCHEME);
                // CRITICAL: USEFUL FOR KEEPING MAIN ACTIVITY ALIVE
                Localize.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(Localize);
            }
        }

        public void run()
        {
            try
            {
                ClientSocket = new Socket(SQLDatabase, portNumber);
                toServer = new ObjectOutputStream(ClientSocket.getOutputStream());
                toServer.writeObject("Hello");
                toServer.flush();
                connected = true;
            }
            catch (IOException e)
            {
                Wifi_needed.show();
                connected = false;
            }
        }
    }

    private class reset implements View.OnClickListener, Runnable
    {
        public void onClick(View v)
        {
            new Thread(this).start();
        }

        public void run()
        {
            try
            {
                ClientSocket = new Socket(SQLDatabase, portNumber);

                //Troubleshoot/Confirm the Socket Successfully connected
                if (!ClientSocket.isConnected())
                {
                    Log.d(TAG, "Client Socket is NOT connected" + portNumber);
                }
                Log.d(TAG, "Client Socket Successfully Connected: " + portNumber);

                //Prepare I/O Stream
                toServer = new ObjectOutputStream(ClientSocket.getOutputStream());
                fromServer = new ObjectInputStream(ClientSocket.getInputStream());
                toServer.writeObject("RESET");
                toServer.flush();
                if (fromServer.readBoolean())
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Toast.makeText(getApplicationContext(), "RESET COMPLETE!", Toast.LENGTH_LONG).show();
                        }
                    });
                }
                else
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Toast.makeText(getApplicationContext(), "RESET FAILED!", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
            catch (IOException e)
            {
                Wifi_needed.show();
            }
        }
    }

    private class undo implements View.OnClickListener, Runnable
    {
        public void onClick(View v)
        {
            new Thread(this).start();
        }

        public void run()
        {
            try
            {
                ClientSocket = new Socket(SQLDatabase, portNumber);

                //Troubleshoot/Confirm the Socket Successfully connected
                if (!ClientSocket.isConnected())
                {
                    Log.d(TAG, "Client Socket is NOT connected" + portNumber);
                }
                Log.d(TAG, "Client Socket Successfully Connected" + portNumber);

                //Prepare I/O Stream
                toServer = new ObjectOutputStream(ClientSocket.getOutputStream());
                fromServer = new ObjectInputStream(ClientSocket.getInputStream());
                toServer.writeObject("UNDO");
                toServer.flush();

                if (fromServer.readBoolean())
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Toast.makeText(getApplicationContext(),"UNDO COMPLETE!", Toast.LENGTH_LONG).show();
                        }
                    });
                }
                else
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Toast.makeText(getApplicationContext(),"UNDO FAILED!", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
            catch (IOException e)
            {
                Wifi_needed.show();
            }
        }
    }

    private class start_scan implements View.OnClickListener
    {
        public void onClick(View v)
        {
            if(mWifiManager.startScan())
            {
                Toast.makeText(getApplicationContext(), "Got reading from Wifi Manager", Toast.LENGTH_LONG).show();
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Scanning Wi-Fi failed!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Build the Paillier and DGK Public/Private Key pairs!
    public void run()
    {
        // Key Generation
        startTime = System.nanoTime();

        // Build DGK Keys
        DGKKeyPairGenerator gen = new DGKKeyPairGenerator(16, 160, KEY_SIZE);
        gen.initialize(1024, new SecureRandom()); //Speed up Key Making Process
        KeyPair DGK = gen.generateKeyPair();
        DGKsk = (DGKPrivateKey) DGK.getPrivate();
        DGKpk = (DGKPublicKey) DGK.getPublic();

        // Build Paillier Keys
        PaillierKeyPairGenerator p = new PaillierKeyPairGenerator();
        p.initialize(KEY_SIZE, new SecureRandom());
        KeyPair paillier = p.generateKeyPair();
        pk = (PaillierPublicKey) paillier.getPublic();
        sk = (PaillierPrivateKey) paillier.getPrivate();

        endTime = System.nanoTime();
        Log.d(TAG, "Time to complete Key Generation: " + (endTime-startTime)/BILLION + " seconds");
    }

    /*
    public void El_Gamal()
    {
        // Build ElGamal Keys
        ElGamalKeyPairGenerator pg = new ElGamalKeyPairGenerator();
        pg.initialize(KEY_SIZE, new SecureRandom());
        KeyPair el_gamal = pg.generateKeyPair();
        e_pk = (ElGamalPublicKey) el_gamal.getPublic();
        e_sk = (ElGamalPrivateKey) el_gamal.getPrivate();
    }
    */

    public void onResume()
    {
        super.onResume();
        mWifiManager.registerReceiver(this);
    }

    public void onPause()
    {
        super.onPause();
        mWifiManager.unregisterReceiver(this);
    }

    public static String [] getPhoneData()
    {
        String [] phones = new String[4];
        phones[0] = System.getProperty("os.version");  // OS version
        phones[1] = android.os.Build.DEVICE;           // Device
        phones[2] = getDeviceName();                   // Manufactuer/Model
        phones[3] = android.os.Build.PRODUCT;          // Product
        return phones;
    }

    // source: https://stackoverflow.com/questions/1995439/get-android-phone-model-programmatically
    // Better way to get model
    private static String getDeviceName()
    {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase()))
        {
            return capitalize(model);
        }
        else
        {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private static String capitalize(String s)
    {
        if (s == null || s.length() == 0)
        {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first))
        {
            return s;
        }
        else
        {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    /*
    protected void MAC_to_AP_Name()
    {
        String line;
        String [] tuple;
        try
        {
            Resources res = getResources();
            InputStream in_s = res.openRawResource(R.raw.broadwayfloormap);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in_s));
            while((line = reader.readLine()) != null)
            {
                tuple = line.split(",");
                AP_map.put(tuple[0], tuple[1]);
                //answer.put(tuple[0], tuple[1]);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
    */
}