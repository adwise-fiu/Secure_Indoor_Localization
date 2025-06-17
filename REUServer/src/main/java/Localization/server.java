package Localization;

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

/*
Code for implementing Multi-Thread Server is from:
http://tutorials.jenkov.com/java-multithreaded-servers/multithreaded-server.html
*/
public class server implements Runnable
{
	protected int          serverPort 	= 9254;
	protected ServerSocket serverSocket = null;
	protected boolean      isStopped    = false;
	protected Thread       runningThread= null;
	public static boolean  preprocessed = false;
	public static boolean  multi_phone = false;
	
	public server(int port)
	{
		serverPort = port;
	}

	public void run()
	{
		synchronized(this)
		{
			this.runningThread = Thread.currentThread();
		}
		openServerSocket();
		while(!isStopped())
		{
			Socket clientSocket = null;
			try
			{
				clientSocket = this.serverSocket.accept();
			}
			catch (IOException e)
			{
				if(isStopped())
				{
					System.out.println("Server Stopped.") ;
					return;
				}
				throw new RuntimeException("Error accepting client connection", e);
			}
			new Thread(new LocalizationThread(clientSocket)).start();
		}
		System.out.println("Server Stopped.") ;
	}

	private synchronized boolean isStopped()
	{
		return this.isStopped;
	}

	public synchronized void stop()
	{
		this.isStopped = true;
		try
		{
			this.serverSocket.close();
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error closing server", e);
		}
	}

	private void openServerSocket()
	{
		try
		{
			this.serverSocket = new ServerSocket(this.serverPort);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Cannot open port " + this.serverPort, e);
		}
	}
	
	public static void main(String[] args)
	{
		Scanner inputReader = new Scanner(System.in);
		int port = 9254;
		
		try 
		{
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
				System.out.println("NEW VECTOR SIZE: " + Distance.VECTOR_SIZE);
			}
			
			// Custom Port if needed?
			if (args.length == 1) {
				port = Integer.parseInt(args[0]);
				if(port <= 1024) {
					System.err.println("Invalid Port! " + port + " use value over 1024!");
					System.exit(1);
				}
				else if(port > 65535) {
					System.err.println("Invalid Port! " + port + " use value below 65535!");
					System.exit(1);					
				}
			}
		}
		catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}
		catch (NumberFormatException nfe) {
			System.out.println("Please enter a valid custom port number");
			System.exit(1);
		}

		server Localizationserver = new server(port);
		new Thread(Localizationserver).start();
		
		// Create the Training Table...
		if(LocalizationLUT.createTrainingTable()) {
			System.out.println("Created Database and Training Table!");
		}
		else {
			System.out.println("Failed to create Training Table! It probably already exists!!!");
		}
		
		System.out.println("==================FIU Indoor Localization Server Online=========================");
		
		// Get arguments from the command line...
		while(true) {
			try {
				String input = inputReader.nextLine();
				input = input.trim();
				String [] commands = input.split(" ");
				
				System.out.println("Database preprocessed? " + server.preprocessed);
				System.out.println("Vector size: " + Distance.VECTOR_SIZE);
				System.out.println("APs in Training Data: " + LocalizationLUT.getVectorSize(0.0));
				System.out.println("N_F: " + LocalizationLUT.getX("BWY_FL_03").length);
				System.out.println("Current value of K is: " + Distance.k);
				
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
					}
					else {
						System.out.println("Lookup Tables not processed yet!");
					}
				}
				else if (commands[0].equals("frequency")) {
					HashMap<String, Integer> frequency_map = LocalizationLUT.getCommonMac();
					int idx = 1;
					// Get sorted
					Map<String, Integer> sorted_map = sortByComparator(frequency_map, true);
					for (String AP: frequency_map.keySet())
					{
						System.out.println(idx + ", MAC: " + AP + " was detected " + sorted_map.get(AP) + " times");
						++idx;
					}
					toukey_summary(frequency_map.values().toArray(new Integer[0]));
				}
				// Change k
				else if(commands[0].equals("k")) {
					Distance.k = Integer.parseInt(commands[1]);
					System.out.println("Updated value of K is: " + Distance.k);
				}
				// Test FSF
				else if (commands[0].equalsIgnoreCase("test-FSF")) {
					double fsf = Double.parseDouble(commands[1]);
					if (fsf < 0 || fsf > 1) {
						System.out.println("Invalid FSF value! " +  fsf);
						continue;
					}
					else {
						System.out.println("Given FSF value: " + fsf + " minimum AP match is: " + LocalizationLUT.getVectorSize(fsf));
					}
				}
				// Change FSF
				else if (commands[0].equalsIgnoreCase("FSF")) {
					double fsf = Double.parseDouble(commands[1]);
					if (fsf < 0 || fsf > 1) {
						System.out.println("Invalid FSF value! " +  fsf);
						continue;
					}
					else {
						Distance.FSF = fsf;
						if(server.preprocessed) {
							System.out.println("Please note for number of columns to change, you must re-build new Lookup table!");
						}
					}
				}
				else if(commands[0].equalsIgnoreCase("process")) {
					if(server.multi_phone) {
						if(LocalizationThread.multiprocess()) {
							System.out.println("Preprocessing all Lookup Tables successful!");
						}
						else {
							System.out.println("Preprocessing all Lookup Tables failed!");
						}
					}
					else {
						if(LocalizationThread.process()) {
							System.out.println("Preprocessing one Lookup Table successful!");
						}
						else {
							System.out.println("Preprocessing all Lookup Tables failed!");
						}
					}
				}
				else if(commands[0].equalsIgnoreCase("reset"))
				{
					if(LocalizationLUT.reset()) {
						System.out.println("RESET SUCCESSFUL!");
						server.preprocessed = false;
					}
					else {
						System.out.println("RESET FAILED!");
					}
				}
				else if (commands[0].equalsIgnoreCase("switch")) {
					if(server.multi_phone) {
						System.out.println("Server is switcthed to 1 LUT");
						server.multi_phone = false;
					}
					else {
						System.out.println("Server is switched to 1 LUT per Phone");
						server.multi_phone = true;
					}
				}
				else if (commands[0].equalsIgnoreCase("exit")) {
					break;
				}
			}
			catch (NumberFormatException nfe) {
				//nfe.printStackTrace();
				continue;
			} 
			catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			catch (SQLException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Stopping Localization Server...");
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
				new LinkedList<Entry<String, Integer>>(unsortMap.entrySet());
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
		System.out.println("Min:          " + Percentile(arr, 0));
		System.out.println("25% quartile: " + Percentile(arr, 25));
		System.out.println("50% quartile: " + Percentile(arr, 50));
		System.out.println("75% quartile: " + Percentile(arr, 75));
		System.out.println("Max:          " + Percentile(arr, 100));
	}
	
    private static long Percentile(Integer [] latencies, double Percentile) {
        int Index = (int) Math.ceil((Percentile/100.0) * latencies.length);
        System.out.println("Index for " + Percentile + " is: " + Index);
        if(Index == 0) {
        	return latencies[0];
        }
        else {
        	return latencies[Index - 1];	
        }
    }
}
