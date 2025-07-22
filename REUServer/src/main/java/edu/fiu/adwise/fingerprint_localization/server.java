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

/*
Code for implementing Multi-Thread Server is from:
http://tutorials.jenkov.com/java-multithreaded-servers/multithreaded-server.html
*/
public class server implements Runnable {
	protected int          serverPort 	= 9254;
	protected ServerSocket serverSocket = null;
	protected boolean      isStopped    = false;
	protected Thread       runningThread= null;
	public static boolean  preprocessed = false;
	public static boolean  multi_phone = false;
	private static final Logger logger = LogManager.getLogger(server.class);

	public server(int port) {
		serverPort = port;
	}

	public void run() {
		synchronized(this) {
			this.runningThread = Thread.currentThread();
		}
		openServerSocket();
		while(!isStopped()) {
			Socket clientSocket = null;
			try {
				clientSocket = this.serverSocket.accept();
			}
			catch (IOException e) {
				if(isStopped()) {
					logger.info("Server Stopped.") ;
					return;
				}
				throw new RuntimeException("Error accepting client connection", e);
			}
			new Thread(new LocalizationThread(clientSocket)).start();
		}
		logger.info("Server Stopped.") ;
	}

	private synchronized boolean isStopped() {
		return this.isStopped;
	}

	public synchronized void stop() {
		this.isStopped = true;
		try {
			this.serverSocket.close();
		} catch (IOException e) {
			throw new RuntimeException("Error closing server", e);
		}
	}

	private void openServerSocket() {
		try {
			this.serverSocket = new ServerSocket(this.serverPort);
		} catch (IOException e) {
			throw new RuntimeException("Cannot open port " + this.serverPort, e);
		}
	}
	
