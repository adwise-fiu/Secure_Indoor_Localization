package edu.fiu.adwise.fingerprint_localization;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import edu.fiu.adwise.fingerprint_localization.database.LocalizationLUT;
import edu.fiu.adwise.fingerprint_localization.database.MultiphoneLocalization;
import edu.fiu.adwise.fingerprint_localization.distance_computation.Distance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Multithreaded server for FIU Indoor Localization.
 * <p>
 * Listens for client connections, processes localization requests, and provides
 * a command-line interface for server management and database operations.
 * </p>
 * <p>
 * The server supports switching between single and multi-phone lookup tables,
 * database table creation, and various runtime configuration commands.
 * </p>
 * <p>
 * Based on code from:
 * <a href="http://tutorials.jenkov.com/java-multithreaded-servers/multithreaded-server.html"> multithreaded server</a>
 * </p>
 *
 * @author Andrew
 * @since 2017-07-06
 */
public class server implements Runnable {
	/** Port number for the server to listen on. */
	protected int serverPort = 9254;
	/** Server socket for accepting client connections. */
	protected ServerSocket serverSocket = null;
	/** Indicates if the server is stopped. */
	protected boolean isStopped = false;
	/** Reference to the running server thread. */
	protected Thread runningThread = null;
	/** Indicates if the lookup table has been preprocessed. */
	public static boolean preprocessed = false;
	/** Indicates if multi-phone lookup tables are enabled. */
	public static boolean multi_phone = false;
	/** Logger for server events and errors. */
	private static final Logger logger = LogManager.getLogger(server.class);

	/**
	 * Constructs a new server instance with the specified port.
	 *
	 * @param port the port number to listen on
	 */
	public server(int port) {
		serverPort = port;
	}

	/**
	 * Main server loop. Accepts client connections and starts a new thread for each.
	 * Handles server shutdown and socket management.
	 */
	public void run() {
		synchronized (this) {
			this.runningThread = Thread.currentThread();
		}
		openServerSocket();
		while (!isStopped()) {
			Socket clientSocket;
			try {
				clientSocket = this.serverSocket.accept();
			} catch (IOException e) {
				if (isStopped()) {
					logger.info("Server Stopped.");
					return;
				}
				throw new RuntimeException("Error accepting client connection", e);
			}
			new Thread(new LocalizationThread(clientSocket)).start();
		}
		logger.info("Server Stopped.");
	}

	/**
	 * Checks if the server is stopped.
	 *
	 * @return true if stopped, false otherwise
	 */
	private synchronized boolean isStopped() {
		return this.isStopped;
	}

	/**
	 * Stops the server and closes the server socket.
	 */
	public synchronized void stop() {
		this.isStopped = true;
		try {
			this.serverSocket.close();
		} catch (IOException e) {
			throw new RuntimeException("Error closing server", e);
		}
	}

	/**
	 * Opens the server socket on the configured port.
	 * Throws a runtime exception if the port cannot be opened.
	 */
	private void openServerSocket() {
		try {
			this.serverSocket = new ServerSocket(this.serverPort);
		} catch (IOException e) {
			throw new RuntimeException("Cannot open port " + this.serverPort, e);
		}
	}

