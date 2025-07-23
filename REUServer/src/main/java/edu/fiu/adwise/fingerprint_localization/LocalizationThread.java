package edu.fiu.adwise.fingerprint_localization;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import edu.fiu.adwise.fingerprint_localization.distance_computation.DistanceDGK;
import edu.fiu.adwise.fingerprint_localization.distance_computation.DistancePaillier;
import edu.fiu.adwise.fingerprint_localization.distance_computation.DistancePlain;

import edu.fiu.adwise.fingerprint_localization.structs.LocalizationResult;
import edu.fiu.adwise.fingerprint_localization.structs.SendLocalizationData;
import edu.fiu.adwise.fingerprint_localization.structs.SendTrainingData;
import edu.fiu.adwise.fingerprint_localization.database.LocalizationLUT;
import edu.fiu.adwise.fingerprint_localization.distance_computation.Distance;

import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPublicKey;
import edu.fiu.adwise.homomorphic_encryption.elgamal.ElGamalPublicKey;
import edu.fiu.adwise.homomorphic_encryption.misc.HomomorphicException;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPublicKey;
import edu.fiu.adwise.homomorphic_encryption.socialistmillionaire.alice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * Uses Multi-thread code from:
 * http://tutorials.jenkov.com/java-multithreaded-servers/multithreaded-server.html
 */

public class LocalizationThread implements Runnable {
	private static final Logger logger = LogManager.getLogger(LocalizationThread.class);

    // Paillier and DGK Public Keys
	public PaillierPublicKey pk = null;
	public DGKPublicKey pubKey = null;
	public ElGamalPublicKey e_pk = null;
	
	// Communication
    private SendLocalizationData transmission;	// Data from Android Phone to Localize
    private SendTrainingData trainDatabase;	// For Training  Data
 
    // I am measuring in nanoseconds, this converts back to seconds...
    private final static long BILLION = 1000000000;
    
    protected Socket clientSocket;

    // I/O streams
	private ObjectInputStream fromClient = null;
	private ObjectOutputStream toClient = null;

    //Distance Computations
	private DistancePlain PlaintextLocalization = null;
	private DistanceDGK DGKLocalization = null;
	private DistancePaillier PaillierLocalization = null;

	// For File safety
	private String BASEDIR;

	//To either return Encrypted Values...or Send encrypted distances back...
	private List<LocalizationResult> replyToClient = new ArrayList<>();
	private alice Niu = null;
	
    public LocalizationThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

