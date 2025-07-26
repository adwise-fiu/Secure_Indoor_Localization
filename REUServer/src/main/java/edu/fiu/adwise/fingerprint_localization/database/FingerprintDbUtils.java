package edu.fiu.adwise.fingerprint_localization.database;

/*
 * Copyright (c) 2025 andre
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

import edu.fiu.adwise.fingerprint_localization.distance_computation.Distance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static edu.fiu.adwise.fingerprint_localization.database.LocalizationLUT.isProcessed;

/**
 * Utility class for managing fingerprint localization database schema and lookup tables.
 * <p>
 * The {@code FingerprintDbUtils} class provides static methods for:
 * </p>
 * <ul>
 *   <li>Retrieving the most common MAC addresses for use as lookup table columns</li>
 *   <li>Calculating the optimal vector size (number of access points) based on a percentile</li>
 *   <li>Creating lookup tables for each map in the database</li>
 *   <li>Listing all available map names in the training data</li>
 *   <li>Converting between MAC addresses and valid MySQL column names</li>
 * </ul>
 * <p>
 * Database connection parameters are loaded from environment variables, with sensible defaults.
 * All methods use JDBC and handle SQL exceptions, logging errors using Log4j.
 * </p>
 */
public class FingerprintDbUtils {
    /** MySQL username, loaded from environment variable MYSQL_USER. */
    public static String username = System.getenv("MYSQL_USER") != null ? System.getenv("MYSQL_USER") : "";

    /** MySQL password, loaded from environment variable MYSQL_PASSWORD. */
    public static String password = System.getenv("MYSQL_PASSWORD") != null ? System.getenv("MYSQL_PASSWORD") : "";

    /** Database name, loaded from environment variable DATABASE or defaults to "fiu". */
    public static String DB = System.getenv("DATABASE") != null ? System.getenv("DATABASE") : "fiu";

    /** Training data table name, loaded from environment variable TRAININGDATA or defaults to "trainingpoints". */
    protected static String TRAININGDATA = System.getenv("TRAININGDATA") != null ? System.getenv("TRAININGDATA") : "trainingpoints";

    /** MySQL JDBC driver class name. */
    public final static String myDriver = "com.mysql.cj.jdbc.Driver";

    /** MySQL JDBC connection URL. */
    public final static String URL = "jdbc:mysql://localhost:3306/?&useSSL=false";

    /** Logger instance for FingerprintDbUtils. */
    private static final Logger logger = LogManager.getLogger(FingerprintDbUtils.class);

    /**
     * Retrieves the list of MAC addresses (as column names) used for lookup tables for a given map.
     * <p>
     * If lookup tables are already processed, it fetches the column names from an existing table.
     * Otherwise, it queries the training data to determine the most frequently seen MAC addresses.
     * </p>
     *
     * @param map the map name for which to retrieve MAC address columns
     * @return an array of MAC address strings (column names), or null if an error occurs
     */
    public static String[] getColumnMAC(String map) {
        List<String> common_aps = new ArrayList<>();
        try {
            Class.forName(myDriver);
            try (Connection conn = DriverManager.getConnection(URL, username, password);
                 Statement st = conn.createStatement()) {

                ResultSet rs;
                if (isProcessed()) {
                    try (ResultSet columns = st.executeQuery("SHOW COLUMNS FROM " + DB + "." + map + " ;")) {
                        int counter = 1;
                        while (columns.next()) {
                            // skip ID(1), XCoordinate(2) and YCoordinate(3)!
                            if (counter != 4) {
                                ++counter;
                            } else {
                                common_aps.add(columns.getString("Field"));
                            }
                        }
                    }
                    common_aps.replaceAll(FingerprintDbUtils::getColumnName);
                } else {
                    if (Distance.VECTOR_SIZE == -1) {
                        Distance.VECTOR_SIZE = getVectorSize(Distance.FSF);
                    }
                    String sql = "SELECT MACADDRESS, Count(MACADDRESS) as count "
                            + "from " + DB + "." + TRAININGDATA + " "
                            + "Where Map= ? "
                            + "group by MACADDRESS "
                            + "ORDER BY count DESC LIMIT " + Distance.VECTOR_SIZE + ";";
                    try (PreparedStatement state = conn.prepareStatement(sql)) {
                        state.setString(1, map);
                        try (ResultSet result = state.executeQuery()) {
                            while (result.next()) {
                                common_aps.add(result.getString("MACADDRESS"));
                            }
                        }
                    }
                }
                return common_aps.toArray(new String[0]);
            }
        } catch (SQLException | ClassNotFoundException cnf) {
            logger.fatal("Error collecting the Mac Addresses on the lookup table: {}", cnf.getMessage());
            return null;
        }
    }