	public static void main(String[] args) {
		Scanner inputReader = new Scanner(System.in);
		int port = 9254;
		
		try {
			// Finally, load user/password credentials
			String username = System.getenv("MYSQL_USER");
			String password = System.getenv("MYSQL_PASSWORD");
			LocalizationLUT.username = username;
			LocalizationLUT.password = password;

			// Check if LUT exists
			server.preprocessed = LocalizationLUT.isProcessed();
			// If made, update vector size now!
			if(server.preprocessed) {
				Distance.VECTOR_SIZE = LocalizationLUT.getVectorSize(Distance.FSF);
                logger.info("NEW VECTOR SIZE: {}", Distance.VECTOR_SIZE);
			}
			
			// Custom Port if needed?
			if (args.length == 1) {
				port = Integer.parseInt(args[0]);
				if(port <= 1024) {
                    logger.fatal("Invalid Port! {} use value over 1024!", port);
					System.exit(1);
				}
				else if(port > 65535) {
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
		
		// Create the Training Table...
		if(LocalizationLUT.createTrainingTable()) {
			logger.info("Created Database and Training Table!");
		}
		else {
			logger.info("Failed to create Training Table! It probably already exists!!!");
		}

		logger.info("==================FIU Indoor Localization Server Online=========================");
		
		// Get arguments from the command line...
		while(true) {
			try {
				String input = inputReader.nextLine();
				input = input.trim();
				String [] commands = input.split(" ");

                logger.info("Database preprocessed? {}", server.preprocessed);
                logger.info("Vector size: {}", Distance.VECTOR_SIZE);
                logger.info("APs in Training Data: {}", LocalizationLUT.getVectorSize(0.0));
                logger.info("N_F: {}", LocalizationLUT.getX("BWY_FL_03").length);
                logger.info("Current value of K is: {}", Distance.k);
				
				// Clear CLI
				if (commands[0].equalsIgnoreCase("clr")) {
					System.out.println("\033[2J\033[;H");
					System.out.flush();
				}
				else if (commands[0].equalsIgnoreCase("print")) {
					if (server.preprocessed) {
						LocalizationLUT.printTrainingData();
						if(server.multi_phone) {
							MultiphoneLocalization.printLUT();
						}
						else {
							LocalizationLUT.printLUT();				
						}
					} else {
						logger.info("Lookup Tables not processed yet!");
					}
				}
				else if (commands[0].equals("frequency")) {
					HashMap<String, Integer> frequency_map = LocalizationLUT.getCommonMac();
					if (frequency_map == null) {
						logger.info("Frequencies not processed yet!");
						continue;
					}
					int idx = 1;
					// Get sorted
					Map<String, Integer> sorted_map = sortByComparator(frequency_map, true);
					for (String AP: frequency_map.keySet()) {
						logger.info(idx + ", MAC: " + AP + " was detected " + sorted_map.get(AP) + " times");
						++idx;
					}
					toukey_summary(frequency_map.values().toArray(new Integer[0]));
				}
				// Change k
				else if(commands[0].equals("k")) {
					Distance.k = Integer.parseInt(commands[1]);
                    logger.info("Updated value of K is: {}", Distance.k);
				}
				// Test FSF
				else if (commands[0].equalsIgnoreCase("test-FSF")) {
					double fsf = Double.parseDouble(commands[1]);
					if (fsf < 0 || fsf > 1) {
						logger.info("Invalid FSF value! " +  fsf);
                    }
					else {
						logger.warn("Given FSF value: " + fsf + " minimum AP match is: " + LocalizationLUT.getVectorSize(fsf));
					}
				}
				// Change FSF
				else if (commands[0].equalsIgnoreCase("FSF")) {
					double fsf = Double.parseDouble(commands[1]);
					if (fsf < 0 || fsf > 1) {
                        logger.info("Invalid FSF value to set! {}", fsf);
                    }
					else {
						Distance.FSF = fsf;
						if(server.preprocessed) {
							logger.info("Please note for number of columns to change, you must re-build new Lookup table!");
						}
					}
				}
				else if(commands[0].equalsIgnoreCase("process")) {
					if(server.multi_phone) {
						if(LocalizationThread.multiprocess()) {
							logger.info("Preprocessing all Lookup Tables successful!");
						}
						else {
							logger.info("Preprocessing all Lookup Tables failed! (Single)");
						}
					}
					else {
						if(LocalizationThread.process()) {
							logger.info("Preprocessing one Lookup Table successful!");
						}
						else {
							logger.info("Preprocessing all Lookup Tables failed! (Multiple)");
						}
					}
				}
				else if(commands[0].equalsIgnoreCase("reset")) {
					if(LocalizationLUT.reset()) {
						logger.info("RESET SUCCESSFUL!");
						server.preprocessed = false;
					}
					else {
						logger.info("RESET FAILED!");
					}
				}
				else if (commands[0].equalsIgnoreCase("switch")) {
					if(server.multi_phone) {
						logger.info("Server is switched to 1 LUT");
						server.multi_phone = false;
					}
					else {
						logger.info("Server is switched to 1 LUT per Phone");
						server.multi_phone = true;
					}
				}
				else if (commands[0].equalsIgnoreCase("exit")) {
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
		inputReader.close();
		Localizationserver.stop();
	}
	
	/*
	 * This method is to sort a HashMap by its values
	 * It will return a new Hashmap sorted either in ascending or
	 * descending order with respect to its value
	 */

	private static Map<String, Integer> sortByComparator
	(HashMap<String, Integer> unsortMap, final boolean order) {
		List<Entry<String, Integer>> list = 
				new LinkedList<>(unsortMap.entrySet());
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

	private static void toukey_summary(Integer [] arr) {
		Arrays.sort(arr);
		logger.info("Min:          " + Percentile(arr, 0));
		logger.info("25% quartile: " + Percentile(arr, 25));
		logger.info("50% quartile: " + Percentile(arr, 50));
		logger.info("75% quartile: " + Percentile(arr, 75));
		logger.info("Max:          " + Percentile(arr, 100));
	}
	
    private static long Percentile(Integer [] latencies, double Percentile) {
        int Index = (int) Math.ceil((Percentile/100.0) * latencies.length);
        logger.info("Index for " + Percentile + " is: " + Index);
        if(Index == 0) {
        	return latencies[0];
        } else {
        	return latencies[Index - 1];	
        }
    }
}
