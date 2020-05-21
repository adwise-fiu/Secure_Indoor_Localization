package Localization;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.chrisbanes.photoview.PhotoViewAttacher;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import Localization.structs.SendLocalizationData;
import Localization.structs.LocalizationResult;
import security.elgamal.ElGamalCipher;
import security.elgamal.ElGamalPrivateKey;
import security.elgamal.ElGamalPublicKey;
import security.elgamal.ElGamal_Ciphertext;
import ui.LocalizeActivity;
import ui.MainActivity;

import security.DGK.DGKOperations;
import security.DGK.DGKPrivateKey;
import security.DGK.DGKPublicKey;
import security.paillier.PaillierCipher;
import security.paillier.PaillierPrivateKey;
import security.paillier.PaillierPublicKey;

import static android.graphics.Color.RED;
import static Localization.LOCALIZATION_SCHEME.*;

// https://www.concretepage.com/android/android-asynctask-example-with-progress-bar
public final class background extends AsyncTask<Void, Integer, Float[]>
{
    private final static String TAG = "LOCALIZE";

    // Packages to send
    private String [] phone_data;
    List<LocalizationResult> fromServer = new ArrayList<>();
    Double [] coordinates = new Double[2];
    private LOCALIZATION_SCHEME LOCALIZATION_SCHEME;

    //Keys and read Keys
    private static PaillierPublicKey pk = MainActivity.pk;
    private static DGKPublicKey DGKpk = MainActivity.DGKpk;
    private static PaillierPrivateKey sk = MainActivity.sk;
    private static DGKPrivateKey DGKsk = MainActivity.DGKsk;
    private static ElGamalPublicKey e_pk = MainActivity.e_pk;
    private static ElGamalPrivateKey e_sk = MainActivity.e_sk;

    // Localization variables
    private long distanceSUM = 0;
    private double [] w_i = new double[MainActivity.k];
    private boolean isREU2017;

    // Time
    private long startTime;

    // GUI
    private WeakReference<TextView> results;
    private WeakReference<ProgressBar> progress;

    private WeakReference<Bitmap> location;
    private WeakReference<ImageView> imageView;
    private WeakReference<PhotoViewAttacher> my_Attach;

    private String [] Found_APs;
    private Integer [] Found_RSS;

    // To be sent
    private String [] MAC_send;
    private Integer [] RSS_send;

    private Thread t;

    // SST REU 2017
    String [] CommonMAC;

    public background(int SCHEME, boolean _isREU2017, String [] MAC_send, Integer [] RSS_send,
                      TextView output, ProgressBar progress, Bitmap location, ImageView imageView, PhotoViewAttacher my_Attach)
    {
        this.LOCALIZATION_SCHEME = from_int(SCHEME);
        this.isREU2017 = _isREU2017;
        this.Found_APs = MAC_send;
        this.Found_RSS = RSS_send;
        this.results = new WeakReference<>(output);
        this.progress = new WeakReference<>(progress);
        this.location = new WeakReference<>(location);
        this.imageView =  new WeakReference<>(imageView);
        this.my_Attach = new WeakReference<>(my_Attach);
        // Fill phone data
        phone_data = MainActivity.getPhoneData();
    }