    /**
     * Calculates the vector size (number of access points to filter out) based on a given percentile.
     * <p>
     * This method queries the database for the count of occurrences of each MAC address in the training data,
     * sorts them in ascending order, and determines the number of access points to exclude using the specified percentile.
     * Only percentiles in the range [0, 1] are accepted.
     * </p>
     * Reference: <a href="http://www.dummies.com/education/math/statistics/how-to-calculate-percentiles-in-statistics/">reference</a>
     *
     * @param percentile the percentile (between 0 and 1) used to filter access points
     * @return the number of access points to keep after filtering, or -1 if the percentile is out of range
     */
    public static int getVectorSize(double percentile) {
        if(percentile < 0 || percentile > 1) {
            return -1;
        }

        List<Integer> AP_count = new ArrayList<>();
        try {
            Class.forName(myDriver);
            try (Connection conn = DriverManager.getConnection(URL, username, password);
                 Statement stmt = conn.createStatement();
                 ResultSet vec = stmt.executeQuery(
                         "SELECT Count(MACADDRESS) as count from\n" +
                                 DB + "." + TRAININGDATA + "\n" +
                                 "group by MACADDRESS\n" +
                                 "ORDER BY count ASC\n")) {

                while (vec.next()) {
                    AP_count.add(vec.getInt("count"));
                }
            }
        } catch (SQLException | ClassNotFoundException cnf) {
            logger.warn("Failed to the new vector size: {}", cnf.getMessage());
        }
// same as get IDX of Percentile, Note the int is already sorted!
        int num_AP_filtered = (int) Math.ceil(percentile * AP_count.size());
        return AP_count.size() - num_AP_filtered;
    }

    /**
     * Creates lookup tables for each map in the database.
     * Each table is named after the map and contains columns for ID, coordinates, and MAC addresses.
     * The number of MAC address columns is determined by Distance.VECTOR_SIZE.
     *
     * @return true if all tables were created successfully, false otherwise
     */
    public static boolean createTables() {
        try {
            if (!DB.matches("\\w+")) {
                throw new IllegalArgumentException("Invalid database name.");
            }
            Class.forName(myDriver);
            String[] maps = getMaps();
            try (Connection conn = DriverManager.getConnection(URL, username, password);
                 Statement stmt = conn.createStatement()) {
                for (String map : maps) {
                    String[] ColumnNames = getColumnMAC(map);
                    StringBuilder sql = new StringBuilder(
                            "CREATE TABLE " + DB + "." + map + " ("
                                    + "ID INTEGER not NULL, "
                                    + "Xcoordinate DOUBLE not NULL, "
                                    + "Ycoordinate DOUBLE not NULL, ");
                    for (int i = 0; i < Distance.VECTOR_SIZE; i++) {
                        assert ColumnNames != null;
                        sql.append(makeColumnName(ColumnNames[i])).append(" INTEGER not NULL,");
                    }
                    sql.append(" PRIMARY KEY (ID));");
                    stmt.executeUpdate(sql.toString());
                }
                return true;
            }
        } catch (SQLException | ClassNotFoundException se) {
            logger.fatal("Failed to create Training Table: {}", se.getMessage());
            return false;
        }
    }

    /**
     * Retrieves all distinct map names from the training data table.
     *
     * @return an array of unique map names present in the training data
     * @throws ClassNotFoundException if the JDBC driver class is not found
     * @throws SQLException if a database access error occurs
     */
    public static String[] getMaps() throws ClassNotFoundException, SQLException {
        if (!DB.matches("\\w+")) {
            throw new IllegalArgumentException("Invalid database name.");
        }
        if (!TRAININGDATA.matches("\\w+")) {
            throw new IllegalArgumentException("Invalid training table name.");
        }
        List<String> listMaps = new ArrayList<>();
        Class.forName(myDriver);
        try (Connection conn = DriverManager.getConnection(URL, username, password);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT Map FROM %s.%s".formatted(DB, TRAININGDATA))) {
            while (rs.next()) {
                listMaps.add(rs.getString("Map"));
            }
        }
        return listMaps.toArray(new String[0]);
    }

    /**
     * Converts a given column name (typically a MAC address) into a valid MySQL column name.
     * <p>
     * If the column name starts with a digit (0-9), the first character is mapped to a corresponding
     * lowercase letter (0 → q, 1 → r, ..., 9 → z) to ensure the column name does not start with a number.
     * All colons (:) in the column name are replaced with underscores (_).
     * </p>
     *
     * @param column the original column name (e.g., a MAC address)
     * @return a valid MySQL column name, or an empty string if the input is null or empty
     */
    protected static String makeColumnName(String column) {
        String answer = "";
        if(column == null || column.isEmpty()) {
            return answer;
        }
        char first = column.charAt(0);
        if(Character.isDigit(first)) {
            char alphabet = (char) (((int) first) + 65);
            answer = alphabet + column.substring(1);
            answer = answer.replace(':', '_');
        } else {
            answer = column.replace(':', '_');
        }
        return answer;
    }


    /**
     * Converts a MySQL column name (originally derived from a MAC address) back to its original MAC address format.
     * <p>
     * If the column name starts with a letter in the range 'q' to 'z', it is mapped back to a digit (0-9)
     * by subtracting 65 from the character's ASCII value. All underscores ('_') are replaced with colons (':').
     * This reverses the transformation applied to MAC addresses that start with a digit, ensuring valid MySQL column names.
     * </p>
     *
     * @param column the column name to convert back to a MAC address format
     * @return the original MAC address string, or an empty string if the input is null or empty
     */
    protected static String getColumnName(String column) {
        String answer = "";
        if(column == null || column.isEmpty()) {
            return answer;
        }
        char first = column.charAt(0);
        if("qrstuvwxyz".indexOf(first) != -1) {
            char alphabet = (char) (((int) first) - 65);
            answer = alphabet + column.substring(1);
            answer = answer.replace('_', ':');
        }
        else {
            answer = column.replace('_', ':');
        }
        return answer;
    }
}
