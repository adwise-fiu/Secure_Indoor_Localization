package edu.fiu.adwise.fingerprint_localization.database;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import edu.fiu.adwise.fingerprint_localization.structs.SendTrainingData;
import edu.fiu.adwise.fingerprint_localization.distance_computation.Distance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.*;


/**
 * Utility class for building and managing lookup tables for Wi-Fi fingerprint localization.
 *
 * <p>
 * The {@code LocalizationLUT} class provides static methods to create, populate, and manage
 * lookup tables used in Wi-Fi fingerprint-based localization systems. These tables map
 * physical coordinates to RSS (Received Signal Strength) vectors for the most common access points.
 * </p>
 *
 * <b>Assumptions for using this class:</b>
 * <ul>
 *   <li>The table named <code>map</code> (or equivalent) exists in the database.</li>
 *   <li>The lookup table has the structure: X, Y, and columns for the top N MAC addresses (e.g., 10).</li>
 *   <li>Training data is already loaded into a MySQL server and available for processing.</li>
 *   <li>Column names in the database match those expected by the class methods.</li>
 * </ul>
 *
 * <b>Note:</b> If MySQL Server Safe Mode is enabled, you may need to run:
 * <pre>
 * SET SQL_SAFE_UPDATES = 0;
 * </pre>
 * to allow certain update or delete operations.
 *
 * <p>
 * This class extends {@link FingerprintDbUtils} and uses JDBC for database operations.
 * All database and file resources are managed using try-with-resources where applicable.
 * Errors and warnings are logged using Log4j.
 * </p>
 */
public class LocalizationLUT extends FingerprintDbUtils {
	private static final Logger logger = LogManager.getLogger(LocalizationLUT.class);

	/**
	 * Creates the training table in the specified database.
	 * If the database does not exist, it will be created.
	 * The table structure is defined by the TRAININGDATA variable.
	 *
	 * @return true if the table was created successfully, false otherwise
	 */
	public static boolean createTrainingTable() {
		try {
			Class.forName(myDriver);
			// Validate DB and TRAININGDATA to contain only allowed characters
			if (!DB.matches("\\w+") || !TRAININGDATA.matches("\\w+")) {
				throw new SQLException("Invalid database or table name.");
			}

			// Use try-with-resources for automatic resource management
			try (Connection conn = DriverManager.getConnection(URL, username, password);
				 Statement stmt = conn.createStatement()) {

				// Parameterize the database name using PreparedStatement for safety
				String createDbSQL = "CREATE DATABASE IF NOT EXISTS ?";
				try (PreparedStatement createDbStmt = conn.prepareStatement(createDbSQL)) {
					createDbStmt.setString(1, DB);
					createDbStmt.execute();
				}

				String sqlTrain = "CREATE TABLE IF NOT EXISTS " + DB + "." + TRAININGDATA + " " +
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
                //noinspection SqlSourceToSinkFlow
                stmt.executeUpdate(sqlTrain);
			}
		} catch (SQLException | ClassNotFoundException cnf) {
			logger.warn("Failed to create Training Table: {}", cnf.getMessage());
			return false;
		}
		return true;
	}

