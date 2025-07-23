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

/**
 * Handles client requests for Wi-Fi fingerprint localization, training data submission,
 * and map file operations in a multi-threaded server environment.
 * <p>
 * This class processes localization queries using various algorithms (Plain, DGK, Paillier),
 * manages training data, and supports map file upload/download. It communicates with clients
 * over sockets and interacts with the database and lookup tables.
 * </p>
 *
 * <ul>
 *   <li>Processes localization requests using homomorphic encryption or plaintext algorithms.</li>
 *   <li>Handles training data submission for updating the fingerprint database.</li>
 *   <li>Supports map file upload and download with security checks.</li>
 *   <li>Manages client-server communication using object streams.</li>
 *   <li>Logs operations and errors using Log4j.</li>
 * </ul>
 *
 * <a href="http://tutorials.jenkov.com/java-multithreaded-servers/multithreaded-server.html">Multi-threaded</a>
 * @author Andrew Quijano
 * @since 2017-07-06
 */
public class LocalizationThread implements Runnable {
	/** Logger for thread operations and errors. */
	private static final Logger logger = LogManager.getLogger(LocalizationThread.class);

	/** Paillier public key for encryption operations. */
	public PaillierPublicKey pk = null;

	/** DGK public key for encryption operations. */
	public DGKPublicKey pubKey = null;

	/** ElGamal public key for encryption operations. */
	public ElGamalPublicKey e_pk = null;

    /** Nanosecond to second conversion constant. */
	private final static long BILLION = 1000000000;

	/** Socket for client communication. */
	protected Socket clientSocket;

    /** Base directory for file operations. */
	private String BASEDIR;

	/** List of localization results to send to a client. */
	private List<LocalizationResult> replyToClient = new ArrayList<>();

    public LocalizationThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