	public void run() {
        try {
			BASEDIR = new File(".").getCanonicalPath() + "/";
		} catch (IOException e) {
            logger.fatal("Error obtaining BASEDIR: {}", e.getMessage());
		}
        
		try {
			fromClient= new ObjectInputStream(clientSocket.getInputStream());
			toClient = new ObjectOutputStream(clientSocket.getOutputStream());

			long startTime = System.nanoTime();//Start Timer
			Object x = fromClient.readObject();

			if (x instanceof String command) {
                if (command.equalsIgnoreCase("UNDO")) {
					logger.info("Command acquired: UNDO");
					Double [] coordinate = (Double []) fromClient.readObject();
					String map = (String) fromClient.readObject();
					String device = (String) fromClient.readObject();
					logger.info(coordinate[0] + " " + coordinate[1] + " " + map + " " + device);
					toClient.writeBoolean(LocalizationLUT.undo(coordinate, map, device));
					logger.info("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				} else if (command.equals("RESET")) {
					logger.info("Command acquired: RESET");
					toClient.writeBoolean(LocalizationLUT.reset());
					logger.info("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");
					server.preprocessed = false;
					
					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				} else if (command.equals("Acquire all current training points")) {
					logger.info("Command acquired: Obtain all Fingerprints!");
					String Map = (String) fromClient.readObject();
					toClient.writeObject(LocalizationLUT.getX(Map));
					toClient.writeObject(LocalizationLUT.getY(Map));
					logger.info("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				}
				else if (command.equals("Process LUT")) {
					logger.info("Command acquired: Process Lookup Table");
					toClient.writeBoolean(process());
					toClient.flush();
					logger.info("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

					// Flush and Close I/O streams and Socket
					this.closeClientConnection();		
					return;
				} else if(command.equals("Get Lookup Columns")) {
					logger.info("Command acquired: Get Lookup MAC Addresses!");
					String map = (String) fromClient.readObject();
					toClient.writeObject(LocalizationLUT.getColumnMAC(map));
					toClient.flush();
					logger.info("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				} else if(command.equals("GET")) {
					// AddMapActivity, download Bitmap and save to storage
					String map_name = (String) fromClient.readObject();
					File file = null;
					try {
						file = new File(BASEDIR + map_name);
						if (file.getCanonicalPath().startsWith(BASEDIR)) {
						    // process file
							byte[] map = new byte[(int) file.length()];

							// Convert file into array of bytes
							FileInputStream fileInputStream = new FileInputStream(file);
							fileInputStream.read(map);
							fileInputStream.close();

							// Send Size and bytes itself
							toClient.writeInt((int) file.length());
							toClient.flush();
							toClient.write(map);
							toClient.flush();
						} else {
							// Security Risk Attempt to traverse directory
							toClient.writeInt(0);
							toClient.flush();
						}
					} catch(FileNotFoundException e) {
						logger.warn("File not found: " + map_name);
						toClient.writeInt(0);
						toClient.flush();
					}
					
					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				} else if(command.equals("SET")) {
					// Get the correct Map back to the User
					try {
						String map_name = (String) fromClient.readObject();
						int map_size = fromClient.readInt();
						
						if (new File(map_name).getCanonicalPath().startsWith(BASEDIR)) {
							byte [] map = new byte[map_size];
							fromClient.readFully(map);
							
							BufferedImage image = ImageIO.read(new ByteArrayInputStream(map));
							ImageIO.write(image, "BMP", new File(map_name));
							toClient.writeBoolean(true);
                            logger.info("New Map: {} Successfully uploaded!", map_name);
						} else {
							toClient.writeBoolean(false);
						}
					} catch (Exception e) {
						toClient.writeBoolean(false);
                        logger.warn("Error uploading map: {}", e.getMessage());
					}
					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				} else {
                    logger.info("INVALID Command acquired: {}", command);
					logger.info("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");
					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				}
			} else if(x instanceof SendTrainingData) {
				// Train the Database
				// dataType = 0
				trainDatabase = (SendTrainingData) x;
				logger.info("TRAINING DATA RECEIVED...");
				toClient.writeBoolean(LocalizationLUT.submitTrainingData(trainDatabase));
				logger.info("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

				// Flush and Close I/O streams and Socket
				this.closeClientConnection();
				return;
			} else if (!(x instanceof SendLocalizationData)) {
                logger.info("INVALID OBJECT: {}! Closing...", x.getClass());
				logger.info("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");
				
				// Flush and Close I/O streams and Socket
				this.closeClientConnection();		
				return;
			}
		
			transmission = (SendLocalizationData) x;

			// Obtain Algorithm and keys
            boolean isREU2017 = transmission.isREU2017;
            // REU Variables
			pubKey = transmission.pubKey;
			pk = transmission.pk;
			e_pk = transmission.e_pk;

			Niu = new alice(clientSocket);
			Niu.setDGKMode(true);
			
			// Read from Database
			switch(transmission.LOCALIZATION_SCHEME) {
				case PLAIN_MIN:
					PlaintextLocalization = new DistancePlain(transmission);
					replyToClient = PlaintextLocalization.MinimumDistance(null, isREU2017);
					if(isREU2017) {
						toClient.writeObject(PlaintextLocalization.location);
					} else {
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;
				case DGK_MIN:
					DGKLocalization = new DistanceDGK(transmission);
					replyToClient = DGKLocalization.MinimumDistance(Niu, isREU2017);
					if (isREU2017) {
						toClient.writeObject(DGKLocalization.encryptedLocation);	
					} else {
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;	
					
				case PAILLIER_MIN:
					PaillierLocalization = new DistancePaillier(transmission);
					Niu.setDGKMode(false);
					replyToClient = PaillierLocalization.MinimumDistance(Niu, isREU2017);

					if (isREU2017) {
						toClient.writeObject(PaillierLocalization.encryptedLocation);
					} else {
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;

				case PLAIN_MCA:
					PlaintextLocalization = new DistancePlain(transmission);
					replyToClient = PlaintextLocalization.MissConstantAlgorithm();

					if(isREU2017) {
						toClient.writeObject(PlaintextLocalization.location);
					} else {
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;

				case DGK_MCA:
					DGKLocalization = new DistanceDGK(transmission);
					replyToClient = DGKLocalization.MissConstantAlgorithm();
					if (isREU2017) {
						toClient.writeObject(DGKLocalization.Phase3(Niu));
					} else {
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;	
				case PAILLIER_MCA:
					Niu.setDGKMode(false);
					PaillierLocalization = new DistancePaillier(transmission);
					replyToClient = PaillierLocalization.MissConstantAlgorithm();
					if (isREU2017) {
						toClient.writeObject(PaillierLocalization.Phase3(Niu));
					} else {
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;			 	
				 
				case PLAIN_DMA:
					PlaintextLocalization = new DistancePlain(transmission);
					replyToClient = PlaintextLocalization.DynamicMatchingAlgorithm();
					if(isREU2017) {
						toClient.writeObject(PlaintextLocalization.location);
					} else {
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;

				case DGK_DMA:
					DGKLocalization = new DistanceDGK(transmission);
					replyToClient = DGKLocalization.DynamicMatchingAlgorithm();
					if (isREU2017) {
						toClient.writeObject(DGKLocalization.Phase3(Niu));
					} else {
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;
				 	
				case PAILLIER_DMA:
					Niu.setDGKMode(false);
					PaillierLocalization = new DistancePaillier(transmission);
					replyToClient = PaillierLocalization.DynamicMatchingAlgorithm();
					if (isREU2017) {
						toClient.writeObject(PaillierLocalization.Phase3(Niu));
					} else {
						toClient.writeObject(replyToClient);	
					}
					toClient.flush();
					break;
				default:
					System.err.println("INVALID LOCALIZATION SCHEME!");
					break;
			}
            logger.info("Computation completed, it took {} seconds", (System.nanoTime() - startTime) / BILLION);
			this.closeClientConnection();
        } catch(IOException | SQLException | ClassNotFoundException | IllegalArgumentException | HomomorphicException e) {
            logger.fatal("Error in Localization Thread: {}", e.getMessage());
		}
    }
	
	public static boolean process() {
		if(!server.preprocessed) {
			if(LocalizationLUT.createTables()) {
				logger.info("Created new Lookup Table!");
			}
			else {
				logger.info("The table exists! Drop it first!");
				return false;
			}
			logger.info("Computing Lookup Table Data...");
			Distance.VECTOR_SIZE = LocalizationLUT.getVectorSize(Distance.FSF);
			logger.info("NEW VECTOR SIZE: " + Distance.VECTOR_SIZE);
			
			if(LocalizationLUT.UpdatePlainLUT()) {
				logger.info("Sucessfully created Lookup table!");
				server.preprocessed = true;
				return true;
			} else {
				logger.info("Failed to create Lookup table!");
				return false;
			}
		} else {
			logger.info("Denied to pre-process again. Reset First!");
			return false;
		}
	}

	private void closeClientConnection() throws IOException {
		toClient.close();
		fromClient.close();
		if (clientSocket != null && clientSocket.isConnected()) {
			clientSocket.close();	
		}
	}
}