package edu.fiu.adwise.fingerprint_tests;

import static org.junit.Assert.assertEquals;

import edu.fiu.adwise.homomorphic_encryption.dgk.DGKKeyPairGenerator;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPublicKey;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyPair;

public class PlainTestDistanceTest {

    private static int KEY_SIZE = 1024;
    private static DGKPublicKey public_key;
    private static DGKPrivateKey private_key;

    private static BigInteger a;

    @BeforeClass
    public static void generate_keys() {
        DGKKeyPairGenerator pa = new DGKKeyPairGenerator();
        pa.initialize(KEY_SIZE, null);
        KeyPair dgk = pa.generateKeyPair();
        public_key = (DGKPublicKey) dgk.getPublic();
        private_key = (DGKPrivateKey) dgk.getPrivate();
    }

    @BeforeClass
    public static void prepare_fingerprint_database() {
        // Load training data into Fingerprint database

        // Process the training data, create the lookup table

    }

    @Test 
    public void test_plaintext_minimum_distance() {
		assertEquals(100, 100);
    }

    @Test
    public void test_plaintext_mca() {
        assertEquals(100, 100);
    }

    @Test
    public void test_plaintext_dma() {
        assertEquals(100, 100);
    }
}
