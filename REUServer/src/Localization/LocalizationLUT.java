package Localization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import Localization.structs.SendTrainingArray;

import java.net.*;

/*
Use this command to disable MySQL Server Safe Mode:
SET SQL_SAFE_UPDATES = 0;
*/

public class LocalizationLUT
{
	/*
	 * 	For this Class to Build the Lookup Table...
	 * 	it assumes 
	 * 	1 - The Table called REULookUpTable is made
	 * 	2 - it has the structure of X, Y, 10 MAC Addresses
	 *  3 - Training Data is ready and loaded into a MYSQLServer
	 *  Final assumption, you are using the correct column names!
	 */

	public static String username = "hello";
	public static String password = "world";
		
	public final static String myDriver = "org.gjt.mm.mysql.Driver";
	public final static String DB = "fiu";
	public final static String URL = "jdbc:mysql://localhost:3306/?&useSSL=false";
	
	//Data to be modified
	protected final static String TRAININGDATA = "trainingpoints";
	protected final static String PLAINLUT = "LUT";

	/*
	How to Build Lookup Table
	
	build.createTables(); 	//Create All needed Tables, ALL PLAINLUTS for each Phone
	build.findCommonMAC();	//Get 10 AP's
	build.getXY();			//Get all X Y coordinates
	build.getDataforLUT();	//Acquire RSS Data for Lookup Table
	build.UpdateTables();	//Using the previous methods, insert into Lookup Tables
	
	Print the LookupTables to a CSV File
	build.createCSVFiles();
	*/

	/*
	 	Input: Training Array
	 	- Xcoordiante of Training Location
	 	- Ycoordinate of Training Location
	 	- Array of RSS
	 	- Array of AP's
	 	
	 	Purpose of Method:
		Process training Data and send it to a Table to be processed later into 
		a LookUp Table
		
		Potential Errors:
		
		Returns:
		Nothing, just updates the table on MySQL Server.
		
	 */
	
	public static boolean submitTrainingData(SendTrainingArray input)
	{
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);

			String SQL = "insert into " + DB + "." + TRAININGDATA + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
			PreparedStatement insert;
		
			String [] MAC = input.getMACAddress();
			Integer [] RSS  = input.getRSS();
		
			java.sql.Timestamp date = new java.sql.Timestamp(new java.util.Date().getTime());
			
			for (int i = 0; i < MAC.length; i++)
			{
				insert = conn.prepareStatement(SQL);
				insert.setDouble(1, input.getX());
				insert.setDouble(2, input.getY());
				insert.setString(3, MAC[i]);
				insert.setInt	(4, RSS[i]);
				
				insert.setString(5, input.getOS());
				insert.setString(6, input.getDevice());
				insert.setString(7, input.getModel());
				insert.setString(8, input.getProduct());
				insert.setTimestamp(9, date);
				
				//Execute and Close SQL Command
				insert.execute();
				insert.close();
			}
			
