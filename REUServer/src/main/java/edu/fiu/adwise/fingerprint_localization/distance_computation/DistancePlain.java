package edu.fiu.adwise.fingerprint_localization.distance_computation;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;

import edu.fiu.adwise.fingerprint_localization.database.LocalizationLUT;
import edu.fiu.adwise.fingerprint_localization.database.MultiphoneLocalization;
import edu.fiu.adwise.fingerprint_localization.server;
import edu.fiu.adwise.fingerprint_localization.structs.LocalizationResult;
import edu.fiu.adwise.fingerprint_localization.structs.SendLocalizationData;
import edu.fiu.adwise.homomorphic_encryption.socialistmillionaire.alice;

public class DistancePlain extends Distance {
	private final boolean isREU2017;
	public DistancePlain(SendLocalizationData in) throws ClassNotFoundException, SQLException {
		scanAPs = in.APs;
		scanRSS = in.RSS;
		isREU2017 = in.isREU2017;
		
		if(column == null) {
			column = LocalizationLUT.getColumnMAC(in.map);
		}
		// Read from Database
		if(server.multi_phone) {
			MultiphoneLocalization.getPlainLookup(this.RSS_ij, this.coordinates, in.phone_data, in.map);
		} else {
			LocalizationLUT.getPlainLookup(this.RSS_ij, this.coordinates, in.map);
		}
	}

	public ArrayList<LocalizationResult> MinimumDistance(alice Niu)
			throws ClassNotFoundException, IOException {
		resultList = this.MissConstantAlgorithm();
		if(isREU2017) {
			Collections.sort(resultList);
			this.location = resultList.get(0).coordinates;
		}
		return resultList;
	}

	public ArrayList<LocalizationResult> MissConstantAlgorithm()
			throws ClassNotFoundException, IOException {
		long distance;
		for (int i = 0; i < RSS_ij.size(); i++) {
			distance = 0;
			for (int j = 0; j < VECTOR_SIZE; j++) {
				if(scanAPs[j].equals(column[j])) {
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

	public ArrayList<LocalizationResult> DynamicMatchingAlgorithm()
			throws ClassNotFoundException, IOException {
		long distance = 0;
		long matches;
		for (int i = 0; i < RSS_ij.size();i++) {
			matches = 0;
			for (int j = 0; j < VECTOR_SIZE;j++) {
				if (scanAPs[i].equals(column[i])) {
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
