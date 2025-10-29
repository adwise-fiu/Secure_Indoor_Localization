package edu.fiu.adwise.fingerprint_localization.distance_computation;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.fiu.adwise.fingerprint_localization.structs.LocalizationResult;
import edu.fiu.adwise.ciphercraft.misc.HomomorphicException;
import edu.fiu.adwise.ciphercraft.socialistmillionaire.alice;

/**
 * Abstract base class for distance computation in Wi-Fi fingerprint localization.
 * <p>
 * Provides common fields and methods for handling access point scans, RSS values,
 * encrypted distances, and localization results. Subclasses must implement
 * specific algorithms for minimum distance, MCA, DMA, and secure computation phases.
 * </p>
 *
 * <ul>
 *   <li>Supports both plaintext and homomorphic encryption-based localization.</li>
 *   <li>Handles filtering of access points based on Feature Selection Factor (FSF).</li>
 *   <li>Stores scan data, result lists, and coordinates for localization.</li>
 * </ul>
 *
 * @author Andrew Quijano
 * @since 2017-07-06
 */
public abstract class Distance {
	/** Default RSS value for missing access points. */
	public static final int v_c = -120;

	/** Minimum number of AP matches required for computation. */
	protected long MINIMUM_AP_MATCH;

	/** Scaling factor for distance calculations. */
	protected final static long FACTOR = 10;

	/** Array of scanned access point MAC addresses. */
	protected String[] scanAPs;

	/** Array of scanned RSS values. */
	protected Integer[] scanRSS;

	/** List of encrypted distance values for each candidate location. */
	protected List<BigInteger> encryptedDistance = new ArrayList<>();

	/** List of localization results for each candidate location. */
	protected List<LocalizationResult> resultList = new ArrayList<>();

	/** Array of column names (APs) from the database. */
	protected static String[] lookup_table_column = null;

	/** Estimated location coordinates [x, y] (plaintext). */
	public Double[] location = new Double[2];

	/** Estimated location coordinates [x, y] (encrypted). */
	public BigInteger[] encryptedLocation = new BigInteger[2];

	/** Number of nearest neighbors (K) for KNN-based algorithms. */
	public static int k = 2;

	/** RSS values from a database for each candidate location. */
	protected List<Long[]> RSS_ij = new ArrayList<>();

	/** Coordinates from a database for each candidate location. */
	protected List<Double[]> coordinates = new ArrayList<>();

	/**
	 * Computes distances using MCA, substituting missing RSS values with v_c.
	 * Must be implemented by subclasses.
	 *
	 * @return list of localization results
	 * @throws ClassNotFoundException if database class is not found
	 * @throws IOException if I/O error occurs
	 * @throws IllegalArgumentException if arguments are invalid
	 * @throws HomomorphicException if homomorphic encryption fails
	 */
	protected abstract List<LocalizationResult> MissConstantAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException;

	/**
	 * Computes distances using DMA, skipping Access Points if not present in the scan.
	 * Must be implemented by subclasses.
	 *
	 * @return list of localization results
	 * @throws ClassNotFoundException if database class is not found
	 * @throws IOException if I/O error occurs
	 * @throws IllegalArgumentException if arguments are invalid
	 * @throws HomomorphicException if homomorphic encryption fails
	 */
	protected abstract List<LocalizationResult> DynamicMatchingAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException;

	/**
	 * Performs phase 3 of secure computation (DGK/Paillier).
	 * Must be implemented by subclasses.
	 *
	 * @param Niu instance of Alice for secure computation
	 * @return array of encrypted location coordinates
	 * @throws ClassNotFoundException if database class is not found
	 * @throws IOException if I/O error occurs
	 * @throws IllegalArgumentException if arguments are invalid
	 * @throws HomomorphicException if homomorphic encryption fails
	 */
	protected abstract BigInteger[] Phase3(alice Niu)
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException;

	/**
	 * Finds the index of the result with the specified encrypted distance.
	 *
	 * @param min encrypted distance value to search for
	 * @return index of the matching result, or -1 if not found
	 */
	protected int distance_index(BigInteger min) {
		for(int i = 0; i < resultList.size(); i++) {
			if(resultList.get(i).encryptedDistance.equals(min)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Computes the minimum distance and updates encrypted location coordinates.
	 * Encrypts coordinates if REU2017 mode is enabled.
	 *
	 * @param Niu instance of Alice for secure computation
	 * @param isREU2017 flag indicating REU2017 mode
	 * @return list of localization results
	 * @throws ClassNotFoundException if database class is not found
	 * @throws IOException if I/O error occurs
	 * @throws IllegalArgumentException if arguments are invalid
	 * @throws HomomorphicException if homomorphic encryption fails
	 */
	public List<LocalizationResult> MinimumDistance(alice Niu, boolean isREU2017)
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException {
		resultList = this.MissConstantAlgorithm();
		// REU 2015, let the phone do the work!
		if(!isREU2017) {
			return resultList;
		}

		// 1- Encrypt and Store coordinates
		for (LocalizationResult localizationResult : resultList) {
			if (Niu.isDGK()) {
				localizationResult.add_secret_coordinates(Niu.getDGKPublicKey());
			} else {
				localizationResult.add_secret_coordinates(Niu.getPaillierPublicKey());
			}
		}

		// 2- Shuffle Result List
		Collections.shuffle(resultList);

		// 3- Get Min and return ([[x]], [[y]])
		BigInteger min = Niu.getKValues(encryptedDistance, 1, true)[0];
		for(LocalizationResult l: resultList) {
			if(l.encryptedDistance.equals(min)) {
				this.encryptedLocation[0] = l.encryptedCoordinates[0];
				this.encryptedLocation[1] = l.encryptedCoordinates[1];
				break;
			}
		}
		return resultList;
	}

	/**
	 * Checks if the scan has enough AP matches to satisfy the FSF threshold.
	 *
	 * @return true if the number of matches is lower than the minimum required, false otherwise
	 */
	protected boolean has_sufficient_fsf() {
		// Step 1, Compute FSF
		int count = 0;
		for (int j = 0; j < lookup_table_column.length; j++) {
			if(scanAPs[j].equals(lookup_table_column[j])) {
				++count;
			}
		}

		// Step 2, if FSF is NOT satisfied, skip this step!
		return count < MINIMUM_AP_MATCH;
	}
}