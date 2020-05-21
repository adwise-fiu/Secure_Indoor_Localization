package Localization;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;

import Localization.structs.LocalizationResult;
import Localization.structs.SendLocalizationData;
import Localization.structs.SendTrainingArray;
import security.DGK.DGKPublicKey;
import security.elgamal.ElGamalPublicKey;
import security.paillier.PaillierPublicKey;
import security.socialistmillionaire.alice;

/*
 * Uses Multi-thread code from:
 * http://tutorials.jenkov.com/java-multithreaded-servers/multithreaded-server.html
 */

public class LocalizationThread implements Runnable
{
	// REU Variables
	private LOCALIZATION_SCHEME LOCALIZATIONSCHEME;
	private boolean isREU2017;
	
	// Paillier and DGK Public Keys
	public PaillierPublicKey pk = null;
	public DGKPublicKey pubKey = null;
	public ElGamalPublicKey e_pk = null;
	
	// Communication
    private SendLocalizationData transmission;	// Data from Android Phone to Localize
    private SendTrainingArray trainDatabase;	// For Training  Data
 
    // I am measuring in nano-seconds, this converts back to seconds...
    private final static long BILLION = 1000000000;
    
    protected Socket clientSocket = null;

    // I/O streams
	private ObjectInputStream fromClient = null;
	private ObjectOutputStream toClient = null;
    
    //Distance Computations
	private DistancePlain PlaintextLocalization = null;
	private DistanceDGK DGKLocalization = null;
	private DistancePaillier PaillierLocalization = null;
	private DistanceElGamal ElGamalLocalization = null;
	
	//To either return Encrypted Values...or Send encrypted distances back...
	private ArrayList<LocalizationResult> replyToClient = new ArrayList<LocalizationResult>();
	private alice Niu = null;
	
    public LocalizationThread(Socket clientSocket)
    {
        this.clientSocket = clientSocket;
    }