	/**
	 * Entry point for the server application.
	 * <p>
	 * Loads database credentials from environment variables, checks and creates
	 * lookup tables, and provides a command-line interface for server management.
	 * </p>
	 *
	 * @param args optional port number as the first argument
	 */
	public static void main(String[] args) {
		int port = 9254;

		try {
			// Check if LUT exists
			server.preprocessed = LocalizationLUT.isProcessed();
			// If made, update vector size now!
			if (server.preprocessed) {
				Distance.VECTOR_SIZE = LocalizationLUT.getVectorSize(Distance.FSF);
				logger.info("NEW VECTOR SIZE: {}", Distance.VECTOR_SIZE);
			}

			// Custom Port if needed
			if (args.length == 1) {
				port = Integer.parseInt(args[0]);
				if (port <= 1024) {
					logger.fatal("Invalid Port! {} use value over 1024!", port);
					System.exit(1);
				} else if (port > 65535) {
					logger.fatal("Invalid Port! {} use value below 65535!", port);
					System.exit(1);
				}
			}
		} catch (ClassNotFoundException | SQLException e) {
			logger.fatal("Error loading database credentials or checking LUT: {}", e.getMessage());
			System.exit(1);
		} catch (NumberFormatException nfe) {
			logger.warn("Please enter a valid custom port number");
			System.exit(1);
		}

		server Localizationserver = new server(port);
		new Thread(Localizationserver).start();

		// Create the Training Table
		if (LocalizationLUT.createTrainingTable()) {
			logger.info("Created Database and Training Table!");
		} else {
			logger.info("Failed to create Training Table! It probably already exists!!!");
		}

		logger.info("==================FIU Indoor Localization Server Online=========================");

		// Command-line interface loop
		while (true) {
			try (Scanner inputReader = new Scanner(System.in)){
				String input = inputReader.nextLine();
				input = input.trim();
				String[] commands = input.split(" ");

				logger.info("Database preprocessed? {}", server.preprocessed);
				logger.info("Vector size: {}", Distance.VECTOR_SIZE);
				logger.info("APs in Training Data: {}", LocalizationLUT.getVectorSize(0.0));
				logger.info("N_F: {}", LocalizationLUT.getX("BWY_FL_03").length);
				logger.info("Current value of K is: {}", Distance.k);

				// Command handling
				if (commands[0].equalsIgnoreCase("clr")) {
					System.out.println("\033[2J\033[;H");
					System.out.flush();
				} else if (commands[0].equalsIgnoreCase("print")) {
					if (server.preprocessed) {
						LocalizationLUT.printTrainingData();
						if (server.multi_phone) {
							MultiphoneLocalization.printLUT();
						} else {
							LocalizationLUT.printLUT();
						}
					} else {
						logger.info("Lookup Tables not processed yet!");
					}
				} else if (commands[0].equals("frequency")) {
					HashMap<String, Integer> frequency_map = LocalizationLUT.getCommonMac();
					if (frequency_map == null) {
						logger.info("Frequencies not processed yet!");
						continue;
					}
					int idx = 1;
					// Get sorted
					Map<String, Integer> sorted_map = sortByComparator(frequency_map, true);
					for (String AP : frequency_map.keySet()) {
                        logger.info("{}, MAC: {} was detected {} times", idx, AP, sorted_map.get(AP));
						++idx;
					}
					toukey_summary(frequency_map.values().toArray(new Integer[0]));
				} else if (commands[0].equals("k")) {
					Distance.k = Integer.parseInt(commands[1]);
					logger.info("Updated value of K is: {}", Distance.k);
				} else if (commands[0].equalsIgnoreCase("test-FSF")) {
					double fsf = Double.parseDouble(commands[1]);
					if (fsf < 0 || fsf > 1) {
                        logger.info("Invalid FSF value! {}", fsf);
					} else {
                        logger.warn("Given FSF value: {} minimum AP match is: {}", fsf, LocalizationLUT.getVectorSize(fsf));
					}
				} else if (commands[0].equalsIgnoreCase("FSF")) {
					double fsf = Double.parseDouble(commands[1]);
					if (fsf < 0 || fsf > 1) {
						logger.info("Invalid FSF value to set! {}", fsf);
					} else {
						Distance.FSF = fsf;
						if (server.preprocessed) {
							logger.info("Please note for number of columns to change, you must re-build new Lookup table!");
						}
					}
				} else if (commands[0].equalsIgnoreCase("process")) {
					if (server.multi_phone) {
						if (LocalizationThread.multiprocess()) {
							logger.info("Preprocessing all Lookup Tables successful!");
						} else {
							logger.info("Preprocessing all Lookup Tables failed! (Single)");
						}
					} else {
						if (LocalizationThread.process()) {
							logger.info("Preprocessing one Lookup Table successful!");
						} else {
							logger.info("Preprocessing all Lookup Tables failed! (Multiple)");
						}
					}
				} else if (commands[0].equalsIgnoreCase("reset")) {
					if (LocalizationLUT.reset()) {
						logger.info("RESET SUCCESSFUL!");
						server.preprocessed = false;
					} else {
						logger.info("RESET FAILED!");
					}
				} else if (commands[0].equalsIgnoreCase("switch")) {
					if (server.multi_phone) {
						logger.info("Server is switched to 1 LUT");
						server.multi_phone = false;
					} else {
						logger.info("Server is switched to 1 LUT per Phone");
						server.multi_phone = true;
					}
				} else if (commands[0].equalsIgnoreCase("exit")) {
					break;
				}
			} catch (NumberFormatException nfe) {
				logger.info("Please enter a valid number!");
			} catch (ClassNotFoundException e) {
				logger.fatal("Error loading database driver: {}", e.getMessage());
			} catch (SQLException e) {
				logger.fatal("Error connecting to database: {}", e.getMessage());
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		logger.info("Stopping Localization Server...");
		Localizationserver.stop();
	}

	/**
	 * Sorts a HashMap by its values in ascending or descending order.
	 *
	 * @param unsortMap the unsorted map
	 * @param order true for ascending, false for descending
	 * @return a new LinkedHashMap sorted by values
	 */
	private static Map<String, Integer> sortByComparator(HashMap<String, Integer> unsortMap, final boolean order) {
		List<Entry<String, Integer>> list = new LinkedList<>(unsortMap.entrySet());
		// Sorting the list based on values
		list.sort((o1, o2) -> {
			if (order) {
				return o1.getValue().compareTo(o2.getValue());
			} else {
				return o2.getValue().compareTo(o1.getValue());
			}
		});

		// Maintaining insertion order with the help of LinkedList
		Map<String, Integer> sortedMap = new LinkedHashMap<>();
		for (Entry<String, Integer> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		return sortedMap;
	}

	/**
	 * Prints summary statistics (min, quartiles, max) for an array of integers.
	 *
	 * @param arr the array of integer values
	 */
	private static void toukey_summary(Integer[] arr) {
		Arrays.sort(arr);
        logger.info("Min:          {}", Percentile(arr, 0));
        logger.info("25% quartile: {}", Percentile(arr, 25));
        logger.info("50% quartile: {}", Percentile(arr, 50));
        logger.info("75% quartile: {}", Percentile(arr, 75));
        logger.info("Max:          {}", Percentile(arr, 100));
	}

	/**
	 * Calculates the specified percentile value from a sorted array.
	 *
	 * @param latencies the sorted array of integer values
	 * @param Percentile the percentile to compute (0-100)
	 * @return the value at the given percentile
	 */
	private static long Percentile(Integer[] latencies, double Percentile) {
		int Index = (int) Math.ceil((Percentile / 100.0) * latencies.length);
        logger.info("Index for {} is: {}", Percentile, Index);
		if (Index == 0) {
			return latencies[0];
		} else {
			return latencies[Index - 1];
		}
	}
}