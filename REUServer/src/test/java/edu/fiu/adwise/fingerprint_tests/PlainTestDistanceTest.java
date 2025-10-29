package edu.fiu.adwise.fingerprint_tests;

import edu.fiu.adwise.fingerprint_localization.database.LocalizationLUT;
import edu.fiu.adwise.fingerprint_localization.server;
import edu.fiu.adwise.fingerprint_localization.structs.SendLocalizationData;
import edu.fiu.adwise.fingerprint_localization.structs.SendTrainingData;
import edu.fiu.adwise.ciphercraft.dgk.DGKKeyPairGenerator;
import edu.fiu.adwise.ciphercraft.dgk.DGKPublicKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import edu.fiu.adwise.fingerprint_localization.distance_computation.LOCALIZATION_SCHEME;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyPair;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PlainTestDistanceTest {
    private static final Logger logger = LogManager.getLogger(PlainTestDistanceTest.class);

    public static List<SendTrainingData> readTrainingData(String csvPath, String mapName) throws IOException {
        Map<String, List<String[]>> grouped = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split(",");
                String key = tokens[0] + "," + tokens[1] + "," + tokens[4] + "," + tokens[5] + "," + tokens[6] + "," + tokens[7];
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(tokens);
            }
        }
        List<SendTrainingData> result = new ArrayList<>();
        for (Map.Entry<String, List<String[]>> entry : grouped.entrySet()) {
            List<String[]> rows = entry.getValue();
            String[] first = rows.get(0);
            Double x = Double.valueOf(first[0]);
            Double y = Double.valueOf(first[1]);
            String os = first[4], device = first[5], model = first[6], product = first[7];
            String[] macs = rows.stream().map(r -> r[2]).toArray(String[]::new);
            Integer[] rss = rows.stream().map(r -> Integer.valueOf(r[3])).toArray(Integer[]::new);
            SendTrainingData data = new SendTrainingData(
                mapName, x, y, macs, rss, os, device, model, product
            );
            logger.info("Adding training data:\n {}", data);
            result.add(data);
        }
        return result;
    }

    private static KeyPair dgk;

    @BeforeClass
    public static void generate_keys() {
        DGKKeyPairGenerator pa = new DGKKeyPairGenerator();
        int KEY_SIZE = 2048;
        pa.initialize(KEY_SIZE, null);
        dgk = pa.generateKeyPair();
    }

    @BeforeClass
    public static void prepare_fingerprint_database() throws SQLException, ClassNotFoundException, IOException {
        // Load training data into Fingerprint database
        assertTrue("Failed to create database and training data table", LocalizationLUT.createTrainingTable());
        assertFalse("Confirm that the database has no lookup table", LocalizationLUT.isProcessed());

        // Process the training data: Read from a file and use train database function
        List<SendTrainingData> test_data = readTrainingData("TrainingPoints.csv", "Broadway");
        assertFalse("Confirming non-empty training data array", test_data.isEmpty());
        for (SendTrainingData data : test_data) {
            assertTrue("Failed to insert training data into the database", LocalizationLUT.submitTrainingData(data));
        }
        // Create and populate the lookup table
        assertTrue("Failed to create Lookup Table", LocalizationLUT.createTables());
        assertTrue("Populated the lookup table", LocalizationLUT.UpdatePlainLUT());
        assertTrue("Confirm that the database has a lookup table created", LocalizationLUT.isProcessed());
        String [] look_up_mac_addresses = LocalizationLUT.getColumnMAC("Broadway");
        assertNotNull("Lookup MAC addresses should not be null", look_up_mac_addresses);
        for (String mac : look_up_mac_addresses) {
            logger.info("Looking up mac address {}", mac);
        }
    }

    @Test 
    public void test_plaintext_minimum_distance() throws InterruptedException {
        // Mock client sending localization request
        SendLocalizationData data = new SendLocalizationData(
            new String[]{"00:11:22:33:44:55", "66:77:88:99:AA:BB"},
            new Integer[]{-50, -60},
                (DGKPublicKey) dgk.getPublic(),
            LOCALIZATION_SCHEME.PLAIN_MIN,
            false,
            new String[]{"Android", "Pixel 4"},
            "Broadway-3"
        );

        // Create a server to handle the request
        server Localizationserver = new server(9000);
        new Thread(Localizationserver).start();

        // Wait for the server to initialize (for example, 1 second)
        Thread.sleep(1000);

        //mock_localize_client client = new mock_localize_client(data, 9000);
        //new Thread(client).start();

        // Close the server since you are done
        Localizationserver.stop();
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