	public void run()
    {
		try
		{
			fromClient= new ObjectInputStream(clientSocket.getInputStream());
			toClient = new ObjectOutputStream(clientSocket.getOutputStream());

			long startTime = System.nanoTime();//Start Timer
			Object x = fromClient.readObject();

			if (x instanceof String)
			{
				String command = (String) x;

				if (command.equalsIgnoreCase("UNDO"))
				{
					System.out.println("Command acquired: UNDO");
					toClient.writeBoolean(LocalizationLUT.undo());
					System.out.println("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				}
				else if (command.equals("RESET"))
				{
					System.out.println("Command acquired: RESET"); 
					toClient.writeBoolean(LocalizationLUT.reset());
					System.out.println("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");
					server.preprocessed = false;
					
					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				}
				else if (command.equals("Acquire all current training points"))
				{
					System.out.println("Command acquired: Obtain all Fingerprints!");
					if(server.multi_phone)
					{
						x = fromClient.readObject();
						if(x instanceof String [])
						{
							String [] phone_data = (String []) x;
							// Get All X-Y Coordinates from Training Table
							// Multi-Phone patch, Give only training points with specific phone
		                    String OS = phone_data[0];
		                    String Device = phone_data[1];
		                    String Model = phone_data[2];
		                    String Product = phone_data[3];
							toClient.writeObject(MultiphoneLocalization.getX(OS, Device, Model, Product));
							toClient.writeObject(MultiphoneLocalization.getY(OS, Device, Model, Product));
						}
					}
					else
					{
						toClient.writeObject(LocalizationLUT.getX());
						toClient.writeObject(LocalizationLUT.getY());
					}
					System.out.println("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				}
				else if (command.equals("Process LUT"))
				{
					System.out.println("Command acquired: Process Lookup Table");
					if(server.multi_phone)
					{
						toClient.writeBoolean(process());
					}
					else
					{
						toClient.writeBoolean(multiprocess());
					}
					toClient.flush();
					System.out.println("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

					// Flush and Close I/O streams and Socket
					this.closeClientConnection();		
					return;
				}
				else if(command.equals("Get Lookup Columns"))
				{
					System.out.println("Command acquired: Get Lookup MAC Addresses!");
					toClient.writeObject(LocalizationLUT.getColumnMAC());
					toClient.flush();
					System.out.println("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				}
				else
				{
					System.out.println("Command acquired: INVALID");
					System.out.println("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");
					// Flush and Close I/O streams and Socket
					this.closeClientConnection();
					return;
				}
			}
			
			// Train the Database
			// dataType = 0
			else if(x instanceof SendTrainingArray)
			{
				trainDatabase = (SendTrainingArray) x;
				System.out.println("TRAINING DATA RECEIVED...");
				server.lastX = trainDatabase.getX();
				server.lastY = trainDatabase.getY();

				toClient.writeBoolean(LocalizationLUT.submitTrainingData(trainDatabase));
				System.out.println("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");

				// Flush and Close I/O streams and Socket
				this.closeClientConnection();
				return;
			}
//============================================Localization==================================================================
			else if (!(x instanceof SendLocalizationData))
			{
				System.out.println("INVALID OBJECT: " + x.getClass() + "! Closing...");
				System.out.println("Completion time: " + (System.nanoTime() - startTime)/BILLION + " seconds");
				
				// Flush and Close I/O streams and Socket
				this.closeClientConnection();		
				return;
			}
		
			transmission = (SendLocalizationData) x;

			// Obtain Algorithm and keys
			isREU2017 = transmission.isREU2017;
			LOCALIZATIONSCHEME = transmission.LOCALIZATION_SCHEME;
			pubKey = transmission.pubKey;
			pk = transmission.pk;
			e_pk = transmission.e_pk;
			
			System.out.println("LOCALIZATION SCHEME: " + LOCALIZATIONSCHEME + " isREU2017: " + isREU2017);
			Niu = new alice(clientSocket);
			Niu.setDGKMode(true);
			
			// Read from Database
			switch(LOCALIZATIONSCHEME)
			{
				case PLAIN_MIN:
					PlaintextLocalization = new DistancePlain(transmission);
					replyToClient = PlaintextLocalization.MinimumDistance(null);
					if(isREU2017)
					{
						toClient.writeObject(PlaintextLocalization.location);
					}
					else
					{
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;
					
				case DGK_MIN:
					DGKLocalization = new DistanceDGK(transmission);
					replyToClient = DGKLocalization.MinimumDistance(Niu);
					if (isREU2017)
					{
						toClient.writeObject(DGKLocalization.encryptedLocation);	
					}
					else
					{	
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;	
					
				case PAILLIER_MIN:
					PaillierLocalization = new DistancePaillier(transmission);
					Niu.setDGKMode(false);
					replyToClient = PaillierLocalization.MinimumDistance(Niu);

					if (isREU2017)
					{
						toClient.writeObject(PaillierLocalization.encryptedLocation);
					}
					else
					{
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;

				case PLAIN_MCA:
					PlaintextLocalization = new DistancePlain(transmission);
					replyToClient = PlaintextLocalization.MissConstantAlgorithm();

					if(isREU2017)
					{
						toClient.writeObject(PlaintextLocalization.location);
					}
					else
					{
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;

				case DGK_MCA:
					DGKLocalization = new DistanceDGK(transmission);
					replyToClient = DGKLocalization.MissConstantAlgorithm();
					if (isREU2017)
					{
						toClient.writeObject(DGKLocalization.Phase3(Niu));
					}
					else
					{
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;	
				case PAILLIER_MCA:
					Niu.setDGKMode(false);
					PaillierLocalization = new DistancePaillier(transmission);
					replyToClient = PaillierLocalization.MissConstantAlgorithm();

					if (isREU2017)
					{
						toClient.writeObject(PaillierLocalization.Phase3(Niu));
					}
					else
					{
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;			 	
				 
				case PLAIN_DMA:
					PlaintextLocalization = new DistancePlain(transmission);
					replyToClient = PlaintextLocalization.DynamicMatchingAlgorithm();
					if(isREU2017)
					{
						toClient.writeObject(PlaintextLocalization.location);
					}
					else
					{
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;

				case DGK_DMA:
					DGKLocalization = new DistanceDGK(transmission);
					replyToClient = DGKLocalization.DynamicMatchingAlgorithm();
					if (isREU2017)
					{				
						toClient.writeObject(DGKLocalization.Phase3(Niu));
					}
					else
					{	
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;
				 	
				case PAILLIER_DMA:
					Niu.setDGKMode(false);
					PaillierLocalization = new DistancePaillier(transmission);
					replyToClient = PaillierLocalization.DynamicMatchingAlgorithm();
					if (isREU2017)
					{
						toClient.writeObject(PaillierLocalization.Phase3(Niu));
					}
					else
					{	
						toClient.writeObject(replyToClient);	
					}
					toClient.flush();
					break;
				case EL_GAMAL_MIN:
					ElGamalLocalization = new DistanceElGamal(transmission);
					replyToClient = ElGamalLocalization.MinimumDistance(Niu);
					if (isREU2017)
					{
						toClient.writeObject(ElGamalLocalization.e_xy);
					}
					else
					{	
						toClient.writeObject(replyToClient);	
					}
					toClient.flush();
					break;
				case EL_GAMAL_MCA:
					ElGamalLocalization = new DistanceElGamal(transmission);
					replyToClient = ElGamalLocalization.MissConstantAlgorithm();
					if (isREU2017)
					{
						ElGamalLocalization.Phase3(Niu);
						toClient.writeObject(ElGamalLocalization.e_xy);
					}
					else
					{	
						toClient.writeObject(replyToClient);	
					}
					toClient.flush();
					break;
				case EL_GAMAL_DMA:
					ElGamalLocalization = new DistanceElGamal(transmission);
					replyToClient = ElGamalLocalization.DynamicMatchingAlgorithm();
					if (isREU2017)
					{
						ElGamalLocalization.Phase3(Niu);
						toClient.writeObject(ElGamalLocalization.e_xy);
					}
					else
					{
						toClient.writeObject(replyToClient);
					}
					toClient.flush();
					break;
				default:
					System.err.println("INVALID LOCALIZATION SCHEME!");
					break;
			}
			System.out.println("Computation completed, it took " + (System.nanoTime() - startTime)/BILLION + " seconds");
			this.closeClientConnection();
			return;
		}
		catch(IOException | SQLException | ClassNotFoundException | IllegalArgumentException e) 
		{
			e.printStackTrace();
		}
    }
	
	public static boolean process()
	{
		if(!server.preprocessed)
		{
			if(LocalizationLUT.createTables())
			{
				System.out.println("Created new Lookup Table!");
			}
			else
			{
				System.out.println("The table exists! Drop it first!");
				return false;
			}
			System.out.println("Computing Lookup Table Data...");
			Distance.VECTOR_SIZE = LocalizationLUT.getVectorSize(Distance.FSF);
			System.out.println("NEW VECTOR SIZE: " + Distance.VECTOR_SIZE);
			
			if(LocalizationLUT.UpdatePlainLUT())
			{
				System.out.println("Sucessfully created Lookup table!");
				server.preprocessed = true;
				return true;
			}
			else
			{
				System.out.println("Failed to create Lookup table!");
				return false;
			}
		}
		else
		{
			System.out.println("Denied to pre-process again. Reset First!");
			return false;
		}
	}
	
	public static boolean multiprocess()
	{
		if(!server.preprocessed)
		{
			if(MultiphoneLocalization.createTables())
			{
				System.out.println("Created new Lookup Table!");
			}
			else
			{
				System.out.println("The table exists! Drop it first!");
				return false;
			}
			System.out.println("Computing Lookup Table Data...");
			Distance.VECTOR_SIZE = LocalizationLUT.getVectorSize(Distance.FSF);
			System.out.println("NEW VECTOR SIZE: " + Distance.VECTOR_SIZE);
			
			if(MultiphoneLocalization.UpdatePlainLUT())
			{
				System.out.println("Sucessfully created Lookup table!");
				server.preprocessed = true;
				return true;
			}
			else
			{
				System.out.println("Failed to create Lookup table!");
				return false;
			}
		}
		else
		{
			System.out.println("Denied to pre-process again. Reset First!");
			return false;
		}
	}

	private void closeClientConnection() throws IOException
	{
		toClient.close();
		fromClient.close();
		if (clientSocket != null && clientSocket.isConnected())
		{
			clientSocket.close();	
		}
	}
}