    protected void onPreExecute()
    {
        startTime = System.nanoTime();

        // 1- GET THE COLUMN MAC ADDRESSES
        (t = new Thread(new ClientThread(this))).start();
        try
        {
            t.join();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        // 2- ORGANIZE THE AP/RSSI READINGS CORRECTLY!
        MainActivity.VECTOR_SIZE = CommonMAC.length;
        MAC_send = new String [MainActivity.VECTOR_SIZE];
        RSS_send = new Integer[MainActivity.VECTOR_SIZE];

        // Initialize...
        for (int i = 0; i < MainActivity.VECTOR_SIZE; i++)
        {
            MAC_send[i] = "NULL";
            RSS_send[i] = -120;
        }

        // 2- Prepare data to match with columns in Fingerprint
        for (int column = 0; column < MainActivity.VECTOR_SIZE; column++)
        {
            // Match AP Scan with correct column
            for (int i = 0; i < Found_APs.length; i++)
            {
                if (Found_APs[i].equals(CommonMAC[column]))
                {
                    MAC_send[column] = Found_APs[i];
                    RSS_send[column] = Found_RSS[i];
                }
            }
        }
    }

    protected Float [] doInBackground(Void... voids)
    {
        Float [] location = new Float[2];

        // S2 comp
        BigInteger[] S2;

        // S3
        long S3_plaintext = 0;
        BigInteger S3;

        // S3 comp
        BigInteger[] S3_comp;

        // For El Gamal
        ElGamal_Ciphertext e_S3;
        List<ElGamal_Ciphertext> e_S2 = new ArrayList<>();
        List<ElGamal_Ciphertext> e_S3_comp = new ArrayList<>();

         S2 = new BigInteger[MainActivity.VECTOR_SIZE];
         S3_comp = new BigInteger[MainActivity.VECTOR_SIZE];

        publishProgress(2);

        switch (LOCALIZATION_SCHEME)
        {
            case PLAIN_MIN:
            case PLAIN_DMA:
            case PLAIN_MCA:

                ClientThread plainSend = new ClientThread(new SendLocalizationData(MAC_send, RSS_send, DGKpk,
                        LOCALIZATION_SCHEME, isREU2017, phone_data), this, LOCALIZATION_SCHEME.value);
                t = new Thread(plainSend);
                t.start();
                try
                {
                    t.join();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                publishProgress(3);
                Log.d(TAG, "Server Communication completed!");

                // Access data from Send Distance
                if (isREU2017)
                {
                    publishProgress(9);
                    // The Server already computed the location!
                    location[0] = coordinates[0].floatValue();
                    location[1] = coordinates[1].floatValue();
                }
                else
                {
                    // get a Send Distance Object from Server...
                    if(LOCALIZATION_SCHEME == PLAIN_DMA)
                    {
                        for(LocalizationResult l: fromServer)
                        {
                            l.plain_decrypt();
                        }
                    }
                    // ---- Sort Distances!-----
                    Collections.sort(fromServer);
                    publishProgress(9);

                    if (LOCALIZATION_SCHEME == PLAIN_MIN)
                    {
                        location[0] = fromServer.get(0).getX().floatValue();
                        location[1] = fromServer.get(0).getY().floatValue();
                    }
                    else if (LOCALIZATION_SCHEME == PLAIN_MCA || LOCALIZATION_SCHEME == PLAIN_DMA)
                    {
                        return Phase3();
                    }
                    publishProgress(10);
                }
                //Place it on the map...
                break;

            case DGK_MIN:
            case DGK_MCA:
            case DGK_DMA:
                try
                {
                    if(LOCALIZATION_SCHEME == DGK_DMA)
                    {
                        for (int i = 0; i < MainActivity.VECTOR_SIZE; i++)
                        {
                            S2[i] = DGKOperations.encrypt(DGKpk, -2 * RSS_send[i]);
                            S3_comp[i] = DGKOperations.encrypt(DGKpk, RSS_send[i] * RSS_send[i]);
                        }

                        t = new Thread( new ClientThread(
                                new SendLocalizationData(MAC_send, S2, null, S3_comp,
                                        DGKpk, LOCALIZATION_SCHEME, isREU2017, phone_data),
                                this, LOCALIZATION_SCHEME.value));
                        t.start();
                    }
                    else
                    {
                        for (int i = 0; i < MainActivity.VECTOR_SIZE; i++)
                        {
                            S2[i] = DGKOperations.encrypt(DGKpk, -2 * RSS_send[i]);
                            S3_plaintext += RSS_send[i] * RSS_send[i];
                        }

                        try
                        {
                            S3 = DGKOperations.encrypt(S3_plaintext, DGKpk);
                        }
                        catch(IllegalArgumentException e)
                        {
                            S3 = DGKOperations.encrypt(S3_plaintext % DGKpk.getU().longValue(), DGKpk);
                        }

                        t = new Thread( new ClientThread(
                                new SendLocalizationData(MAC_send, S2, S3, null,
                                        DGKpk, LOCALIZATION_SCHEME, isREU2017, phone_data),
                                this, LOCALIZATION_SCHEME.value));
                        t.start();
                    }
                    t.join();
                    publishProgress(4);
                    Log.d(TAG, "Client Thread starting to submit DGK Data");

                /*
                I have a sorted distance list
                And (x, y) and matches are lined up correctly
                */

                    if (isREU2017)
                    {
                        // The Server already computed the location!
                        location[0] = coordinates[0].floatValue();
                        location[1] = coordinates[1].floatValue();
                        publishProgress(10);
                    }
                    else
                    {
                        // 1- Get Data from Server & Decrypt Distances
                        for (int i = 0; i < fromServer.size(); i++)
                        {
                            fromServer.get(i).decrypt_all(DGKsk);
                        }
                        // 3- Sort
                        Collections.sort(fromServer);
                        publishProgress(8);

                        // Minimum Distance only
                        if (LOCALIZATION_SCHEME == DGK_MIN)
                        {
                            //Find its matching (x, y)
                            location[0] = fromServer.get(0).getX().floatValue();
                            location[1] = fromServer.get(0).getX().floatValue();
                        }
                        // MCA/DMA
                        if (LOCALIZATION_SCHEME == DGK_MCA || LOCALIZATION_SCHEME == DGK_DMA)
                        {
                            return Phase3();
                        }
                        publishProgress(10);
                    }
                    break;
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                catch (IndexOutOfBoundsException | NullPointerException e)
                {
                    LocalizeActivity.off_map.show();
                }

            case PAILLIER_MIN:
            case PAILLIER_MCA:
            case PAILLIER_DMA:
                try
                {
                    if(LOCALIZATION_SCHEME == PAILLIER_DMA)
                    {
                        for (int i = 0; i < MainActivity.VECTOR_SIZE; i++)
                        {
                            S2[i] = PaillierCipher.encrypt(-2 * RSS_send[i], pk);
                            S3_comp[i] = PaillierCipher.encrypt(RSS_send[i] * RSS_send[i], pk);
                        }

                        (t = new Thread(new ClientThread(
                                new SendLocalizationData(MAC_send, S2, null, S3_comp,
                                        pk, DGKpk,
                                        LOCALIZATION_SCHEME, isREU2017, phone_data),
                                this, LOCALIZATION_SCHEME.value))).start();
                    }
                    else
                    {
                        for (int i = 0; i < MainActivity.VECTOR_SIZE; i++)
                        {
                            S2[i] = PaillierCipher.encrypt(-2 * RSS_send[i], pk);
                            S3_plaintext += RSS_send[i] * RSS_send[i];
                        }
                        S3 = PaillierCipher.encrypt(S3_plaintext, pk);

                        (t = new Thread(new ClientThread(
                                new SendLocalizationData(MAC_send, S2, S3, null,
                                        pk, DGKpk,
                                        LOCALIZATION_SCHEME, isREU2017, phone_data),
                                this, LOCALIZATION_SCHEME.value))).start();
                    }
                    t.join();
                    publishProgress(4);

                    if (isREU2017)
                    {
                        // The Server already computed the location!
                        location[0] = coordinates[0].floatValue();
                        location[1] = coordinates[1].floatValue();
                        publishProgress(10);
                    }
                    else
                    {
                        // 1- Get Data from Server and decrypt distance
                        for (int i = 0; i < fromServer.size(); i++)
                        {
                            fromServer.get(i).decrypt_all(sk);
                        }
                        // 3- Sort data
                        Collections.sort(fromServer);
                        publishProgress(8);

                        // Minimum Distance
                        if (LOCALIZATION_SCHEME == PAILLIER_MIN)
                        {
                            // Find its matching (x, y)
                            location[0] = fromServer.get(0).getX().floatValue();
                            location[1] = fromServer.get(0).getY().floatValue();
                        }
                        // MCA/DMA
                        if (LOCALIZATION_SCHEME == PAILLIER_DMA || LOCALIZATION_SCHEME == PAILLIER_MCA)
                        {
                            return Phase3();
                        }
                        publishProgress(10);
                    }
                    break;
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                catch (IndexOutOfBoundsException | NullPointerException e)
                {
                    LocalizeActivity.off_map.show();
                }

            case EL_GAMAL_MIN:
            case EL_GAMAL_MCA:
            case EL_GAMAL_DMA:
                try
                {
                    if(LOCALIZATION_SCHEME == EL_GAMAL_DMA)
                    {
                        for (int i = 0; i < MainActivity.VECTOR_SIZE; i++)
                        {
                            e_S2.add(ElGamalCipher.encrypt(e_pk, -2 * RSS_send[i]));
                            e_S3_comp.add(ElGamalCipher.encrypt(e_pk, RSS_send[i] * RSS_send[i]));
                        }

                        (t = new Thread(new ClientThread(
                                new SendLocalizationData(MAC_send, e_S2, null, e_S3_comp,
                                        e_pk, DGKpk,
                                        LOCALIZATION_SCHEME, isREU2017, phone_data),
                                this, LOCALIZATION_SCHEME.value))).start();
                    }
                    else
                    {
                        for (int i = 0; i < MainActivity.VECTOR_SIZE; i++)
                        {
                            e_S2.add(ElGamalCipher.encrypt(e_pk, -2 * RSS_send[i]));
                            S3_plaintext += RSS_send[i] * RSS_send[i];
                        }
                        e_S3 = ElGamalCipher.encrypt(e_pk, S3_plaintext);

                        (t = new Thread(new ClientThread(
                                new SendLocalizationData(MAC_send, e_S2, e_S3, null,
                                        e_pk, DGKpk,
                                        LOCALIZATION_SCHEME, isREU2017, phone_data),
                                this, LOCALIZATION_SCHEME.value))).start();
                    }
                    t.join();
                    publishProgress(4);
                /*
                I have a sorted distance list
                And (x, y) and matches are lined up correctly
                */
                    if (isREU2017)
                    {
                        // The Server already computed the location!
                        location[0] = coordinates[0].floatValue();
                        location[1] = coordinates[1].floatValue();
                        publishProgress(10);
                    }
                    else
                    {
                        // 1- Get Data from Server and decrypt Distance
                        for (int i = 0; i < fromServer.size(); i++)
                        {
                            fromServer.get(i).decrypt_all(e_sk);
                        }
                        // 3- Sort data
                        Collections.sort(fromServer);
                        publishProgress(8);

                        // Minimum Distance
                        if (LOCALIZATION_SCHEME == EL_GAMAL_MIN)
                        {
                            // Find its matching (x, y)
                            location[0] = fromServer.get(0).getX().floatValue();
                            location[1] = fromServer.get(0).getY().floatValue();
                        }
                        // MCA/DMA
                        if (LOCALIZATION_SCHEME == EL_GAMAL_DMA || LOCALIZATION_SCHEME == EL_GAMAL_MCA)
                        {
                            return Phase3();
                        }
                        publishProgress(10);
                    }
                    break;
                }
                catch (IndexOutOfBoundsException | NullPointerException e)
                {
                    e.printStackTrace();
                    LocalizeActivity.off_map.show();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            default:
                Log.d(TAG, "Case Switch Failure: Localization Scheme set to: " + LOCALIZATION_SCHEME);
                break;
        }
        return location;
    }

    // TO BE USED ONLY BY PLAINTEXT METHODS
    private Float [] Phase3()
    {
        float x = 0;
        float y = 0;

        for (int i = 0; i < MainActivity.k; i++)
        {
            distanceSUM += fromServer.get(i).plainDistance;
        }

        // Find value of all w_i
        for (int i = 0 ; i < MainActivity.k; i++)
        {
            w_i[i] = ((double) fromServer.get(i).plainDistance/distanceSUM);
            w_i[i] = 1.0 - w_i[i];
            w_i[i] = w_i[i]/(MainActivity.k - 1);
            x += w_i[i] * fromServer.get(i).getX();
            y += w_i[i] * fromServer.get(i).getY();
        }

        Float [] final_location = new Float[2];
        final_location[0] = x;
        final_location[1] = y;
        publishProgress(10);
        return final_location;
    }

    protected void onProgressUpdate(Integer... values)
    {
        this.progress.get().setProgress(values[0]);
    }

    // Do the Drawing!
    protected void onPostExecute(Float[] result)
    {
        BitmapDrawable drawTrain;
        final Bitmap mutableBitmap;
        Canvas drawFlags;
        int flagH;
        int flagW;

        drawTrain = (BitmapDrawable) imageView.get().getDrawable();
        mutableBitmap = drawTrain.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
        drawFlags = new Canvas(mutableBitmap);

        Log.d(TAG, "Draw X bit: " + result[0] + ", Draw Y bit: " + result[1]);

        //Dimensions of Flag
        flagW = location.get().getWidth();
        flagH = location.get().getHeight();

        try
        {
            drawFlags.drawBitmap(location.get(), result[0] - (float) (flagW / 2), result[1] - (float) (flagH / 2), new Paint(RED));
            imageView.get().post(new Runnable() {
                public void run() {
                    imageView.get().setImageBitmap(mutableBitmap);
                }
            });
            my_Attach.get().update();
        }
        catch (NullPointerException e)
        {
            // This will happen if your FSF is too strict or you are off the map.
        }
        finally
        {
            String msg = (System.nanoTime() - startTime) / MainActivity.BILLION + " seconds.";
            results.get().setText(msg);
            Log.d(TAG, " Localization Scheme: " + LOCALIZATION_SCHEME + ", time to complete: " + (System.nanoTime() - startTime) / MainActivity.BILLION);
        }
    }
}
