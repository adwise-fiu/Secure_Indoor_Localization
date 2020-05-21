package sensors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import java.util.List;

import ui.MainActivity;

import static android.widget.Toast.*;

public class WifiReceiver extends BroadcastReceiver implements Runnable
{
    private final static String TAG = "MAIN_SCANNER";
    private ProgressBar loading;
    private WifiManager my_wifiManager;
    private boolean isRegistered = false;

    // Data
    public String  [] WifiAP;
    public Integer [] WifiRSS;

    private Context context;

    public WifiReceiver(Context context, ProgressBar loading)
    {
        this.loading = loading;
        this.context = context;
        my_wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(my_wifiManager == null)
        {
            Log.d(TAG, "WIFI MANAGER IS NULL!");
            System.exit(1);
        }
        if (!my_wifiManager.isWifiEnabled())
        {
            makeText(context.getApplicationContext(), "wifi is disabled...making it enabled", LENGTH_LONG).show();
            my_wifiManager.setWifiEnabled(true);
        }
        registerReceiver(context);
    }

    public void registerReceiver(Context context)
    {
        if(!isRegistered)
        {
            context.registerReceiver(this, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            isRegistered = true;
            get_data();
            makeText(context.getApplicationContext(), "Registered Wifi Receiver!", LENGTH_SHORT).show();
        }
    }

    public void unregisterReceiver(Context context)
    {
        if(isRegistered)
        {
            context.unregisterReceiver(this);
            isRegistered = false;
        }
    }

    // Try this...
    // https://stackoverflow.com/questions/13238600/use-registerreceiver-for-non-activity-and-non-service-class
    public void onReceive(Context context, Intent intent)
    {
        String action = intent.getAction();
        if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action))
        {
            get_data();
            makeText(context.getApplicationContext(), "Got new data from WifiReceiver!", LENGTH_LONG).show();
        }
    }

    private void get_data()
    {
        List<ScanResult> results = my_wifiManager.getScanResults();
        WifiAP = new String[results.size()];
        WifiRSS = new Integer[results.size()];
        for (int i = 0; i < results.size(); i++)
        {
            WifiAP[i] = results.get(i).BSSID;
            WifiRSS[i] = results.get(i).level;
            // makeText(context.getApplicationContext(), results.get(i).SSID, LENGTH_SHORT).show();
        }

        // WHAT APs were found!
        // Note AP maps a MAC Address to a Physical Location
        // e. g. MAC 1 -> Broadway 3
        for (String s : WifiAP)
        {
            // The purpose to have this is because maybe you want to
            // be able to test how far an AP is until you can't see it.
            String AP_name = MainActivity.AP_map.get(s);
            if (AP_name != null)
            {
                makeText(context.getApplicationContext(), "Found AP " + AP_name, LENGTH_LONG).show();
            }
        }

        if (loading != null)
        {
            loading.setVisibility(View.INVISIBLE);
        }
    }

    public boolean startScan()
    {
        try
        {
            run();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void run()
    {
        this.unregisterReceiver(context);
        this.registerReceiver(context);
        get_data();
    }
}
