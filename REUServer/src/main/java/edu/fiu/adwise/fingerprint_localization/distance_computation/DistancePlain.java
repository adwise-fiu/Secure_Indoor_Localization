package edu.fiu.adwise.fingerprint_localization.distance_computation;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import edu.fiu.adwise.fingerprint_localization.database.LocalizationLUT;
import edu.fiu.adwise.fingerprint_localization.structs.LocalizationResult;
import edu.fiu.adwise.fingerprint_localization.structs.SendLocalizationData;
import edu.fiu.adwise.homomorphic_encryption.socialistmillionaire.alice;

/**
 * Implements plaintext distance computation for Wi-Fi fingerprint localization.
 * <p>
 * This class provides methods for calculating distances between scanned and database RSS values
 * without homomorphic encryption. It supports both Miss Constant Algorithm (MCA) and Dynamic Matching Algorithm (DMA)
 * approaches, and computes location coordinates using centroid finding.
 * </p>
 *
 * <ul>
 *   <li>Uses standard arithmetic for privacy-unaware localization.</li>
 *   <li>Handles both constant and dynamic AP matching strategies.</li>
 *   <li>Supports centroid calculation for estimated location.</li>
 * </ul>
 *
 * @author Andrew Quijano
 * @since 2017-07-06
 */
public class DistancePlain extends Distance {
	/**
	 * Constructs a plaintext distance computation instance from localization input data.
	 *
	 * @param in input data containing scan APs, RSS values, and map information
	 * @throws ClassNotFoundException if database class is not found
	 * @throws SQLException if database access error occurs
	 */
	public DistancePlain(SendLocalizationData in) throws ClassNotFoundException, SQLException {
		scanAPs = in.APs;
		scanRSS = in.RSS;
		if(lookup_table_column == null) {
			lookup_table_column = LocalizationLUT.getColumnMAC(in.map);
		}
		// Read from Database
		LocalizationLUT.getPlainLookup(this.RSS_ij, this.coordinates, in.map);
	}

	/**
	 * Computes minimum distance between scan and database entries.
	 * If REU2017 mode is enabled, sorts results and updates estimated location.
	 *
	 * @param Niu instance of Alice (unused in plaintext mode)
	 * @param isREU2017 flag indicating REU2017 mode
	 * @return list of localization results
	 * @throws ClassNotFoundException if database class is not found
	 * @throws IOException if I/O error occurs
	 */
	public List<LocalizationResult> MinimumDistance(alice Niu, boolean isREU2017)
			throws ClassNotFoundException, IOException {
		resultList = this.MissConstantAlgorithm();
		if(isREU2017) {
			Collections.sort(resultList);
			this.location = resultList.get(0).coordinates;
		}
		return resultList;
	}

	/**
	 * Computes distances using the Miss Constant Algorithm (MCA).
	 * Substitutes missing RSS values with a constant and calculates distances.
	 *
	 * @return list of localization results with computed distances
	 * @throws ClassNotFoundException if database class is not found
	 * @throws IOException if I/O error occurs
	 */
	public List<LocalizationResult> MissConstantAlgorithm()
			throws ClassNotFoundException, IOException {
		long distance;
		for (int i = 0; i < RSS_ij.size(); i++) {
			distance = 0;
			for (int j = 0; j < lookup_table_column.length; j++) {
				if(scanAPs[j].equals(lookup_table_column[j])) {
					distance += (RSS_ij.get(i)[j] - scanRSS[j]) * (RSS_ij.get(i)[j] - scanRSS[j]);	
				} else {
					distance += (long) (v_c - scanRSS[j]) * (v_c - scanRSS[j]);
				}
			}
			resultList.add(new LocalizationResult(coordinates.get(i)[0], coordinates.get(i)[1], distance, null));
		}
		this.Phase3(null);
		return resultList;
	}

	/**
	 * Computes distances using the Dynamic Matching Algorithm (DMA).
	 * Only matches APs present in the scan and calculates distances.
	 *
	 * @return list of localization results with computed distances
	 * @throws ClassNotFoundException if database class is not found
	 * @throws IOException if I/O error occurs
	 */
	public List<LocalizationResult> DynamicMatchingAlgorithm()
			throws ClassNotFoundException, IOException {
		long distance = 0;
		long matches;
		for (int i = 0; i < RSS_ij.size();i++) {
			matches = 0;
			for (int j = 0; j < lookup_table_column.length; j++) {
				if (scanAPs[i].equals(lookup_table_column[i])) {
					distance = (RSS_ij.get(i)[j] - scanRSS[j]) * (RSS_ij.get(i)[j] - scanRSS[j]);
					++matches;
				}
			}
			resultList.add(new LocalizationResult(coordinates.get(i)[0], coordinates.get(i)[1], distance, matches));
		}
		// Phase 3 will take care of sorting!
		this.Phase3(null);
		return resultList;
	}

	/**
	 * Performs centroid calculation for estimated location using the k-nearest results.
	 *
	 * @param Niu instance of Alice (unused in plaintext mode)
	 * @return null (location is updated in the class fields)
	 */
	protected BigInteger[] Phase3(alice Niu) {
		long distanceSUM = 0;
		double [] w = new double[k];
		
		// Step 2: Sort them...Merge Sort should be the best...
    	Collections.sort(resultList);
        
    	// Finish the rest of Phase 3
		for (int i = 0; i < k;i++) {
			distanceSUM += resultList.get(i).getPlainDistance();
		}
		    
        double x = 0, y = 0;
        // Find the value of all w_i
        for (int i = 0 ; i < k; i++) {
            w[i] = (1.0 - ((double) resultList.get(i).getPlainDistance()/distanceSUM))/(k - 1);
			x += w[i] * resultList.get(i).coordinates[0];
            y += w[i] * resultList.get(i).coordinates[1];
        }
        this.location[0] = x;
        this.location[1] = y;
		return null;
	}
}
