package edu.fiu.adwise.fingerprint_tests;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import edu.fiu.adwise.fingerprint_localization.distance_computation.LOCALIZATION_SCHEME;
import edu.fiu.adwise.fingerprint_localization.structs.LocalizationResult;
import edu.fiu.adwise.fingerprint_localization.structs.SendLocalizationData;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKKeyPairGenerator;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKOperations;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.misc.HomomorphicException;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierCipher;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierKeyPairGenerator;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.socialistmillionaire.bob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class mock_localize_client implements Runnable {
    private final int port;
    public Double [] coordinates = new Double[2];
    private static final Logger logger = LogManager.getLogger(mock_localize_client.class);
    private final LOCALIZATION_SCHEME LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.PLAIN_MIN;
    private final SendLocalizationData transmission;
    private final List<LocalizationResult> localize_results = new ArrayList<>();
    private final int k = 2;

    public mock_localize_client(SendLocalizationData transmission, int port) {
        this.transmission = transmission;
        this.port = port;
    }

    public void run () {
        try (
                Socket clientSocket = new Socket("127.0.0.1", port);
                ObjectOutputStream toServer = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream fromServer = new ObjectInputStream(clientSocket.getInputStream());
                ) {

            switch(LOCALIZATIONSCHEME) {
                case PLAIN_MIN:
                case PLAIN_DMA:
                case PLAIN_MCA:
                case PAILLIER_DMA:
                case PAILLIER_MCA:
                case PAILLIER_MIN:
                case DGK_MIN:
                case DGK_MCA:
                case DGK_DMA:
                    localize(clientSocket, fromServer, toServer);
                    break;
                default:
                    break;
            }
        }
        catch (ClassCastException | ClassNotFoundException | IOException | HomomorphicException e) {
            throw new RuntimeException(e);
        }
    }

    private void localize(
            Socket clientSocket,
            ObjectInputStream fromServer,
            ObjectOutputStream toServer
    ) throws IOException, ClassNotFoundException, HomomorphicException {
        bob andrew;
        BigInteger[] location;
        BigInteger divisor;
        Object in;

        toServer.writeObject(transmission);
        toServer.flush();

        // DGK Key Pair
        DGKKeyPairGenerator pa = new DGKKeyPairGenerator();
        int KEY_SIZE = 2048;
        pa.initialize(KEY_SIZE, null);
        KeyPair dgk = pa.generateKeyPair();

        PaillierKeyPairGenerator p = new PaillierKeyPairGenerator();
        p.initialize(KEY_SIZE, null);
        KeyPair paillier = p.generateKeyPair();

        andrew = new bob(dgk, paillier);
        andrew.set_socket(clientSocket);

        switch(LOCALIZATIONSCHEME) {
            case PLAIN_MIN:
            case PLAIN_MCA:
            case PLAIN_DMA:
                in = fromServer.readObject();
                if(transmission.isREU2017) {
                    if (in instanceof Double[]) {
                        coordinates = (Double[]) in;
                    } else {
                        logger.warn("INVALID OBJECT: {}", in.getClass());
                    }
                } else {
                    if (in instanceof ArrayList<?>) {
                        for (Object o: (ArrayList<?>) in) {
                            if(o instanceof LocalizationResult) {
                                localize_results.add((LocalizationResult) o);
                            }
                        }
                    }
                    else {
                        logger.fatal("ERROR WRONG OBJECT IN PLAINTEXT 2015/2017! {}", in.getClass());
                    }
                }
                break;

            case DGK_MIN:
            case DGK_MCA:
            case DGK_DMA:
                if(transmission.isREU2017) {
                    // bob is spawned
                    andrew.setDGKMode(true);
                    // Sort to get the Minimum value OR K-Minimum
                    andrew.sort();
                    in = fromServer.readObject();
                    if (LOCALIZATIONSCHEME == LOCALIZATION_SCHEME.DGK_MIN) {
                        if (in instanceof BigInteger[]) {
                            location = (BigInteger []) in;
                            coordinates[0] = (double) DGKOperations.decrypt(location[0], (DGKPrivateKey) dgk.getPrivate());
                            coordinates[1] = (double) DGKOperations.decrypt(location[1], (DGKPrivateKey) dgk.getPrivate());
                        }
                        else {
                            logger.fatal("INVALID OBJECT IN DGK_MIN: {}", in.getClass());
                        }
                    }
                    else {
                        // If DMA, divide all matches
                        divisor = BigInteger.valueOf(DGKOperations.decrypt((BigInteger) in, (DGKPrivateKey) dgk.getPrivate()));
                        toServer.writeObject(divisor);
                        toServer.flush();

                        // Bob must stay alive to divide...
                        for (int i = 0; i < k; i++) {
                            andrew.division(divisor.longValue() * (k - 1));
                        }

                        // Now you can get your location
                        in = fromServer.readObject();
                        if (in instanceof BigInteger[]) {
                            location = (BigInteger[]) in;
                            // Divide by the factor of both server/phone
                            coordinates[0] = (double) DGKOperations.decrypt(location[0], (DGKPrivateKey) dgk.getPrivate());
                            coordinates[1] = (double) DGKOperations.decrypt(location[1], (DGKPrivateKey) dgk.getPrivate());
                        }
                    }
                } else {
                    // REU 2015 DGK Code
                    in = fromServer.readObject();
                    logger.info("DGK REU 2015");
                    if (in instanceof ArrayList<?>) {
                        for (Object o: (ArrayList<?>) in) {
                            logger.info("OBJECT FOUND");
                            if(o instanceof LocalizationResult) {
                                localize_results.add((LocalizationResult) o);
                            }
                        }
                    } else {
                        logger.fatal("Output Loop: INVALID OBJECT SENT: {}", in.getClass());
                    }
                }
                break;
            case PAILLIER_MIN:
            case PAILLIER_MCA:
            case PAILLIER_DMA:
                if(transmission.isREU2017) {
                    andrew.setDGKMode(false);
                    andrew.sort();
                    in = fromServer.readObject();
                    if(LOCALIZATIONSCHEME == LOCALIZATION_SCHEME.PAILLIER_MIN) {
                        if (in instanceof BigInteger []) {
                            // Will always be DGK encrypted!
                            location = (BigInteger[]) in;
                            coordinates[0] = PaillierCipher.decrypt(location[0], (PaillierPrivateKey) paillier.getPrivate()).doubleValue();
                            coordinates[1] = PaillierCipher.decrypt(location[1], (PaillierPrivateKey) paillier.getPrivate()).doubleValue();
                        } else {
                            logger.fatal("ERROR, INVALID OBJECT {}", in.getClass());
                        }
                    }
                    else {
                        divisor = PaillierCipher.decrypt((BigInteger) in, (PaillierPrivateKey) paillier.getPrivate());
                        toServer.writeObject(divisor);
                        toServer.flush();

                        for (int i = 0; i < k; i++) {
                            andrew.division(divisor.longValue() * (k - 1));
                        }

                        // Now you can get your location
                        in = fromServer.readObject();
                        if (in instanceof BigInteger[]) {
                            location = (BigInteger[]) in;
                            // Decrypt and Divide by the factor of both server/phone
                            coordinates[0] = PaillierCipher.decrypt(location[0], (PaillierPrivateKey) paillier.getPrivate()).doubleValue();
                            coordinates[1] = PaillierCipher.decrypt(location[1], (PaillierPrivateKey) paillier.getPrivate()).doubleValue();
                        }
                    }
                }
                else {
                    // REU 2015
                    in = fromServer.readObject();
                    if(in instanceof ArrayList<?>) {
                        for (Object o: (ArrayList<?>) in) {
                            if(o instanceof LocalizationResult) {
                                localize_results.add((LocalizationResult) o);
                            } else {
                                throw new IllegalArgumentException("EXPECTED LOCALIZATION RESULT");
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("INVALID OBJECT! " + in.getClass());
                    }
                }
        }
    }
}