	/**
	 * Main thread execution method. Processes client requests for localization,
	 * training data, and map file operations. Handles communication, computation,
	 * and error logging.
	 */
	public void run() {
		try {
			BASEDIR = new File(".").getCanonicalPath() + File.separator;
		} catch (IOException e) {
			logger.fatal("Error obtaining BASEDIR: {}", e.getMessage());
		}

		try (
				ObjectInputStream fromClient = new ObjectInputStream(clientSocket.getInputStream());
				ObjectOutputStream toClient = new ObjectOutputStream(clientSocket.getOutputStream())
		) {
			long startTime = System.nanoTime();
			Object x = fromClient.readObject();

			if (x instanceof String command) {
                if (command.equalsIgnoreCase("UNDO")) {
					logger.info("Command acquired: UNDO");
					Double [] coordinate = (Double []) fromClient.readObject();
					String map = (String) fromClient.readObject();
					String device = (String) fromClient.readObject();
                    logger.info("{} {} {} {}", coordinate[0], coordinate[1], map, device);
					toClient.writeBoolean(LocalizationLUT.undo(coordinate, map, device));
				} else if (command.equals("RESET")) {
					logger.info("Command acquired: RESET");
					toClient.writeBoolean(LocalizationLUT.reset());
					logger.info("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");
					server.preprocessed = false;
					return;
				} else if (command.equals("Acquire all current training points")) {
					logger.info("Command acquired: Obtain all Fingerprints!");
					String Map = (String) fromClient.readObject();
					toClient.writeObject(LocalizationLUT.getX(Map));
					toClient.writeObject(LocalizationLUT.getY(Map));
				}
				else if (command.equals("Process LUT")) {
					logger.info("Command acquired: Process Lookup Table");
					toClient.writeBoolean(process());
					toClient.flush();
				} else if(command.equals("Get Lookup Columns")) {
					logger.info("Command acquired: Get Lookup MAC Addresses!");
					String map = (String) fromClient.readObject();
					toClient.writeObject(LocalizationLUT.getColumnMAC(map));
					toClient.flush();
				} else if(command.equals("GET")) {
					// AddMapActivity, download Bitmap and save to storage
					String map_name = (String) fromClient.readObject();
					File file = new File(BASEDIR + map_name);
					try (FileInputStream fileInputStream = new FileInputStream(file)) {
						if (file.getCanonicalPath().startsWith(BASEDIR)) {
						    // process file
							byte[] map = new byte[(int) file.length()];
							// Convert a file into an array of bytes
							if (fileInputStream.read(map) != file.length()) {
								logger.warn("File read size mismatch for: {}", map_name);
							}

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
                        logger.warn("File not found: {}", map_name);
						toClient.writeInt(0);
						toClient.flush();
					}
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
						toClient.flush();
                        logger.warn("Error uploading map: {}", e.getMessage());
					}
					return;
				} else {
                    logger.info("INVALID Command acquired: {}", command);
				}
			} else if(x instanceof SendTrainingData trainDatabase) {
				// Train the Database
				// dataType = 0
                logger.info("TRAINING DATA RECEIVED...");
				toClient.writeBoolean(LocalizationLUT.submitTrainingData(trainDatabase));
			} else if (x instanceof SendLocalizationData transmission) {
                DistancePaillier paillierLocalization;
				DistancePlain PlaintextLocalization;
				DistanceDGK DGKLocalization;

				boolean isREU2017 = transmission.isREU2017;
				pubKey = transmission.pubKey;
				pk = transmission.pk;
				e_pk = transmission.e_pk;

                alice niu = new alice(clientSocket);
				niu.setDGKMode(true);

				// Read from Database
				switch (transmission.LOCALIZATION_SCHEME) {
					case PLAIN_MIN:
						PlaintextLocalization = new DistancePlain(transmission);
						replyToClient = PlaintextLocalization.MinimumDistance(null, isREU2017);
						if (isREU2017) {
							toClient.writeObject(PlaintextLocalization.location);
						} else {
							toClient.writeObject(replyToClient);
						}
						toClient.flush();
						break;
					case DGK_MIN:
						DGKLocalization = new DistanceDGK(transmission);
						replyToClient = DGKLocalization.MinimumDistance(niu, isREU2017);
						if (isREU2017) {
							toClient.writeObject(DGKLocalization.encryptedLocation);
						} else {
							toClient.writeObject(replyToClient);
						}
						toClient.flush();
						break;

					case PAILLIER_MIN:
						paillierLocalization = new DistancePaillier(transmission);
						niu.setDGKMode(false);
						replyToClient = paillierLocalization.MinimumDistance(niu, isREU2017);

						if (isREU2017) {
							toClient.writeObject(paillierLocalization.encryptedLocation);
						} else {
							toClient.writeObject(replyToClient);
						}
						toClient.flush();
						break;

					case PLAIN_MCA:
						PlaintextLocalization = new DistancePlain(transmission);
						replyToClient = PlaintextLocalization.MissConstantAlgorithm();

						if (isREU2017) {
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
							toClient.writeObject(DGKLocalization.Phase3(niu));
						} else {
							toClient.writeObject(replyToClient);
						}
						toClient.flush();
						break;
					case PAILLIER_MCA:
						niu.setDGKMode(false);
						paillierLocalization = new DistancePaillier(transmission);
						replyToClient = paillierLocalization.MissConstantAlgorithm();
						if (isREU2017) {
							toClient.writeObject(paillierLocalization.Phase3(niu));
						} else {
							toClient.writeObject(replyToClient);
						}
						toClient.flush();
						break;

					case PLAIN_DMA:
						PlaintextLocalization = new DistancePlain(transmission);
						replyToClient = PlaintextLocalization.DynamicMatchingAlgorithm();
						if (isREU2017) {
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
							toClient.writeObject(DGKLocalization.Phase3(niu));
						} else {
							toClient.writeObject(replyToClient);
						}
						toClient.flush();
						break;
					case PAILLIER_DMA:
						niu.setDGKMode(false);
						paillierLocalization = new DistancePaillier(transmission);
						replyToClient = paillierLocalization.DynamicMatchingAlgorithm();
						if (isREU2017) {
							toClient.writeObject(paillierLocalization.Phase3(niu));
						} else {
							toClient.writeObject(replyToClient);
						}
						toClient.flush();
						break;
					default:
						System.err.println("INVALID LOCALIZATION SCHEME!");
						break;
				}
				// Log completion time
				if (replyToClient.isEmpty()) {
					logger.info("No results to send back to client.");
				} else {
					logger.info("Sent {} results back to client.", replyToClient.size());
				}
			}
			else {
				logger.info("INVALID OBJECT: {}! Closing...", x.getClass().getName());
			}
            logger.info("Computation completed, it took {} seconds", (System.nanoTime() - startTime) / BILLION);
        } catch(IOException | SQLException | ClassNotFoundException | IllegalArgumentException | HomomorphicException e) {
            logger.fatal("Error in Localization Thread: {}", e.getMessage());
		}
		finally {
			try {
				if (clientSocket != null && !clientSocket.isClosed()) {
					clientSocket.close();
				}
			} catch (IOException e) {
				logger.warn("Error closing client socket: {}", e.getMessage());
			}
		}
    }

	/**
	 * Processes the creation and update of the lookup table for localization.
	 * Ensures preprocessing is only performed once unless reset.
	 *
	 * @return true if lookup table was successfully created and updated, false otherwise
	 */
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
				logger.info("Successfully created Lookup table!");
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
}