package ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.chrisbanes.photoview.OnPhotoTapListener;
import com.github.chrisbanes.photoview.PhotoViewAttacher;

import Localization.KeyMaster;
import edu.fiu.adwise.fingerprint_localization.distance_computation.LOCALIZATION_SCHEME;
import edu.fiu.reu2017.R;
import Localization.background;
import sensors.WifiReceiver;

public class LocalizeActivity extends AppCompatActivity {
    protected WifiReceiver wifi_wrapper;
    private int SCHEME = -1;
    private Bitmap location;
    // GUI
    public Button scan;
    private TextView output;
    protected ProgressBar loading;
    private PhotoViewAttacher my_Attach;
    private ImageView imageView;
    private Switch REU2017Mode;
    private boolean scan_complete;

    public static Toast off_map;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.localize_activity);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        off_map = Toast.makeText(getApplicationContext(), "Your AP filtering is too strong or you are off the map!", Toast.LENGTH_LONG);
        // Attach GUI
        scan = findViewById(R.id.scan);
        output = findViewById(R.id.time);
        loading = findViewById(R.id.localize_bar);
        imageView = findViewById(R.id.map);
        REU2017Mode = findViewById(R.id.REU2017);

        Intent i = getIntent();
        SCHEME = i.getIntExtra("Localization", SCHEME);

        location = BitmapFactory.decodeResource(getResources(), R.drawable.o);
        wifi_wrapper = new WifiReceiver(this, loading);
        imageView.post(() -> imageView.setImageBitmap(Bitmap.createScaledBitmap(KeyMaster.map, imageView.getWidth(),
                imageView.getHeight(), false)));
        my_Attach = new PhotoViewAttacher(imageView);
        my_Attach.setMaximumScale((float) 7.0);

        if (SCHEME >= 1 && SCHEME <= 12) {
            Toast.makeText(getApplicationContext(), "LOCALIZATION SCHEME: " + LOCALIZATION_SCHEME.from_int(SCHEME), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "LOCALIZATION SCHEME: INVALID!", Toast.LENGTH_LONG).show();
        }
        scan.setOnClickListener(new scan());
        my_Attach.setOnPhotoTapListener(new attach());
    }

    // Need a GET Map()
    // Also, each time you train, you need to send MAP data too now

    private class scan implements View.OnClickListener {
        public void onClick(View v) {
            loading.setVisibility(View.VISIBLE);
            if(wifi_wrapper.startScan()) {
                scan_complete = true;
                Toast.makeText(getApplicationContext(), "Got reading from Wifi Manager", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class attach implements OnPhotoTapListener
    {
        public void onPhotoTap (ImageView view, float x, float y) {
            // CLEAR RED DOT, RE-LOAD
            imageView.post(new Runnable() {
                public void run() {
                    imageView.setImageBitmap(Bitmap.createScaledBitmap(KeyMaster.map, imageView.getWidth(),
                            imageView.getHeight(), false));
                }
            });
            my_Attach.update();

            if(scan_complete) {
                Toast.makeText(getApplicationContext(), "Starting Localization!", Toast.LENGTH_LONG).show();
                loading.setVisibility(View.VISIBLE);
                loading.setProgress(0);
                scan_complete = false;

                // ERROR: NEED TO ORGANIZE MY SCAN ACCORDING TO COLUMNS IN MYSQL TABLE
                if(wifi_wrapper.WifiAP == null || wifi_wrapper.WifiRSS == null) {
                    Toast.makeText(getApplicationContext(), "Scan again please!", Toast.LENGTH_SHORT).show();
                } else {
                    output.setText(R.string.localizing);
                    new background(SCHEME, REU2017Mode.isChecked(), wifi_wrapper.WifiAP,
                                wifi_wrapper.WifiRSS, output, loading, location, imageView, my_Attach).execute();
                }
            } else {
                Toast.makeText(getApplicationContext(), "Scan first!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    protected void onResume() {
        super.onResume();
        wifi_wrapper.registerReceiver(this);
    }

    protected void onPause() {
        super.onPause();
        wifi_wrapper.unregisterReceiver(this);
    }
}