	/**
	 * Submits training data to the MySQL training table.
	 * Each entry includes location, MAC address, RSS, device info, and timestamp.
	 * Input: Training Array
	 * - Xcoordiante of Training Location
	 * - Ycoordinate of Training Location
	 * - Array of RSS
	 * - Array of AP's
	 *
	 * @param input the training data to submit
	 * @return true if the data was inserted successfully, false otherwise
	 */
	@SuppressWarnings("SqlSourceToSinkFlow")
    public static boolean submitTrainingData(SendTrainingData input) {
		try {
			Class.forName(myDriver);
			// Validate DB and TRAININGDATA names to prevent SQL injection
			if (!DB.matches("\\w+") || !TRAININGDATA.matches("\\w+")) {
				throw new SQLException("Invalid database or table name.");
			}

			String SQL = "INSERT INTO `" + DB + "`.`" + TRAININGDATA + "` VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			String[] MAC = input.getMACAddress();
			Integer[] RSS = input.getRSS();
			java.sql.Timestamp date = new java.sql.Timestamp(new java.util.Date().getTime());

			try (Connection conn = DriverManager.getConnection(URL, username, password)) {
				conn.setAutoCommit(false);

				for (int i = 0; i < MAC.length; i++) {
					try (PreparedStatement insert = conn.prepareStatement(SQL)) {
						insert.setString(1, input.getMap());
						insert.setDouble(2, input.getX());
						insert.setDouble(3, input.getY());
						insert.setString(4, MAC[i]);
						insert.setInt(5, RSS[i]);
						insert.setString(6, input.getOS());
						insert.setString(7, input.getDevice());
						insert.setString(8, input.getModel());
						insert.setString(9, input.getProduct());
						insert.setTimestamp(10, date);
						insert.execute();
					} catch (SQLException e) {
						logger.error("Error executing insert for MAC {}: {}", MAC[i], e.getMessage());
						conn.rollback();
						return false;
					}
				}
				conn.commit();
				return true;
			} catch (SQLException e) {
				logger.error("Database connection or transaction error: {}", e.getMessage());
				return false;
			}
		} catch (SQLException | ClassNotFoundException e) {
			logger.fatal("Error inserting training data: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Retrieves a frequency map of the most common MAC addresses in the training data.
	 * <p>
	 * Executes a SQL query to count occurrences of each MAC address, ordered by frequency.
	 * The result can be used to select the top N MAC addresses for use as columns in lookup tables.
	 * </p>
	 * <b>Example SQL to find the 10 most prominent MAC addresses:</b>
	 * <pre>
	 * SELECT MACADDRESS, Count(MACADDRESS) as count
	 *   FROM FIU.trainingpoints
	 *   GROUP BY MACADDRESS
	 *   ORDER BY count DESC
	 *   LIMIT 10;
	 * </pre>
	 * <b>Note:</b> The distribution of MAC address frequencies may be highly skewed.
	 * Choosing the number of columns (e.g., top 10, top 20) should consider the percentile
	 * at which the frequency drops off, to balance between coverage and table sparsity.
	 * Analyzing this map can help determine an appropriate cutoff for your application.
	 *
	 * @return a {@code HashMap} mapping each MAC address to its occurrence count, or {@code null} on error
	 */
	public static HashMap<String, Integer> getCommonMac() {
		HashMap<String, Integer> frequency_map = new HashMap<>();
		try {
			Class.forName(myDriver);
			try (Connection conn = DriverManager.getConnection(URL, username, password);
				 PreparedStatement st = conn.prepareStatement(
						 "SELECT MACADDRESS, Count(MACADDRESS) as count from "
								 + DB + "." + TRAININGDATA + " group by MACADDRESS ORDER BY count DESC;");
				 ResultSet rs = st.executeQuery()) {

				while (rs.next()) {
					frequency_map.put(rs.getString("MACADDRESS"), rs.getInt(2));
				}
				return frequency_map;
			}
		} catch (SQLException | ClassNotFoundException cnf) {
			logger.fatal("obtaining a frequency map of MAC addresses from the training data: {}", cnf.getMessage());
			return null;
		}
	}

	/**
	 * Retrieves a list of distinct X or Y coordinates for a given map from the training data table.
	 * <p>
	 * The column parameter must be either "Xcoordinate" or "Ycoordinate" to prevent SQL injection.
	 * </p>
	 *
	 * @param map the map name to filter the results
	 * @param column the column to retrieve ("Xcoordinate" or "Ycoordinate")
	 * @return an array of Double values from the specified column
	 * @throws ClassNotFoundException if the JDBC driver class is not found
	 * @throws SQLException if a database access error occurs
	 * @throws IllegalArgumentException if the column name is invalid
	 */
	public static Double[] get_xy(String map, String column)
			throws ClassNotFoundException, SQLException {
		// Only allow specific column names to prevent SQL injection
		if (!"Xcoordinate".equals(column) && !"Ycoordinate".equals(column)) {
			throw new IllegalArgumentException("Invalid column name: " + column);
		}
		List<Double> y = new ArrayList<>();
		Class.forName(myDriver);
		String query = "SELECT DISTINCT Xcoordinate, Ycoordinate "
				+ "FROM " + DB + "." + TRAININGDATA + " "
				+ "WHERE Map=? "
				+ "ORDER BY Xcoordinate ASC;";
		try (Connection conn = DriverManager.getConnection(URL, username, password);
			 PreparedStatement st = conn.prepareStatement(query)) {
			st.setString(1, map);
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					y.add(rs.getDouble(column));
				}
			}
		}
		return y.toArray(new Double[0]);
	}

	/**
	 * Populates the lookup tables for each map using training data.
	 * <p>
	 * For each map, this method:
	 * <ul>
	 *   <li>Retrieves all unique X and Y coordinates and the most common MAC addresses.</li>
	 *   <li>Queries the RSS values for each coordinate and MAC address combination.</li>
	 *   <li>Inserts the resulting RSS vectors into the corresponding lookup table.</li>
	 * </ul>
	 * Rows with only null RSS values are skipped.
	 * All database and statement resources are managed automatically.
	 *
	 * @return {@code true} if the lookup tables were built successfully, {@code false} otherwise
	 */
	public static boolean UpdatePlainLUT() {
		try {
			Class.forName(myDriver);
			try (Connection conn = DriverManager.getConnection(URL, username, password)) {
				String[] maps = getMaps();
				for (String map : maps) {
					Double[] X = get_xy(map, "Xcoordinate");
					Double[] Y = get_xy(map, "Ycoordinate");
					String[] CommonMac = getColumnMAC(map);
					int[][] Pinsert = new int[X.length][Distance.VECTOR_SIZE];

					// This collects the information for the Lookup Table
					for (int x = 0; x < X.length; x++) {
						for (int currentCol = 0; currentCol < Distance.VECTOR_SIZE; currentCol++) {
							String getRSS = "SELECT RSS FROM " + DB + "." + TRAININGDATA + " "
									+ "WHERE Xcoordinate = ? "
									+ "AND Ycoordinate = ? "
									+ "AND MACADDRESS = ? "
									+ "AND Map = ? "
									+ ";";
							try (PreparedStatement Plainst = conn.prepareStatement(getRSS)) {
								Plainst.setDouble(1, X[x]);
								Plainst.setDouble(2, Y[x]);
								assert CommonMac != null;
								Plainst.setString(3, CommonMac[currentCol]);
								Plainst.setString(4, map);
								try (ResultSet RSS = Plainst.executeQuery()) {
									while (RSS.next()) {
										Pinsert[x][currentCol] = RSS.getInt("RSS");
									}
								}
							}
							// CHECK IF I GOT A NULL!
							if (Pinsert[x][currentCol] == 0) {
								Pinsert[x][currentCol] = Distance.v_c;
							}
						}
					}

					// This is the SQL Query to insert into the Lookup tables
					StringBuilder append = new StringBuilder();
					append.append(" ?,".repeat(Math.max(0, Distance.VECTOR_SIZE)));
					append = new StringBuilder(append.substring(0, append.length() - 1));
					append.append(");");

					String PlainQuery = "insert into " + DB + "." + map
							+ " values (?, ?, ?," + append;

					for (int PrimaryKey = 0; PrimaryKey < X.length; PrimaryKey++) {
						if (isNullTuple(Pinsert[PrimaryKey])) {
							continue;
						}
						try (PreparedStatement Plain = conn.prepareStatement(PlainQuery)) {
							Plain.setInt(1, PrimaryKey + 1);
							Plain.setDouble(2, X[PrimaryKey]);
							Plain.setDouble(3, Y[PrimaryKey]);
							for (int j = 0; j < Distance.VECTOR_SIZE; j++) {
								Plain.setInt((j + 4), Pinsert[PrimaryKey][j]);
							}
							Plain.execute();
						}
					}
					conn.commit();
				}
			}
			return true;
		} catch (SQLException | ClassNotFoundException cnf) {
			logger.warn("Failed to complete Lookup Table: {}", cnf.getMessage());
			return false;
		}
	}

	/**
	 * Checks if a row of RSS values consists entirely of the null value, v_c (-120).
	 * <p>
	 * This is used to prevent inserting rows into the lookup table that contain only null RSS values,
	 * which could cause errors in later processing.
	 * </p>
	 *
	 * @param row an array of RSS values (expected size: {@code Distance.VECTOR_SIZE})
	 * @return {@code true} if all values in the row are -120, {@code false} otherwise
	 */
	public static boolean isNullTuple(int[] row) {
		int counter = 0;
		for (int j : row) {
			if (j == -120) {
				++counter;
			}
		}
		return counter == Distance.VECTOR_SIZE;
	}

	/**
	 * Drops all tables in the current database except the training data table.
	 * <p>
	 * This method is used to perform a hard reset, deleting all lookup tables while preserving the training data.
	 * </p>
	 *
	 * @return {@code true} if all tables except the training table were dropped successfully, {@code false} otherwise
	 */
	public static boolean reset() {
		try {
				Class.forName(myDriver);
				// Query all table names except the training table
				String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name != ?";
				try (Connection conn = DriverManager.getConnection(URL, username, password);
					 PreparedStatement stmt = conn.prepareStatement(sql)) {
					stmt.setString(1, DB);
					stmt.setString(2, TRAININGDATA);
					List<String> tables = new ArrayList<>();
					try (ResultSet rs = stmt.executeQuery()) {
						while (rs.next()) {
							tables.add(rs.getString("table_name"));
						}
					}
					// Drop each table
					for (String table : tables) {
						try (Statement dropStmt = conn.createStatement()) {
							dropStmt.executeUpdate("DROP TABLE " + DB + "." + table + ";");
						}
					}
					conn.commit();
					return true;
				}
		}
		catch(SQLException | ClassNotFoundException cnf) {
			logger.warn("failed to delete every table in the database {} database: {}", DB, cnf.getMessage());
			return false;
		}
	}

	/**
	 * Undoes the last insert into the training points table for a specific device and location.
	 * <p>
	 * Deletes the training data entry matching the given coordinates, map, and device model.
	 * </p>
	 *
	 * @param coordinate an array containing the X and Y coordinates
	 * @param map the map name to filter the deletion
	 * @param device the device model to filter the deletion
	 * @return {@code true} if a row was deleted, {@code false} otherwise
	 */
	public static boolean undo(Double[] coordinate, String map, String device) {
		try {
			Class.forName(myDriver);
			String sql =
					"DELETE FROM " + DB + "." + TRAININGDATA + " " +
							"WHERE Xcoordinate = ? AND " +
							"Ycoordinate = ? AND " +
							"Map = ? AND " +
							"Model = ? ;";
			try (
					Connection conn = DriverManager.getConnection(URL, username, password);
					PreparedStatement stmt = conn.prepareStatement(sql)
			) {
				stmt.setDouble(1, coordinate[0]);
				stmt.setDouble(2, coordinate[1]);
				stmt.setString(3, map);
				stmt.setString(4, device);
				int rows_updated = stmt.executeUpdate();
				conn.commit();
				return rows_updated != 0;
			}
		} catch (SQLException | ClassNotFoundException e) {
			logger.warn("Failed to undo last insert: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Checks if the lookup tables have been built in the database.
	 * <p>
	 * This method queries the information schema to count the number of tables
	 * in the current database schema. If only one table exists, it is assumed
	 * that only the training points table is present. If two or more tables exist,
	 * it is assumed that the lookup tables have been built.
	 * </p>
	 *
	 * @return {@code true} if two or more tables exist in the database schema (indicating LUTs are built), {@code false} otherwise
	 * @throws ClassNotFoundException if the JDBC driver class is not found
	 * @throws SQLException if a database access error occurs
	 */
	public static boolean isProcessed() throws ClassNotFoundException, SQLException {
		int bool = -1;
		String query = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?";
		Class.forName(myDriver);
		try (
				Connection conn = DriverManager.getConnection(URL, username, password);
				PreparedStatement stmt = conn.prepareStatement(query)
		) {
			stmt.setString(1, DB);
			try (ResultSet answer = stmt.executeQuery()) {
				while (answer.next()) {
					bool = answer.getInt(1);
				}
			}
		}
		return bool >= 2;
	}

	/**
	 * Retrieves all unique MAC addresses from the training data table and queries the manufacturer for each using the macvendors API.
	 * <p>
	 * This method should be called before building lookup tables. It fetches all MAC addresses from the database,
	 * then for each address, sends a request to the macvendors API to obtain the manufacturer name, respecting API rate limits.
	 * </p>
	 *
	 * @throws IOException if an I/O error occurs during API requests
	 * @throws InterruptedException if the thread is interrupted while sleeping between requests
	 * @throws SQLException if a database access error occurs
	 * @throws ClassNotFoundException if the JDBC driver class is not found
	 */
	public static void getAPManufacturer() 
			throws IOException, InterruptedException, SQLException, ClassNotFoundException {
		List<String> APs = new ArrayList<>();
		String[] Makers;

		Class.forName(myDriver);
		try (Connection conn = DriverManager.getConnection(URL, username, password);
			 Statement st = conn.createStatement();
			 ResultSet rs = st.executeQuery("SELECT MACADDRESS from " + DB + "." + TRAININGDATA + ";")) {

			while (rs.next()) {
				APs.add(rs.getString("MACADDRESS"));
			}
		}

		Makers = new String[APs.size()];

		// Now get the Manufacturer of all the AP's
		for (int i = 0; i < APs.size(); i++) {
			StringBuilder result = new StringBuilder();
			URL url = new URL("http://api.macvendors.com/" + APs.get(i));
			HttpURLConnection connect = (HttpURLConnection) url.openConnection();
			connect.setRequestMethod("GET");
			try (BufferedReader rd = new BufferedReader(new InputStreamReader(connect.getInputStream()))) {
				String line;
				while ((line = rd.readLine()) != null) {
					result.append(line);
				}
			}
			Makers[i] = result.toString();
			Thread.sleep(1200); // No API 1 request a second, add .2 as Slack
		}
	}

	/**
	 * Retrieves all rows from the specified lookup table and populates the provided lists with RSS vectors and coordinates.
	 * <p>
	 * For each row in the lookup table, this method extracts the X and Y coordinates and the RSS values for all access points,
	 * then adds them to the given lists. The order of the results is sorted by X coordinate.
	 * </p>
	 *
	 * @param SQLData the list to populate with RSS value arrays for each row
	 * @param coordinates the list to populate with coordinate arrays (X, Y) for each row
	 * @param map the name of the lookup table (map) to query
	 * @throws ClassNotFoundException if the JDBC driver class is not found
	 * @throws SQLException if a database access error occurs
	 */
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
}