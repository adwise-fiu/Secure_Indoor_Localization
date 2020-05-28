package Localization;

import android.graphics.Bitmap;

import java.security.KeyPair;
import java.security.SecureRandom;

import security.DGK.DGKKeyPairGenerator;
import security.DGK.DGKPrivateKey;
import security.DGK.DGKPublicKey;
import security.elgamal.ElGamalKeyPairGenerator;
import security.elgamal.ElGamalPrivateKey;
import security.elgamal.ElGamalPublicKey;
import security.paillier.PaillierKeyPairGenerator;
import security.paillier.PaillierPrivateKey;
import security.paillier.PaillierPublicKey;

public final class KeyMaster implements Runnable
{
    private final static int KEY_SIZE = 1024;

    // Key Master
    static DGKPrivateKey DGKsk;
    static DGKPublicKey DGKpk;
    static PaillierPublicKey pk;
    static PaillierPrivateKey sk;
    static ElGamalPublicKey e_pk;
    static ElGamalPrivateKey e_sk;

    public static long duration;
    public static boolean finished = false;
    public static boolean ElGamal = false;

    // Hold onto Map Data
    public static Bitmap map = null;
    public static String map_name = "";

    // Hold onto LAST fingerprint to delete just in case
    public static Double [] last_coordinates = new Double[2];
    public static String last_device;

    public static void init()
    {
        long startTime = System.nanoTime();

        // Build DGK Keys
        DGKKeyPairGenerator gen = new DGKKeyPairGenerator(16, 160, KEY_SIZE);
        gen.initialize(KEY_SIZE, new SecureRandom()); //Speed up Key Making Process
        KeyPair DGK = gen.generateKeyPair();
        DGKsk = (DGKPrivateKey) DGK.getPrivate();
        DGKpk = (DGKPublicKey) DGK.getPublic();

        // Build Paillier Keys
        PaillierKeyPairGenerator p = new PaillierKeyPairGenerator();
        p.initialize(KEY_SIZE, new SecureRandom());
        KeyPair paillier = p.generateKeyPair();
        pk = (PaillierPublicKey) paillier.getPublic();
        sk = (PaillierPrivateKey) paillier.getPrivate();
        duration = System.nanoTime() - startTime;
        finished = true;
    }

    public void run()
    {
        init();
        if(ElGamal)
        {
            // Can run ElGamal on Thread
            ElGamalKeyPairGenerator pg = new ElGamalKeyPairGenerator();
            pg.initialize(KEY_SIZE, new SecureRandom());
            KeyPair el_gamal = pg.generateKeyPair();
            e_pk = (ElGamalPublicKey) el_gamal.getPublic();
            e_sk = (ElGamalPrivateKey) el_gamal.getPrivate();
        }
    }
}
