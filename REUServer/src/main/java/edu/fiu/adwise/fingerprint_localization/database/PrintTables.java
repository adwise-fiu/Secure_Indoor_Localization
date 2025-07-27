package edu.fiu.adwise.fingerprint_localization.database;

/*
 * Copyright (c) 2025 Andrew Quijano
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.sql.*;

/**
 * Utility class for exporting database tables to CSV files for analysis or backup.
 * <p>
 * The {@code PrintTables} class provides static methods to export both the training data
 * and lookup tables from the fingerprint localization database to CSV files. These exports
 * facilitate offline analysis, debugging, and data sharing.
 * </p>
 * <ul>
 *   <li>{@link #printTrainingData()} exports all training points to {@code TrainingPoints.csv}.</li>
 *   <li>{@link #printLookupTables()} exports each map's lookup table to a separate CSV file named {@code &lt;map&gt;_LUT.csv}.</li>
 * </ul>
 * <b>Note:</b> All methods handle database connections and file I/O using try-with-resources for safety and efficiency.
 * Errors are logged using Log4j.
 */
public class PrintTables extends FingerprintDbUtils {
    private static final Logger logger = LogManager.getLogger(PrintTables.class);

    /**
     * Exports all training data from the database to a CSV file.
     * <p>
     * This method queries the entire training data table and writes each row to a CSV file
     * named {@code TrainingPoints.csv}. The CSV includes columns for coordinates, MAC address,
     * RSS, device information, timestamp, and floor map.
     * </p>
     *
     * @throws SQLException if a database access error occurs
     * @throws FileNotFoundException if the output file cannot be created or opened
     */
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

    /**
     * Exports all lookup tables for each map to individual CSV files.
     * <p>
     * For each map, this method retrieves the lookup table from the database and writes its contents
     * to a CSV file named {@code <map>_LUT.csv}. The CSV includes columns for coordinates and all AP MAC addresses.
     * </p>
     * If an error occurs during processing, a warning is logged.
     */
    public static void printLookupTables() {
        try {
            String[] all_maps = getMaps();
            for (String map : all_maps) {
                String [] ColumnMac = getColumnMAC(map);
                StringBuilder header = new StringBuilder("Xcoordinate,Ycoordinate,");
                assert ColumnMac != null;
                for (int i = 0; i < ColumnMac.length; i++) {
                    if (i == ColumnMac.length - 1) {
                        header.append(ColumnMac[i]);
                    } else {
                        header.append(ColumnMac[i]).append(",");
                    }
                }

                String Q3 = "SELECT * FROM " + DB + "." + map;
                String PlainCSV = "./" + map + "_LUT.csv";

                Class.forName(myDriver);
                try (
                        Connection conn = DriverManager.getConnection(URL, username, password);
                        Statement stTwo = conn.createStatement();
                        ResultSet PlainResult = stTwo.executeQuery(Q3);
                        PrintWriter WritePlain = new PrintWriter(
                                new BufferedWriter(
                                        new OutputStreamWriter(
                                                new FileOutputStream(PlainCSV))))
                ) {
                    ResultSetMetaData meta = PlainResult.getMetaData();
                    WritePlain.println(header);

                    StringBuilder tuple = new StringBuilder();
                    while (PlainResult.next()) {
                        // Skip ID, 1
                        tuple.append(PlainResult.getDouble(2)).append(",");
                        tuple.append(PlainResult.getDouble(3)).append(",");
                        for (int i = 0; i < ColumnMac.length; i++) {
                            String name = meta.getColumnName(i + 4);
                            tuple.append(PlainResult.getInt(name)).append(",");
                        }
                        // Delete extra ,
                        tuple = new StringBuilder(tuple.substring(0, tuple.length() - 1));
                        WritePlain.println(tuple);
                        tuple = new StringBuilder();
                    }
                }
            }
        } catch (IOException | SQLException | ClassNotFoundException cnf) {
            logger.warn("Failed to print the contents of the lookup table: {}", cnf.getMessage());
        }
    }

}
