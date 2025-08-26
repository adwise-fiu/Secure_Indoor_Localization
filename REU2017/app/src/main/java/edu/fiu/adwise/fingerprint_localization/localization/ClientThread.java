package edu.fiu.adwise.fingerprint_localization.localization;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyPair;
import java.util.ArrayList;

import edu.fiu.adwise.fingerprint_localization.structs.SendLocalizationData;
import edu.fiu.adwise.fingerprint_localization.structs.LocalizationResult;
import edu.fiu.adwise.fingerprint_localization.structs.SendTrainingData;
import edu.fiu.adwise.homomorphic_encryption.misc.HomomorphicException;
import edu.fiu.adwise.fingerprint_localization.ui.MainActivity;
import edu.fiu.adwise.fingerprint_localization.ui.TrainActivity;


import static edu.fiu.adwise.fingerprint_localization.ui.MainActivity.SQLDatabase;
import static edu.fiu.adwise.fingerprint_localization.ui.MainActivity.portNumber;
import edu.fiu.adwise.fingerprint_localization.distance_computation.LOCALIZATION_SCHEME;

import edu.fiu.adwise.homomorphic_encryption.dgk.DGKOperations;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPublicKey;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierCipher;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPublicKey;
import edu.fiu.adwise.homomorphic_encryption.socialistmillionaire.bob;

public class ClientThread implements Runnable {
    private final static String TAG = "CLIENT_THREAD";
    //Pass Data back to class by reference...
    private background findMe;
    private TrainActivity trainMe;
    private background getColumns;
    private SendTrainingData sendTraining;
    private SendLocalizationData transmission;
    // Have all Keys in case comparison is needed!!
    private final DGKPublicKey pubKey = KeyMaster.DGKpk;
    private final DGKPrivateKey privKey = KeyMaster.DGKsk;
    private final PaillierPublicKey pk = KeyMaster.pk;
    private final PaillierPrivateKey sk = KeyMaster.sk;

    private final LOCALIZATION_SCHEME LOCALIZATIONSCHEME;
    // Get all currently trained points
    public ClientThread(TrainActivity trainActivity) {
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(-3);
        this.trainMe = trainActivity;
    }

    /*
    Called from MainActivity
    Purpose: Force mySQL Database to process data from
    training data.
     */
    public ClientThread() {
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(-2);
    }

    ClientThread(background needMACs) {
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(-1);
        this.getColumns = needMACs;
    }

    // Send Training Data
    public ClientThread (SendTrainingData in) {
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(0);
        this.sendTraining = in;
    }

    // For Localization
    ClientThread (SendLocalizationData input, background search, int local) {
        this.findMe = search;
        this.transmission = input;
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(local);
    }

    //===============================SOCKET METHODS/RUN THREAD============================================

    public void run () {
        Object in;
        try (
                Socket clientSocket = new Socket(SQLDatabase, portNumber);
                ObjectOutputStream toServer = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream fromServer = new ObjectInputStream(clientSocket.getInputStream())
                ) {
            Log.d(TAG, "I/O Streams set!");

            switch(LOCALIZATIONSCHEME) {
                case GETXY:
                    toServer.writeObject("Acquire all current training points");
                    toServer.flush();

                    // Send the Map Name as well
                    toServer.writeObject(KeyMaster.map_name);
                    toServer.flush();

                    in = fromServer.readObject();
                    if(in instanceof Double []) {
                        trainMe.existingX = (Double []) in;
                    }
                    else {
                        Log.d(TAG, "Data Type: -3, DIDN'T GET DOUBLE X []");
                    }

                    in = fromServer.readObject();
                    if(in instanceof Double[]) {
                        trainMe.existingY = (Double []) in;
                    }
                    else {
                        Log.d(TAG, "Data Type: -3, DIDN'T GET DOUBLE Y []");
                    }
                    break;

                /*
                Input: Command to force Database to compute Lookup Table
                Return: NOTHING. Kill Thread after
                 */
                case PROCESS:
                    toServer.writeObject("Process LUT");
                    toServer.flush();
                    if (fromServer.readBoolean()) {
                        Log.d(TAG, "Successfully Processed Lookup Table!");
                        MainActivity.process_good.show();
                    }
                    else {
                        Log.d(TAG, "Error Processing Lookup Tables!");
                        MainActivity.process_bad.show();
                    }
                    break;
                case GET_COLUMN:
                    toServer.writeObject("Get Lookup Columns");
                    // Send the Map with all (x, y)
                    toServer.writeObject(KeyMaster.map_name);
                    toServer.flush();

                    in = fromServer.readObject();
                    if (in instanceof String []) {
                        getColumns.CommonMAC = (String[]) in;
                    }
                    else {
                        Log.d(TAG, "INVALID COLUMN RECEIVED! " + in.getClass());
                    }
                    break;
                case TRAIN:
                    toServer.writeObject(sendTraining);
                    toServer.flush();
                    //Wait to get confirmation that the data successfully inserted...
                    if (fromServer.readBoolean()) {
                        MainActivity.good_train.show();
                    }
                    else {
                        MainActivity.bad_train.show();
                    }
                    break;
                case PLAIN_MIN:
                case PLAIN_DMA:
                case PLAIN_MCA:
                case PAILLIER_DMA:
                case PAILLIER_MCA:
                case PAILLIER_MIN:
                case DGK_MIN:
                case DGK_MCA:
                case DGK_DMA:
                    localize(clientSocket, toServer, fromServer);
                    break;
                default:
                    Log.d(TAG, "Error at Thread run: No Valid Object was sent here");
                    break;
            }
        }
        catch (ClassCastException | ClassNotFoundException cce) {
            Log.e(TAG, "Full exception details: ", cce);
        }
        catch (IOException ioe) {
            Log.d(TAG,"CHECK IF YOU ARE CONNECTED TO WI-FI (Most Common Issue)");
            Log.d(TAG, "MAKE SURE YOU HAVE RIGHT IP ADDRESS!!!");
            Log.d(TAG, "IF YOU ARE STILL TIMING OUT, IT IS YOUR FIREWALL!");
            Log.e(TAG, "Full exception details: ", ioe);
        } catch (HomomorphicException e) {
            throw new RuntimeException(e);
        }
    }