			// DO NOT FORGET TO COMMIT!!
			conn.prepareCall("commit;").execute();
			return true;
		}
		catch(SQLException | ClassNotFoundException cnf)
		{
			return false;
		}
	}
	
	public static String [] getColumnMAC()
	{
		List<String> common_aps = new ArrayList<String>();
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement st = conn.createStatement();

			ResultSet rs = null;
			if(server.preprocessed)
			{
				if(server.multi_phone)
				{
					List<String> tables = new ArrayList<String>();
					// All tables will have same AP columns 
					// show tables in fiu where tables_in_fiu != 'trainingpoints';
					rs = st.executeQuery("SHOW tables in " + DB + " where tables_in_" + DB + " != '" + TRAININGDATA + "'");
					while (rs.next())
					{
						tables.add(rs.getString("Tables_in_" + DB));
					}
					String table = tables.get(0).replace(" ", "");
					table = table.replace("-", "");
					// Now use regular query
					rs = st.executeQuery("SHOW COLUMNS FROM " + DB + "." + table + " ;");
				}
				else
				{
					rs = st.executeQuery("SHOW COLUMNS FROM " + DB + "." + PLAINLUT + " ;");
				}
				int counter = 1;
				
				while (rs.next())
				{
					// skip ID(1), XCoordinate(2) and YCoordinate(3)!
					if(counter != 4)
					{
						++counter;
					}
					else
					{
						common_aps.add(rs.getString("Field"));
					}
				}
				
				for (int i = 0; i < common_aps.size(); i++)
				{
					common_aps.set(i, getColumnName(common_aps.get(i)));
				}
			}
			else
			{
				// This will be called when it is time to create the tables
				if(Distance.VECTOR_SIZE == -1)
				{
					// How many AP columns will we have
					Distance.VECTOR_SIZE = getVectorSize(Distance.FSF);
				}
				// Used to build the Lookup Columns for each most frequently seen AP
				rs = st.executeQuery("SELECT MACADDRESS, Count(MACADDRESS) as count from " + DB + "." 				
						+ TRAININGDATA + " group by MACADDRESS ORDER BY count DESC LIMIT " + Distance.VECTOR_SIZE + ";");
				while (rs.next())
				{
					common_aps.add(rs.getString("MACADDRESS"));
				}
			}
			return common_aps.toArray(new String[common_aps.size()]);
		}
		catch(SQLException | ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
			return null;
		}
	}
	
	public static HashMap<String, Integer> getCommonMac()
	{
		/*
		Do a SQL Statement to find what are the 10 most prominent MAC Addresses:
		SELECT MACADDRESS, Count(MACADDRESS) as count 
		from FIU.trainingpoints
		group by MACADDRESS
		ORDER BY count DESC
		LIMIT 10;
		 */

		HashMap<String, Integer> frequency_map = new HashMap<String, Integer>();
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement st = conn.createStatement();

			// execute the query, and get a java result set
			ResultSet rs = st.executeQuery("SELECT MACADDRESS, Count(MACADDRESS) as count from "
			+ DB + "." + TRAININGDATA + " group by MACADDRESS ORDER BY count DESC;");
	
			while (rs.next())
			{
				frequency_map.put(rs.getString("MACADDRESS"), rs.getInt(2));
			}
			return frequency_map;
		}
		catch(SQLException | ClassNotFoundException cnf)
		{
			return null;
		}
	}

	/*
	 * 	Input: Nothing
	 * 
	 	Purpose of Method:
	 * 	Get all distinct pairs of x, y coordinates.
	 * 	1- Use this Method on TrainingAcitivity to know
	 * 	which points have already been trained...
	 * 	2- Use thid method to get all distinct XY coordinates for Lookup table creation	
	 * Suggestion: 
	 * 	Use the Ascending Keyword as it does serve as a useful 
	 * 	double check if the Lookup Tables are being made correctly.
	 * 	This is because if you do select all the x values are sorted from low to high by default
	 * 	
	 * 	Errors:
	 * 	
	 * 	Returns:
	 * 	Either nothing or returns to Localization Thread,
	 * 	Which returns to the TrainActivity on the Phone, informing which points are already
	 * 	trained
	 */
	
	public static Double [] getX() 
			throws ClassNotFoundException, SQLException
	{
		Double [] X = null;
		ArrayList<Double> x = new ArrayList<Double>();

		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		// execute the query, and get a java result set
		/*
		select distinct Xcoordinate, Ycoordinate from fiu.trainingpoints Order By Xcoordinate ASC;
		 */
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery("select distinct Xcoordinate, Ycoordinate"
				+ " from " + DB + "." + TRAININGDATA +" Order By Xcoordinate ASC;");
		while (rs.next())
		{
			x.add(rs.getDouble("Xcoordinate"));
		}
		X = x.toArray(new Double[x.size()]);
		return X;
	}
	
	public static Double [] getY() 
			throws ClassNotFoundException, SQLException
	{
		Double [] Y = null;
		ArrayList<Double> y = new ArrayList<Double>();
	
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);

		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery("select distinct Xcoordinate, Ycoordinate"
				+ " from " + DB + "." + TRAININGDATA +" Order By Xcoordinate ASC;");
		while (rs.next())
		{
			y.add(rs.getDouble("Ycoordinate"));
		}
		Y = y.toArray(new Double[y.size()]);	
		return Y;
	}

	/*
 	Input: Nothing
 	
 	Purpose of Method:
	Create Three Lookup Tables
	1- PlainText
	2- Paillier
	3- DGK
	
	Potential Errors:
	
	Returns:
	New Tables in the mySQL Database.
	*/
	
	public static boolean createTrainingTable()
	{
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();

			try
			{
				stmt.execute("CREATE DATABASE " + DB);	
			}
			finally
			{
				// Ok the database exists already, but try to make table now...
				String sqlTrain = "CREATE TABLE " + DB + "." + TRAININGDATA + " " +
						"( " +
						"Xcoordinate Double not null, " +
						"YCoordinate Double not null, " +
						"MACADDRESS Text not null, " +
						"RSS Integer not null, " +
						"OS Text not null, "  +
						"Device Text not null, " +
						"Model Text not null, " +
						"Product Text not null, " + 
						"currentTime DATETIME not null "+
						");";
				stmt.executeUpdate(sqlTrain);
			}
		}
		catch(SQLException | ClassNotFoundException cnf)
		{
			return false;
		}
		return true;
	}
	 
	public static boolean createTables()
	{
		String [] ColumnNames = getColumnMAC();
		
		try
		{
			Class.forName(myDriver);
			System.out.println("Connecting to a local database...");
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();
			
			// BUILD ONE TABBLE FOR ALL PHONES
			String sql =
					"CREATE TABLE " + DB + "." + PLAINLUT +
					"("
					+ "ID INTEGER not NULL, "
					+ " Xcoordinate DOUBLE not NULL, "
					+ " Ycoordinate DOUBLE not NULL, ";
			String add = "";
			for (int i = 0; i < Distance.VECTOR_SIZE; i++)
			{
				add += makeColumnName(ColumnNames[i]) + " INTEGER not NULL,";
			}
			
			sql += add;
			sql +=" PRIMARY KEY (ID));"; 
			System.out.println(sql);
			stmt.executeUpdate(sql);
			return true;
		}
		catch(SQLException se)
		{
			se.printStackTrace();
			return false;
		}
		catch(ClassNotFoundException cnf)
		{
			System.err.println("SQL Exception caught: createTables()");
			return false;
		}
	}
	
	public static boolean isProcessed() throws ClassNotFoundException, SQLException
	{
		int bool = -1;
		String query = "";
		query += "SELECT COUNT(*)\n";
		query += "FROM information_schema.tables \n";
		query += "WHERE table_schema = '" + DB + "' \n";
		
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		Statement stmt = conn.createStatement();
		ResultSet answer = stmt.executeQuery(query);
		while (answer.next())
		{
			bool = answer.getInt(1);
		}
		// 1 Table implies only training point table found
		// 2 or more implies that LUts built
		return bool >= 2;
	}
	
	// PROCESS LUT
	public static boolean UpdatePlainLUT()
	{
		try
		{
			// Init
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			
			// Acquire All Data to Create Plain Text Lookup Table	
			Double [] X = getX();
			Double [] Y = getY();
			String [] CommonMac = getColumnMAC();
			int [][] Pinsert = new int [X.length][Distance.VECTOR_SIZE];
						
			Statement Plainst; 
			String getRSS;
			ResultSet RSS;
			for (int x = 0; x < X.length; x++)
			{
				/*
		 		select RSS FROM TRAININGDATA
				WHERE Xcoordinate = 227.761 
				AND YCoordinate = 1095.73 
				AND MACADDRESS = '84:1b:5e:4b:80:e2';
				 */
				
				for (int currentCol = 0; currentCol < Distance.VECTOR_SIZE; currentCol++)
				{
					getRSS = "SELECT RSS FROM " + DB + "." + TRAININGDATA
							+ " WHERE Xcoordinate = "
							+ X[x]
							+ " AND Ycoordinate = "
							+ Y[x]
							+ " AND MACADDRESS = '"
							+ CommonMac[currentCol]
							+ "';";
								
					Plainst = conn.createStatement();
					RSS = Plainst.executeQuery(getRSS);
					while (RSS.next())
					{
						Pinsert [x][currentCol] = RSS.getInt("RSS");
					}
					
					//CHECK IF I GOT A NULL!
					if (Pinsert[x][currentCol] == 0)
					{
						Pinsert[x][currentCol] = Distance.v_c;
					}
					Plainst.close();		
				}
			}
			
			// -----------------Place data----------------------------------------
			String append = "";
			for (int i = 0; i < Distance.VECTOR_SIZE; i++)
			{
				append += " ?,";
			}
			//Remove the Extra , at the end!!
			append = append.substring(0, append.length() - 1);
			append += ");";
			
			//The Insert Statement for Plain Text
			String PlainQuery = "insert into " + DB + "." + PLAINLUT
			+ " values (?, ?, ?," + append;
			
			PreparedStatement Plain;

			for (int PrimaryKey = 0; PrimaryKey < X.length; PrimaryKey++)
			{
				if(isNullTuple(Pinsert[PrimaryKey]))
				{
					continue;
				}
				Plain = conn.prepareStatement(PlainQuery);
				
				//Fill up the PlainText Table Part 1
				Plain.setInt (1, PrimaryKey + 1);
				Plain.setDouble(2, X[PrimaryKey]);
				Plain.setDouble(3, Y[PrimaryKey]);
				
				for (int j = 0; j < Distance.VECTOR_SIZE;j++)
				{
					Plain.setInt((j + 4), Pinsert[PrimaryKey][j]);
				}
				Plain.execute();
				Plain.close();
			}
			
			//DONT FORGET TO COMMIT!!!
			Statement commit = conn.createStatement();
			commit.executeQuery("commit;");
			conn.close();
			return true;
		}
		catch(SQLException | ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
			return false;
		}
	}
	
	/*	
 	Input: A Row of size 10 of RSS values
 	
 	Purpose of Method: 
 	The lookup table shouldn't have any points consisting of all RSS = -120
 	That can cause errors. 
 	To avoid this, if a row is detect full of nulls, return true.
 	If it returns true, the Insert into Lookup Tables will omit this row.

	Potential Errors:
	
	Returns:
	True/False to UpdateTables()
	*/
	
	public static boolean isNullTuple(int [] row)
	{
		int counter = 0;
		for (int i = 0; i < row.length; i++)
		{
			if (row[i]==-120)
			{
				++counter;
			}
		}
		return counter == Distance.VECTOR_SIZE;
	}
	
	public static void printTrainingData()
	{
		String Q1 = "SELECT * FROM " + DB + "." + TRAININGDATA;
		String PointsCSV = "./TrainingPoints.csv";
		
		try
		{	
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);

			// create the java statement
			Statement stFour = conn.createStatement();
			
			// execute the query, and get a java result set
			ResultSet dataSet = stFour.executeQuery(Q1);
			
			PrintWriter WritePoints = new PrintWriter(
					new BufferedWriter(
							new OutputStreamWriter(
									new FileOutputStream(PointsCSV))));
			
			WritePoints.println("Xcoordinate,Ycoordinate,AP,RSS,OS,Device,Model,Product,ScanTime");
			
			String tuple = "";
			while(dataSet.next())
			{
				tuple += dataSet.getDouble("Xcoordinate") 	+ ",";
				tuple += dataSet.getDouble("Ycoordinate")	+ ",";
				tuple += dataSet.getString("MACADDRESS")	+ ",";
				tuple += dataSet.getInt("RSS")				+ ",";
				// Phone Data and Date
				tuple += dataSet.getString("OS")			+ ",";
				tuple += dataSet.getString("Device")		+ ",";
				tuple += dataSet.getString("Model")			+ ",";
				tuple += dataSet.getString("Product")		+ ",";
				tuple += dataSet.getTimestamp("currentTime").toString();
				WritePoints.println(tuple);
				tuple = "";
			}			
			WritePoints.close();
		}
		catch(IOException | SQLException | ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
		}
	}
	
	/*
 	Input: Nothing
 	
 	Purpose of Method:
	Print the Lookup Table(s)
	
	Returns:
	Two new CSV files
	*/
	
	public static void printLUT()
	{
		String Q3 = "SELECT * FROM " + DB + "." + PLAINLUT;
		String PlainCSV = "./PlainLUT.csv";
		
		String [] ColumnMac = LocalizationLUT.getColumnMAC();
		String header = "Xcoordinate,Ycoordiante,";
		for(int i = 0; i < Distance.VECTOR_SIZE; i++)
		{
			if(i == Distance.VECTOR_SIZE - 1)
			{
				header += ColumnMac[i];
			}
			else
			{
				header += ColumnMac[i] + ",";
			}
		}
		
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);

			// create the java statement
			Statement stTwo = conn.createStatement();
			
			// execute the query, and get a java result set
			ResultSet PlainResult = stTwo.executeQuery(Q3);
			ResultSetMetaData meta = PlainResult.getMetaData();

			PrintWriter WritePlain = new PrintWriter(
					new BufferedWriter(
							new OutputStreamWriter(
									new FileOutputStream(PlainCSV))));
			
			WritePlain.println(header);
	
			String tuple = "";
			
			while(PlainResult.next())
			{
				// Skip ID, 1
				tuple += PlainResult.getDouble(2) + ",";
				tuple += PlainResult.getDouble(3)  + ",";
				for (int i = 0; i < Distance.VECTOR_SIZE; i++)
				{
					String name = meta.getColumnName(i+4);
					tuple += PlainResult.getInt(name)  + ",";
				}
				// Delete extra ,
				tuple = tuple.substring(0, tuple.length() - 1);
				WritePlain.println(tuple);
				tuple = "";
			}
			WritePlain.close();
		}
		catch(IOException | SQLException | ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
		}
	}
	
	public static boolean resetLUT()
	{
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();
			if(server.multi_phone)
			{
				System.out.println("Multi Reset");
				// All tables will have same AP columns 
				// show tables in fiu where tables_in_fiu != 'trainingpoints';
				ResultSet rs = stmt.executeQuery("SHOW tables in " + DB + " where tables_in_" + DB + " != '" + TRAININGDATA + "'");
				while (rs.next())
				{
					String table = rs.getString("Tables_in_" + DB);
					table = table.replace(" ", "");
					table = table.replace("-", "");
					stmt.executeUpdate("DELETE FROM " + DB + "." + table + ";");	
				}
			}
			else
			{
				System.out.println("One Reset");
				stmt.executeUpdate("DELETE FROM " + DB + "." + PLAINLUT + ";");	
			}
	
			//Save the Changes...
			stmt.executeUpdate("commit;");
			stmt.close();
			return true;
		}
		catch(SQLException | ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
			return false;
		}
	}
	
	// HARD RESET: RE-TRAIN EVERYTHING!
	// Delete ALL LUT!
	// Can be made into an administrator button?
	public static boolean reset()
	{
		try
		{
			Class.forName(myDriver);
		
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();
			//Save the Changes...
			if(server.multi_phone)
			{
				// All tables will have same AP columns 
				// show tables in fiu where tables_in_fiu != 'trainingpoints';
				ResultSet rs = stmt.executeQuery("SHOW tables in " + DB + " where tables_in_" + DB + " != '" + TRAININGDATA + "'");
				List<String> tables = new ArrayList<String>();
				while (rs.next())
				{
					tables.add(rs.getString("Tables_in_" + DB));
				}
				for (String table: tables)
				{
					table = table.replace(" ", "");
					table = table.replace("-", "");
					stmt.executeUpdate("DROP TABLE " + DB + "." + table + ";");	
				}
			}
			else
			{
				stmt.executeUpdate("DROP TABLE "  + DB + "." + PLAINLUT + ";");
			}
			stmt.executeUpdate("commit;");
			stmt.close();
			return true;
		}
		catch(SQLException | ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
			return false;
		}
	}
	
	/*
	 *  Will undo last insert into Training Points.
	 *  To do this correctly for multiple devices, you mignt need a column to uniquely identify device?
	 *  Assuming you grant the right for one phone to delete...
	 */
	
	public static boolean undo()
	{
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();
		
			stmt.executeUpdate("DELETE FROM " 	+ DB + "."+ TRAININGDATA
					+ " where Xcoordinate = " + server.lastX + " AND "
							+ "Ycoordinate = " + server.lastY + ";");
			//Save the Changes...
			stmt.executeUpdate("commit;");
			stmt.close();
			return true;
		}
		catch(SQLException se)
		{
			se.printStackTrace();
			return false;
		}
		catch(ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
			return false;
		}
	}
	
	// http://www.dummies.com/education/math/statistics/how-to-calculate-percentiles-in-statistics/
	// Only accept from 0 - 1.
	public static int getVectorSize(double percentile)
	{
		if(percentile < 0 || percentile > 1)
		{
			return -1;
		}
		
		ArrayList<Integer> AP_count = new ArrayList<Integer>();
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();
			
			String query = "SELECT Count(MACADDRESS) as count from" + '\n';
			query += DB + "." + TRAININGDATA + '\n';
			query += "group by MACADDRESS" + '\n';
			query += "ORDER BY count ASC" + '\n';
			ResultSet vec = stmt.executeQuery(query);
			
			while(vec.next())
			{
				AP_count.add(vec.getInt("count"));	
			}
			stmt.close();
		}
		catch(SQLException | ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
		}
		// same as get IDX of Percentile, Note the int is already sorted!
		int num_AP_filtered = (int) Math.ceil(percentile * AP_count.size());
		System.out.println("At " + percentile * 100 + "% this AP is detected " + AP_count.get(num_AP_filtered) + " times and you filtered " + num_AP_filtered + " APs");
		return AP_count.size() - num_AP_filtered;
	}
	
	public static void getAPManufacturer() 
			throws IOException, InterruptedException, SQLException, ClassNotFoundException
	{
		// Should be done before Lookup Tables are made...
		// Get all APs from MySQL database
		
		ArrayList<String> APs = new ArrayList<String>();
		String [] Makers;
		
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery("SELECT MACADDRESS from " + DB + "." + TRAININGDATA + ";");
		
		while (rs.next())
		{
			APs.add(rs.getString("MACADDRESS"));
		}
		Makers = new String[APs.size()];
		
		// Now get the Manufacturer of all the AP's
		for (int i = 0; i < APs.size(); i++)
		{
			StringBuilder result = new StringBuilder();
			URL url = new URL("http://api.macvendors.com/" + APs.get(i));
			HttpURLConnection connect = (HttpURLConnection) url.openConnection();
			connect.setRequestMethod("GET");
			BufferedReader rd = new BufferedReader(new InputStreamReader(connect.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null) 
			{
				result.append(line);
			}
			Makers[i] = line;
			rd.close();
			Thread.sleep(1200);//No API 1 request a second, add .2 as as slack
		}
	}
	
	public static void getPlainLookup(ArrayList<Long[]> SQLData, ArrayList<Double[]> coordinates) 
			throws ClassNotFoundException, SQLException
	{
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery(""
				+ "select * from " + DB + "." + PLAINLUT + ""
				+ " Order By Xcoordinate ASC;");
		
		while(rs.next())
		{
			Long [] RSS = new Long [Distance.VECTOR_SIZE];
			Double [] Location = new Double [2];
			Location[0] = rs.getDouble("Xcoordinate");	// 2
			Location[1] = rs.getDouble("Ycoordinate");	// 3
			// Start with 4....
			for (int i = 0; i < Distance.VECTOR_SIZE; i++)
			{
				RSS[i] = (long) rs.getInt(i + 4);
			}
			SQLData.add(RSS);
			coordinates.add(Location);
		}
	}
	
	// All Column names in MySQL can't start with a number
	// So if the MAC Address starts with 1 - 9, swap it!
	protected static String makeColumnName(String column)
	{
		String answer = "";
		if(column == null || column.length() == 0)
		{
			return answer;
		}
		char first = column.charAt(0);
		// Map the following -> (Jump 65)
		// 0 (48) --> q (113)
		// 1 (49) --> r (114)
		// 2 (50) --> s (115)
		// 3 (51) --> t (116)
		// 4 (52) --> u (117)
		// 5 (53) --> v (118)
		// 6 (54) --> w (119)
		// 7 (55) --> x (120)
		// 8 (56) --> y (121)
		// 9 (57) --> z (122)
		
		if(Character.isDigit(first))
		{
			char alphabet = (char) (((int) first) + 65);
			answer = alphabet + column.substring(1);
			answer = answer.replace(':', '_');
			return answer;
		}
		else
		{
			answer = column.replace(':', '_');
			return answer;
		}
	}
	
	// All Column names in MySQL can't start with a number
	// So if the MAC Address starts with 1 - 9, swap it!
	protected static String getColumnName(String column)
	{
		String answer = "";
		if(column == null || column.length() == 0)
		{
			return answer;
		}
		char first = column.charAt(0);
		// NOTE WE DO REVERSE THIS TIME
		// Map the following -> (Jump 65)
		// 0 (48) --> q (113)
		// 1 (49) --> r (114)
		// 2 (50) --> s (115)
		// 3 (51) --> t (116)
		// 4 (52) --> u (117)
		// 5 (53) --> v (118)
		// 6 (54) --> w (119)
		// 7 (55) --> x (120)
		// 8 (56) --> y (121)
		// 9 (57) --> z (122)
		// If the first character in set [q, z] then -65 to get correct MAC back
		if("qrstuvwxyz".indexOf(first) != -1)
		{
			char alphabet = (char) (((int) first) - 65);
			answer = alphabet + column.substring(1);
			answer = answer.replace('_', ':');
			return answer;
		}
		else
		{
			answer = column.replace('_', ':');
			return answer;
		}
	}
}