package edu.fiu.adwise.fingerprint_localization.localization;

import android.graphics.Bitmap;

import java.security.KeyPair;
import java.security.SecureRandom;

import edu.fiu.adwise.ciphercraft.dgk.DGKKeyPairGenerator;
import edu.fiu.adwise.ciphercraft.dgk.DGKPrivateKey;
import edu.fiu.adwise.ciphercraft.dgk.DGKPublicKey;
import edu.fiu.adwise.ciphercraft.misc.HomomorphicException;
import edu.fiu.adwise.ciphercraft.paillier.PaillierKeyPairGenerator;
import edu.fiu.adwise.ciphercraft.paillier.PaillierPrivateKey;
import edu.fiu.adwise.ciphercraft.paillier.PaillierPublicKey;

public final class KeyMaster implements Runnable {
    private final static int KEY_SIZE = 2048;
    // Key Master
    static DGKPrivateKey DGKsk;
    static DGKPublicKey DGKpk;
    static PaillierPublicKey pk;
    static PaillierPrivateKey sk;
    public static long duration;
    public static boolean finished = false;
    // Hold onto Map Data
    public static Bitmap map = null;
    public static String map_name = "";

    // Hold onto LAST fingerprint to delete just in case
    public static Double [] last_coordinates = new Double[2];
    public static String last_device;

    public static void init() throws HomomorphicException {
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

    public void run() {
        try {
            init();
        } catch (HomomorphicException e) {
            throw new RuntimeException(e);
        }
    }
}