    private void localize(
            Socket clientSocket,
            ObjectOutputStream toServer,
            ObjectInputStream fromServer
    ) throws IOException, ClassNotFoundException, HomomorphicException {
        bob andrew;
        BigInteger [] location;
        BigInteger divisor;
        Object in;

        toServer.writeObject(transmission);
        toServer.flush();

        // Server will want to have an Alice/Bob instance ready just in case
        andrew = new bob(new KeyPair(pk, sk), new KeyPair(pubKey, privKey));
        andrew.set_socket(clientSocket);

        switch(LOCALIZATIONSCHEME) {
            case PLAIN_MIN:
            case PLAIN_MCA:
            case PLAIN_DMA:
                in = fromServer.readObject();
                if(transmission.isREU2017) {
                    if (in instanceof Double[]) {
                        findMe.coordinates = (Double[]) in;
                    } else {
                        Log.d(TAG, "INVALID OBJECT: " + in.getClass());
                    }
                } else {
                    if (in instanceof ArrayList<?>) {
                        for (Object o: (ArrayList<?>) in) {
                            if(o instanceof LocalizationResult) {
                                findMe.fromServer.add((LocalizationResult) o);
                            }
                        }
                    }
                    else {
                        Log.d(TAG, "ERROR WRONG OBJECT IN PLAINTEXT 2015/2017! " + in.getClass());
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
                            findMe.coordinates[0] = (double) DGKOperations.decrypt(location[0], privKey);
                            findMe.coordinates[1] = (double) DGKOperations.decrypt(location[1], privKey);
                        }
                        else {
                            Log.d(TAG, "INVALID OBJECT IN DGK_MIN: " + in.getClass());
                        }
                    }
                    else {
                        // If DMA, divide all matches
                        divisor = BigInteger.valueOf(DGKOperations.decrypt((BigInteger) in, privKey));
                        toServer.writeObject(divisor);
                        toServer.flush();

                        // Bob must stay alive to divide...
                        for (int i = 0; i < MainActivity.k; i++) {
                            andrew.division(divisor.longValue() * (MainActivity.k - 1));
                        }

                        // Now you can get your location
                        in = fromServer.readObject();
                        if (in instanceof BigInteger[]) {
                            location = (BigInteger[]) in;
                            // Divide by the factor of both server/phone
                            findMe.coordinates[0] = (double) DGKOperations.decrypt(location[0], privKey);
                            findMe.coordinates[1] = (double) DGKOperations.decrypt(location[1], privKey);
                            findMe.coordinates[0] = findMe.coordinates[0]/MainActivity.FACTOR;
                            findMe.coordinates[1] = findMe.coordinates[0]/MainActivity.FACTOR;
                        }
                    }
                } else {
                    // REU 2015 DGK Code
                    in = fromServer.readObject();
                    Log.d(TAG, "DGK REU 2015");
                    if (in instanceof ArrayList<?>) {
                        for (Object o: (ArrayList<?>) in) {
                            Log.d(TAG, "OBJECT FOUND");
                            if(o instanceof LocalizationResult) {
                                findMe.fromServer.add((LocalizationResult) o);
                            }
                        }
                    } else {
                        Log.d(TAG, "Output Loop: INVALID OBJECT SENT: " + in.getClass());
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
                            findMe.coordinates[0] = PaillierCipher.decrypt(location[0], sk).doubleValue();
                            findMe.coordinates[1] = PaillierCipher.decrypt(location[1], sk).doubleValue();
                        } else {
                            Log.d(TAG, "ERROR, INVALID OBJECT " + in.getClass());
                        }
                    }
                    else {
                        divisor = PaillierCipher.decrypt((BigInteger) in, sk);
                        toServer.writeObject(divisor);
                        toServer.flush();

                        for (int i = 0; i < MainActivity.k; i++) {
                            andrew.division(divisor.longValue() * (MainActivity.k - 1));
                        }

                        // Now you can get your location
                        in = fromServer.readObject();
                        if (in instanceof BigInteger[]) {
                            location = (BigInteger[]) in;
                            // Decrypt and Divide by the factor of both server/phone
                            findMe.coordinates[0] = PaillierCipher.decrypt(location[0], sk).doubleValue()/MainActivity.FACTOR;
                            findMe.coordinates[1] = PaillierCipher.decrypt(location[1], sk).doubleValue()/MainActivity.FACTOR;
                        }
                    }
                }
                else {
                    // REU 2015
                    in = fromServer.readObject();
                    if(in instanceof ArrayList<?>) {
                        for (Object o: (ArrayList<?>) in) {
                            if(o instanceof LocalizationResult) {
                                findMe.fromServer .add((LocalizationResult) o);
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