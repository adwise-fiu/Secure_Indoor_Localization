package edu.fiu.adwise.fingerprint_localization.database;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import edu.fiu.adwise.fingerprint_localization.server;
import edu.fiu.adwise.fingerprint_localization.structs.SendTrainingData;
import edu.fiu.adwise.fingerprint_localization.distance_computation.Distance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.*;

/*
Use this command to disable MySQL Server Safe Mode:
SET SQL_SAFE_UPDATES = 0;
*/

public class LocalizationLUT {
	/*
	 * 	For this Class to Build the Lookup Table...
	 * 	it assumes 
	 * 	1 - The Table called REULookUpTable is made
	 * 	2 - it has the structure of X, Y, 10 MAC Addresses
	 *  3 - Training Data is ready and loaded into a MYSQLServer
	 *  Final assumption, you are using the correct column names!
	 */

	public static String username = System.getenv("MYSQL_USER") != null ? System.getenv("MYSQL_USER") : "";
	public static String password = System.getenv("MYSQL_PASSWORD") != null ? System.getenv("MYSQL_PASSWORD") : "";
	public final static String DB = System.getenv("DATABASE") != null ? System.getenv("DATABASE") : "fiu";
	private static final Logger logger = LogManager.getLogger(LocalizationLUT.class);

	public final static String myDriver = "com.mysql.cj.jdbc.Driver";
	public final static String URL = "jdbc:mysql://localhost:3306/?&useSSL=false";

	protected final static String TRAININGDATA = "trainingpoints";
	
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
	
