package ui;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import Localization.KeyMaster;
import edu.fiu.reu2017.R;

import static ui.MainActivity.SQLDatabase;
import static ui.MainActivity.TIMEOUT;
import static ui.MainActivity.portNumber;

public class AddMapActivity extends Activity
{
    EditText textTargetUri;
    ImageView targetImage;
    Bitmap bitmap = null;

    private Switch mode;
    /** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_map);
        Button buttonLoadImage = findViewById(R.id.loadimage);
        textTargetUri = findViewById(R.id.targeturi);
        targetImage = findViewById(R.id.targetimage);

        // Add both button to get/send and mode
        mode = findViewById(R.id.pick);
        Button send = findViewById(R.id.send);
        send.setOnClickListener(new communicate());
        buttonLoadImage.setOnClickListener(new OnClickListener()
        {
            public void onClick(View arg0)
            {
                Intent intent = new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, 0);
            }
        });
    }

    private class communicate implements View.OnClickListener, Runnable
    {
        public void onClick(View v)
        {
            new Thread(this).start();
        }

        public void run()
        {
            byte [] encoded_image;
            try {
                try (Socket ClientSocket = new Socket();) {
                    ClientSocket.connect(new InetSocketAddress(SQLDatabase, portNumber), TIMEOUT);

                    // Prepare I/O Stream
                    ObjectOutputStream toServer = new ObjectOutputStream(ClientSocket.getOutputStream());
                    ObjectInputStream fromServer = new ObjectInputStream(ClientSocket.getInputStream());
                    final String map_name = textTargetUri.getText().toString();

                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Connected!", Toast.LENGTH_LONG).show());

                    if(map_name.isEmpty()) {
                        toServer.writeObject("Invalid Map Name received, ending now!");
                        toServer.flush();
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Blank Map Name Try Again!", Toast.LENGTH_LONG).show());
                        return;
                    }

                    if(mode.isChecked()) {
                        if(bitmap != null) {
                            encoded_image = encode(bitmap);
                        }
                        else {
                            toServer.writeObject("Invalid Map received, ending now!");
                            toServer.flush();
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Blank Map! Try Again!", Toast.LENGTH_LONG).show());
                            return;
                        }

                        // SET
                        toServer.writeObject("SET");
                        toServer.flush();

                        // Send Image Name
                        toServer.writeObject(map_name + ".bmp");
                        toServer.flush();

                        toServer.writeInt(encoded_image.length);
                        toServer.flush();

                        // Send Image itself
                        toServer.write(encoded_image);
                        toServer.flush();

                        // Get confirmation!
                        if (fromServer.readBoolean()) {
                            KeyMaster.map = bitmap;
                            KeyMaster.map_name = map_name;
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "UPLOAD COMPLETE!", Toast.LENGTH_LONG).show());
                        }
                        else {
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "UPLOAD FAILED!", Toast.LENGTH_LONG).show());
                        }
                    }
                    else {
                        // GET
                        toServer.writeObject("GET");
                        toServer.flush();

                        // Send Image Name
                        toServer.writeObject(map_name + ".bmp");
                        toServer.flush();

                        // Get Size
                        int size = fromServer.readInt();

                        if (size != 0) {
                            // Get image
                            encoded_image = new byte[size];
                            fromServer.readFully(encoded_image);
                            bitmap = decode(encoded_image);
                            // Save it to Key Master
                            KeyMaster.map = bitmap;
                            KeyMaster.map_name = map_name;
                        }
                        else {
                            // Inform user and close out!
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "File " + map_name + ".bmp not found on Server!", Toast.LENGTH_LONG).show());
                        }
                    }
                }
            }
            catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Failed to connect to Server! Or Invalid Map Name!", Toast.LENGTH_LONG).show());
            }
        }

    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            Uri targetUri = data.getData();
            try {
                String out = null;
                if (targetUri != null) {
                    out = targetUri.toString();
                }
                textTargetUri.setText(out);
                if (targetUri != null) {
                    bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(targetUri));
                }
                targetImage.setImageBitmap(bitmap);
            }
            catch (FileNotFoundException e) {
                Log.e(this.getClass().getName(), "File not found", e);
            }
        }
    }

    // https://stackoverflow.com/questions/13854742/byte-array-of-image-into-imageview
    protected byte [] encode(Bitmap bmp) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    protected Bitmap decode(byte [] byteArray) {
        bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        runOnUiThread(() -> targetImage.setImageBitmap(Bitmap.createScaledBitmap(bitmap, targetImage.getWidth(),
                targetImage.getHeight(), false)));
        return bitmap;
    }
}