	public static boolean submitTrainingData(SendTrainingData input) {
		try {
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);

			String SQL = "insert into " + DB + "." + TRAININGDATA + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			PreparedStatement insert;
		
			String [] MAC = input.getMACAddress();
			Integer [] RSS  = input.getRSS();
		
			java.sql.Timestamp date = new java.sql.Timestamp(new java.util.Date().getTime());
			
			for (int i = 0; i < MAC.length; i++) {
				insert = conn.prepareStatement(SQL);
				insert.setString(1, input.getMap());
				insert.setDouble(2, input.getX());
				insert.setDouble(3, input.getY());
				insert.setString(4, MAC[i]);
				insert.setInt	(5, RSS[i]);
				
				insert.setString(6, input.getOS());
				insert.setString(7, input.getDevice());
				insert.setString(8, input.getModel());
				insert.setString(9, input.getProduct());
				insert.setTimestamp(10, date);
				
				//Execute and Close SQL Command
				insert.execute();
				insert.close();
			}
			
			// DO NOT FORGET TO COMMIT!!
			conn.prepareCall("commit;").execute();
			return true;
		}
		catch(SQLException | ClassNotFoundException cnf) {
			logger.fatal("Error inserting new fingerprint entry to the training database: {}", cnf.getMessage());
			return false;
		}
	}
	
	public static String [] getColumnMAC(String map) {
		List<String> common_aps = new ArrayList<>();
		try {
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement st = conn.createStatement();

			ResultSet rs;
			if(server.preprocessed) {
				List<String> tables = new ArrayList<>();
				// All tables will have same AP columns 
				// show tables in fiu where tables_in_fiu != 'trainingpoints';
				rs = st.executeQuery("SHOW tables in " + DB + " where tables_in_" + DB + " != '" + TRAININGDATA + "'");
				while (rs.next()) {
					tables.add(rs.getString("Tables_in_" + DB));
				}
				String table = tables.get(0).replace(" ", "");
				table = table.replace("-", "");
				// Now use a regular query
				rs = st.executeQuery("SHOW COLUMNS FROM " + DB + "." + table + " ;");
				int counter = 1;
				
				while (rs.next()) {
					// skip ID(1), XCoordinate(2) and YCoordinate(3)!
					if(counter != 4) {
						++counter;
					} else {
						common_aps.add(rs.getString("Field"));
					}
				}
                common_aps.replaceAll(LocalizationLUT::getColumnName);
			}
			else {
				// This will be called when it is time to create the tables
				if(Distance.VECTOR_SIZE == -1) {
					// How many AP columns will we have?
					Distance.VECTOR_SIZE = getVectorSize(Distance.FSF);
				}
				// Used to build the Lookup Columns for each most frequently seen AP
				/*
				 * SELECT MACADDRESS, COUNT(MACADDRESS) AS count
				 * FROM fiu.trainingpoints
				 * WHERE Map = 'BWY_FL_03'
				 * GROUP BY MACADDRESS
				 * ORDER BY count DESC
				 * LIMIT 20;
				 */
				PreparedStatement state = conn.prepareStatement(
						"SELECT MACADDRESS, Count(MACADDRESS) as count "
						+ "from " + DB + "." + TRAININGDATA + " "
						+ "Where Map= ?"
						+ "group by MACADDRESS "
						+ "ORDER BY count DESC LIMIT " + Distance.VECTOR_SIZE + ";");
				state.setString(1, map);
				rs = state.executeQuery();
				while (rs.next()) {
					common_aps.add(rs.getString("MACADDRESS"));
				}
			}
			return common_aps.toArray(new String[0]);
		}
		catch(SQLException | ClassNotFoundException cnf) {
			logger.fatal("Error collecting the Mac Addresses on the lookup table: {}", cnf.getMessage());
			return null;
		}
	}
	
	public static HashMap<String, Integer> getCommonMac() {
		/*
		Do an SQL Statement to find what are the 10 most prominent MAC Addresses:
		SELECT MACADDRESS, Count(MACADDRESS) as count 
		from FIU.trainingpoints
		group by MACADDRESS
		ORDER BY count DESC
		LIMIT 10;
		 */

		HashMap<String, Integer> frequency_map = new HashMap<>();
		try {
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			PreparedStatement st = conn.prepareStatement(
					"SELECT MACADDRESS, Count(MACADDRESS) as count from "
					+ DB + "." + TRAININGDATA + " group by MACADDRESS ORDER BY count DESC;");

			// execute the query, and get a java result set
			ResultSet rs = st.executeQuery();
	
			while (rs.next()) {
				frequency_map.put(rs.getString("MACADDRESS"), rs.getInt(2));
			}
			return frequency_map;
		} catch(SQLException | ClassNotFoundException cnf) {
			logger.fatal("Error submitting training data: {}", cnf.getMessage());
			return null;
		}
	}

	/*
	 * 	Input: Nothing
	 * 
	 	Purpose of Method:
	 * 	Get all distinct pairs of x, y coordinates.
	 * 	1- Use this Method on TrainingActivity to know
	 * 	which points have already been trained...
	 * 	2- Use this method to get all distinct XY coordinates for Lookup table creation
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
	public static Double [] getX(String Map) 
			throws ClassNotFoundException, SQLException {
		Double [] X;
		ArrayList<Double> x = new ArrayList<>();

		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		// execute the query, and get a java result set
		/*
		SELECT DISTINCT
    		Xcoordinate, Ycoordinate
		FROM
    		fiu.trainingpoints
		WHERE
    		Map = 'BWY_FL_3'
		ORDER BY Xcoordinate ASC;
		 */
		PreparedStatement st = conn.prepareStatement(
				"select distinct Xcoordinate, Ycoordinate "
				+ "from " + DB + "." + TRAININGDATA + " "
				+ "where Map=? "
				+ "Order By Xcoordinate ASC;");
		st.setString(1, Map);
		ResultSet rs = st.executeQuery();
		while (rs.next()) {
			x.add(rs.getDouble("Xcoordinate"));
		}
		X = x.toArray(new Double[0]);
		return X;
	}
	
	public static Double [] getY(String Map) 
			throws ClassNotFoundException, SQLException {
		Double [] Y;
		ArrayList<Double> y = new ArrayList<>();
	
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);

		PreparedStatement st = conn.prepareStatement(
				"select distinct Xcoordinate, Ycoordinate "
				+ "from " + DB + "." + TRAININGDATA + " "
				+ "where Map=? "
				+ "Order By Xcoordinate ASC;");
		st.setString(1, Map);
		ResultSet rs = st.executeQuery();
		while (rs.next()) {
			y.add(rs.getDouble("Ycoordinate"));
		}
		Y = y.toArray(new Double[0]);
		return Y;
	}
	
	public static String [] getMaps() 
			throws ClassNotFoundException, SQLException {
		String [] maps;
		List<String> list_maps = new ArrayList<>();
	
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);

		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery("select distinct Map "
				+ "from " + DB + "." + TRAININGDATA);
		while (rs.next()) {
			list_maps.add(rs.getString("Map"));
		}
		maps = list_maps.toArray(new String[0]);
		return maps;
	}
	
	public static boolean createTrainingTable() {
		try {
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();

			try {
				stmt.execute("CREATE DATABASE " + DB);	
			}
			finally {
				// Ok, the database exists already, but tries to make table now...
				String sqlTrain = "CREATE TABLE " + DB + "." + TRAININGDATA + " " +
						"( " +
						"Map Text not null, " +
						"Xcoordinate Double not null, " +
						"YCoordinate Double not null, " +
						"MACADDRESS Text not null, " +
						"RSS Integer not null, " +
						"OS Text not null, "  +
						"Device Text not null, " +
						"Model Text not null, " +
						"Product Text not null, " + 
						"currentTime DATETIME not null " +
						");";
				stmt.executeUpdate(sqlTrain);
			}
		}
		catch(SQLException | ClassNotFoundException cnf) {
			logger.warn("Failed to create Training Table: {}", cnf.getMessage());
			return false;
		}
		return true;
	}
	 
	public static boolean createTables() {
		try {
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();
			
			String [] maps = getMaps();
			for (String map: maps) {
				// BUILD ONE TABLE FOR ALL PHONES
				String [] ColumnNames = getColumnMAC(map);
				String sql =
						"CREATE TABLE " + DB + "." + map +
						"("
						+ "ID INTEGER not NULL, "
						+ " Xcoordinate DOUBLE not NULL, "
						+ " Ycoordinate DOUBLE not NULL, ";
				StringBuilder add = new StringBuilder();
				for (int i = 0; i < Distance.VECTOR_SIZE; i++) {
                    assert ColumnNames != null;
                    add.append(makeColumnName(ColumnNames[i])).append(" INTEGER not NULL,");
				}
				
				sql += add;
				sql +=" PRIMARY KEY (ID));"; 
				stmt.executeUpdate(sql);	
			}
			return true;
		}
		catch(SQLException | ClassNotFoundException se) {
			logger.fatal("Failed to create Training Table: {}", se.getMessage());
			return false;
		}
	}
	
	public static boolean isProcessed() throws ClassNotFoundException, SQLException {
		int bool = -1;
		String query = "";
		query += "SELECT COUNT(*)\n";
		query += "FROM information_schema.tables \n";
		query += "WHERE table_schema = '" + DB + "' \n";
		
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		Statement stmt = conn.createStatement();
		ResultSet answer = stmt.executeQuery(query);
		while (answer.next()) {
			bool = answer.getInt(1);
		}
		// 1 Table implies only training point table found
		// 2 or more implies that LUTs built
		return bool >= 2;
	}
	
	// PROCESS LUT
	public static boolean UpdatePlainLUT() {
		try {
			// Init
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			String [] maps = getMaps();
			for (String map: maps) {
				// Acquire All Data to Create Plain Text Lookup Table	
				Double [] X = getX(map);
				Double [] Y = getY(map);
				String [] CommonMac = getColumnMAC(map);
				int [][] Pinsert = new int [X.length][Distance.VECTOR_SIZE];
							
				PreparedStatement Plainst; 
				String getRSS;
				ResultSet RSS;
				for (int x = 0; x < X.length; x++) {
					/*
			 		select RSS FROM TRAININGDATA
					WHERE Xcoordinate = 227.761 
					AND YCoordinate = 1095.73 
					AND MACADDRESS = '84:1b:5e:4b:80:e2';
					 */
					
					for (int currentCol = 0; currentCol < Distance.VECTOR_SIZE; currentCol++) {
						getRSS = "SELECT RSS FROM " + DB + "." + TRAININGDATA + " "
								+ "WHERE Xcoordinate = ? "
								+ "AND Ycoordinate = ? "
								+ "AND MACADDRESS = ? "
								+ "AND Map = ? "
								+ ";";
									
						Plainst = conn.prepareStatement(getRSS);
						Plainst.setDouble(1, X[x]);
						Plainst.setDouble(2, Y[x]);
                        assert CommonMac != null;
                        Plainst.setString(3, CommonMac[currentCol]);
						Plainst.setString(4, map);
						RSS = Plainst.executeQuery();
						while (RSS.next()) {
							Pinsert [x][currentCol] = RSS.getInt("RSS");
						}
						
						//CHECK IF I GOT A NULL!
						if (Pinsert[x][currentCol] == 0) {
							Pinsert[x][currentCol] = Distance.v_c;
						}
						Plainst.close();		
					}
				}
				
				// -----------------Place data----------------------------------------
				StringBuilder append = new StringBuilder();
                append.append(" ?,".repeat(Math.max(0, Distance.VECTOR_SIZE)));
				append = new StringBuilder(append.substring(0, append.length() - 1));
				append.append(");");
				
				//The Insert Statement for Plain Text
				String PlainQuery = "insert into " + DB + "." + map
				+ " values (?, ?, ?," + append;
				
				PreparedStatement Plain;

				for (int PrimaryKey = 0; PrimaryKey < X.length; PrimaryKey++) {
					if(isNullTuple(Pinsert[PrimaryKey])) {
						continue;
					}
					Plain = conn.prepareStatement(PlainQuery);
					
					//Fill up the PlainText Table Part 1
					Plain.setInt (1, PrimaryKey + 1);
					Plain.setDouble(2, X[PrimaryKey]);
					Plain.setDouble(3, Y[PrimaryKey]);
					
					for (int j = 0; j < Distance.VECTOR_SIZE;j++) {
						Plain.setInt((j + 4), Pinsert[PrimaryKey][j]);
					}
					Plain.execute();
					Plain.close();
				}
				
				//DONT FORGET TO COMMIT!!!
				Statement commit = conn.createStatement();
				commit.executeQuery("commit;");
				conn.close();
			}
			return true;
		}
		catch(SQLException | ClassNotFoundException cnf) {
			logger.warn("Failed to complete Lookup Table: {}", cnf.getMessage());
			return false;
		}
	}
	
	/*	
 	Input: A Row of size 10 of RSS values
 	
 	Purpose of Method: 
 	The lookup table shouldn't have any points consisting of all RSS = -120
 	That can cause errors. 
 	To avoid this, if a row is detected full of nulls, return true.
 	If it returns true, the Insert into Lookup Tables will omit this row.

	Potential Errors:
	
	Returns:
	True/False to UpdateTables()
	*/
	public static boolean isNullTuple(int [] row) {
		int counter = 0;
        for (int j : row) {
            if (j == -120) {
                ++counter;
            }
        }
		return counter == Distance.VECTOR_SIZE;
	}
	
	public static void printTrainingData() throws SQLException, FileNotFoundException {
		String Q1 = "SELECT * FROM " + DB + "." + TRAININGDATA;
		String PointsCSV = "./TrainingPoints.csv";
		try (
				Connection conn = DriverManager.getConnection(URL, username, password);
				Statement stFour = conn.createStatement();
				ResultSet dataSet = stFour.executeQuery(Q1);
				PrintWriter WritePoints = new PrintWriter(
						new BufferedWriter(
								new OutputStreamWriter(
										new FileOutputStream(PointsCSV))))
		) {
			WritePoints.println("Xcoordinate,Ycoordinate,AP,RSS,OS,Device,Model,Product,ScanTime");
			StringBuilder tuple = new StringBuilder();
			while (dataSet.next()) {
				tuple.append(dataSet.getDouble("Xcoordinate")).append(",");
				tuple.append(dataSet.getDouble("Ycoordinate")).append(",");
				tuple.append(dataSet.getString("MACADDRESS")).append(",");
				tuple.append(dataSet.getInt("RSS")).append(",");
				tuple.append(dataSet.getString("OS")).append(",");
				tuple.append(dataSet.getString("Device")).append(",");
				tuple.append(dataSet.getString("Model")).append(",");
				tuple.append(dataSet.getString("Product")).append(",");
				tuple.append(dataSet.getTimestamp("currentTime").toString()).append(",");
				tuple.append(dataSet.getString("Map"));
				WritePoints.println(tuple);
				tuple = new StringBuilder();
			}
		}
	}
	
	public static void printLUT() {
		try {
			String [] all_maps = getMaps();
			for(String map: all_maps) {
				String [] ColumnMac = getColumnMAC(map);
				StringBuilder header = new StringBuilder("Xcoordinate,Ycoordinate,");
				for(int i = 0; i < Distance.VECTOR_SIZE; i++) {
					assert ColumnMac != null;
					if(i == Distance.VECTOR_SIZE - 1) {
                        header.append(ColumnMac[i]);
					} else {
						header.append(ColumnMac[i]).append(",");
					}
				}
				
				String Q3 = "SELECT * FROM " + DB + "." + map;
				String PlainCSV = "./" + map + "_LUT.csv";

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

				StringBuilder tuple = new StringBuilder();

				while(PlainResult.next()) {
					// Skip ID, 1
					tuple.append(PlainResult.getDouble(2)).append(",");
					tuple.append(PlainResult.getDouble(3)).append(",");
					for (int i = 0; i < Distance.VECTOR_SIZE; i++) {
						String name = meta.getColumnName(i+4);
						tuple.append(PlainResult.getInt(name)).append(",");
					}
					// Delete extra ,
					tuple = new StringBuilder(tuple.substring(0, tuple.length() - 1));
					WritePlain.println(tuple);
					tuple = new StringBuilder();
				}
				WritePlain.close();
			}
		}
		catch(IOException | SQLException | ClassNotFoundException cnf) {
			logger.warn("Failed to print the contents of the lookup table: {}", cnf.getMessage());
		}
	}
	
	// HARD RESET: RE-TRAIN EVERYTHING!
	// Delete ALL LUT!
	// Can it be made into an administrator button?
	public static boolean reset() {
		try {
			Class.forName(myDriver);
		
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();

			// show tables in fiu where tables_in_fiu != 'trainingpoints';
			ResultSet rs = stmt.executeQuery("SHOW tables in " + DB + " where tables_in_" + DB + " != '" + TRAININGDATA + "'");
			List<String> tables = new ArrayList<>();
			while (rs.next()) {
				tables.add(rs.getString("Tables_in_" + DB));
			}
			for (String table: tables) {
				table = table.replace(" ", "");
				table = table.replace("-", "");
				stmt.executeUpdate("DROP TABLE " + DB + "." + table + ";");	
			}
			stmt.executeUpdate("commit;");
			stmt.close();
			return true;
		}
		catch(SQLException | ClassNotFoundException cnf) {
			logger.warn("failed to delete every table in the database {} database: {}", DB, cnf.getMessage());
			return false;
		}
	}
	
	/*
	 *  Will undo last insert into Training Points.
	 *  To do this correctly for multiple devices, you might need a column to uniquely identify a device?
	 *  Assuming you grant the right for one phone to delete...
	 */
	
	public static boolean undo(Double [] coordinate, String map, String device) {
		try {
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			String sql = 
					"DELETE FROM " + DB + "." + TRAININGDATA + " " +
					"where Xcoordinate = ? AND " +
					"Ycoordinate = ? AND " +
					"Map= ? AND " +
					"Model= ? ;";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setDouble(1, coordinate[0]);
			stmt.setDouble(2, coordinate[1]);
			stmt.setString(3, map);
			stmt.setString(4, device);
			int rows_updated = stmt.executeUpdate();
			//Save the Changes...
			stmt.executeUpdate("commit;");
			stmt.close();
			return rows_updated != 0;
		} catch(SQLException | ClassNotFoundException  e) {
			logger.warn("Failed to undo last insert: {}", e.getMessage());
			return false;
		}
    }
	
	// http://www.dummies.com/education/math/statistics/how-to-calculate-percentiles-in-statistics/
	// Only accept from 0 - 1.
	public static int getVectorSize(double percentile) {
		if(percentile < 0 || percentile > 1) {
			return -1;
		}
		
		ArrayList<Integer> AP_count = new ArrayList<>();
		try {
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();
			
			String query = "SELECT Count(MACADDRESS) as count from" + '\n';
			query += DB + "." + TRAININGDATA + '\n';
			query += "group by MACADDRESS" + '\n';
			query += "ORDER BY count ASC" + '\n';
			ResultSet vec = stmt.executeQuery(query);
			
			while(vec.next()) {
				AP_count.add(vec.getInt("count"));	
			}
			stmt.close();
		}
		catch(SQLException | ClassNotFoundException cnf) {
			logger.warn("Failed to the new vector size: {}", cnf.getMessage());
		}
		// same as get IDX of Percentile, Note the int is already sorted!
		int num_AP_filtered = (int) Math.ceil(percentile * AP_count.size());
		return AP_count.size() - num_AP_filtered;
	}
	
	public static void getAPManufacturer() 
			throws IOException, InterruptedException, SQLException, ClassNotFoundException {
		// Should be done before Lookup Tables are made...
		// Get all APs from MySQL database
		
		ArrayList<String> APs = new ArrayList<>();
		String [] Makers;
		
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery("SELECT MACADDRESS from " + DB + "." + TRAININGDATA + ";");
		
		while (rs.next()) {
			APs.add(rs.getString("MACADDRESS"));
		}
		Makers = new String[APs.size()];
		
		// Now get the Manufacturer of all the AP's
		for (int i = 0; i < APs.size(); i++) {
			StringBuilder result = new StringBuilder();
			URL url = new URL("http://api.macvendors.com/" + APs.get(i));
			HttpURLConnection connect = (HttpURLConnection) url.openConnection();
			connect.setRequestMethod("GET");
			BufferedReader rd = new BufferedReader(new InputStreamReader(connect.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}
			Makers[i] = line;
			rd.close();
			Thread.sleep(1200);//No API 1 request a second, add .2 as Slack
		}
	}
	
	public static void getPlainLookup(List<Long[]> SQLData, List<Double[]> coordinates, String map)
			throws ClassNotFoundException, SQLException {
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		PreparedStatement st = conn.prepareStatement("select * from " + DB + "." + map + " "
				+ "Order By Xcoordinate ASC;");
		ResultSet rs = st.executeQuery();
		
		while(rs.next()) {
			Long [] RSS = new Long [Distance.VECTOR_SIZE];
			Double [] Location = new Double [2];
			Location[0] = rs.getDouble("Xcoordinate");	// 2
			Location[1] = rs.getDouble("Ycoordinate");	// 3
			// Start with 4....
			for (int i = 0; i < Distance.VECTOR_SIZE; i++) {
				RSS[i] = (long) rs.getInt(i + 4);
			}
			SQLData.add(RSS);
			coordinates.add(Location);
		}
	}
	
	// All Column names in MySQL can't start with a number,
	// So if the MAC Address starts with 1-9, swap it!
	protected static String makeColumnName(String column) {
		String answer = "";
		if(column == null || column.isEmpty()) {
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
		
		if(Character.isDigit(first)) {
			char alphabet = (char) (((int) first) + 65);
			answer = alphabet + column.substring(1);
			answer = answer.replace(':', '_');
        } else {
			answer = column.replace(':', '_');
        }
        return answer;
    }
	
	// All Column names in MySQL can't start with a number,
	// So if the MAC Address starts with 1-9, swap it!
	protected static String getColumnName(String column) {
		String answer = "";
		if(column == null || column.isEmpty()) {
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
		if("qrstuvwxyz".indexOf(first) != -1) {
			char alphabet = (char) (((int) first) - 65);
			answer = alphabet + column.substring(1);
			answer = answer.replace('_', ':');
        } else {
			answer = column.replace('_', ':');
        }
        return answer;
    }
}