package edu.fiu.adwise.fingerprint_localization.distance_computation;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;

import edu.fiu.adwise.fingerprint_localization.database.LocalizationLUT;
import edu.fiu.adwise.fingerprint_localization.structs.LocalizationResult;
import edu.fiu.adwise.fingerprint_localization.structs.SendLocalizationData;
import edu.fiu.adwise.homomorphic_encryption.misc.HomomorphicException;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierCipher;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPublicKey;
import edu.fiu.adwise.homomorphic_encryption.socialistmillionaire.alice;


/**
 * Implements distance computation for Wi-Fi fingerprint localization using Paillier homomorphic encryption.
 * <p>
 * This class provides methods for calculating encrypted distances between scanned and database RSS values,
 * supporting both Miss Constant Algorithm (MCA) and Dynamic Matching Algorithm (DMA) approaches.
 * It also computes encrypted location coordinates using secure centroid finding with the Socialist Millionaire protocol.
 * </p>
 *
 * <ul>
 *   <li>Uses Paillier encryption for privacy-preserving localization.</li>
 *   <li>Handles both constant and dynamic AP matching strategies.</li>
 *   <li>Supports secure computation phases for encrypted location estimation.</li>
 * </ul>
 *
 * @author Andrew Quijano
 * @since 2017-07-06
 */
public class DistancePaillier extends Distance {
	/** Paillier-encrypted S2 vector for distance computation. */
	private final BigInteger [] S2;
	/** Paillier-encrypted S3 scalar for distance computation. */
	private final BigInteger S3;
	/** Paillier-encrypted S3 components for DMA. */
	private final BigInteger [] S3_comp;
	/** Paillier public key for encryption operations. */
	private final PaillierPublicKey pk;

	/**
	 * Constructs a Paillier distance computation instance from localization input data.
	 *
	 * @param in input data containing scan APs, Paillier keys, and precomputed values
	 * @throws ClassNotFoundException if database class is not found
	 * @throws SQLException if database access error occurs
	 */
	public DistancePaillier(SendLocalizationData in)
			throws ClassNotFoundException, SQLException {
		pk = in.pk;
		S2 = in.S2;
		S3 = in.S3;
		S3_comp = in.S3_comp;
		scanAPs = in.APs;
		if(lookup_table_column == null) {
			lookup_table_column = LocalizationLUT.getColumnMAC(in.map);
		}
		
		// Read from Database, get S_1 and S_2 Parts!
		LocalizationLUT.getPlainLookup(this.RSS_ij, this.coordinates, in.map);

		//MINIMUM_AP_MATCH = (int) (VECTOR_SIZE * FSF);
		// THIS NUMBER SHOULD ALWAYS BE >= 1 FOR THE FOLLOWING REASONS
		// 1- Inform User if they are not in floor map
		// 2- MCA/DMA will break because division by 0 becomes possible!	
		MINIMUM_AP_MATCH = 1;
	}

	/**
	 * Computes encrypted distances using the Miss Constant Algorithm (MCA).
	 * Substitutes missing RSS values with a constant and calculates Paillier-encrypted distances.
	 *
	 * @return list of localization results with encrypted distances
	 * @throws ClassNotFoundException if database class is not found
	 * @throws IOException if I/O error occurs
	 * @throws IllegalArgumentException if arguments are invalid
	 * @throws HomomorphicException if Paillier encryption fails
	 */
	public List<LocalizationResult> MissConstantAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException {
		BigInteger d;
		BigInteger S1_Row;
		BigInteger S2_Row;
		
		for (int i = 0; i < RSS_ij.size();i++) {
			// Step 1, Compute FSF
			if (!has_sufficient_fsf()) {
				continue;
			}

			// Repeat MCA/DMA as shown in the paper to compute distance
			S1_Row = PaillierCipher.encrypt(0, pk);
			S2_Row = PaillierCipher.encrypt(0, pk);
			
			for (int j = 0; j < lookup_table_column.length; j++) {
				if(scanAPs[j].equals(lookup_table_column[j])) {
					S1_Row = PaillierCipher.add_plaintext(S1_Row, BigInteger.valueOf(RSS_ij.get(i)[j] * RSS_ij.get(i)[j]), pk);
					S2_Row = PaillierCipher.add(S2_Row, PaillierCipher.multiply(S2[j], RSS_ij.get(i)[j], pk), pk);
				} else {
					S1_Row = PaillierCipher.add_plaintext(S1_Row, BigInteger.valueOf((long) v_c * v_c), pk);
					S2_Row = PaillierCipher.add(S2_Row, PaillierCipher.multiply(S2[j], v_c, pk), pk);
				}
			}
			d = PaillierCipher.add(S1_Row, S3, pk);
			d = PaillierCipher.add(d, S2_Row, pk);
			encryptedDistance.add(d);
			resultList.add(new LocalizationResult(coordinates.get(i)[0], coordinates.get(i)[1], d, null));
		}
		return resultList;
	}

	/**
	 * Computes encrypted distances using the Dynamic Matching Algorithm (DMA).
	 * Only matches APs present in the scan and calculates Paillier-encrypted distances.
	 *
	 * @return list of localization results with encrypted distances
	 * @throws ClassNotFoundException if database class is not found
	 * @throws IOException if I/O error occurs
	 * @throws IllegalArgumentException if arguments are invalid
	 * @throws HomomorphicException if Paillier encryption fails
	 */
	public List<LocalizationResult> DynamicMatchingAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException {
		long count;
		BigInteger d;
		BigInteger S1_Row;
		BigInteger S2_Row;
		BigInteger S3_Row;
		
		for (int i = 0; i < RSS_ij.size();i++) {
			// Step 1, Compute FSF
			count = 0;
			for (int j = 0; j < lookup_table_column.length; j++) {
				if(scanAPs[j].equals(lookup_table_column[j])) {
					++count;
				}
			}
			
			// Step 2, if FSF is NOT satisfied, skip this step!
			if(count < MINIMUM_AP_MATCH) {
				continue;
			}
			
			// Repeat MCA/DMA as shown in the paper to compute distance
			S1_Row = PaillierCipher.encrypt(0, pk);
			S2_Row = PaillierCipher.encrypt(0, pk);
			S3_Row = PaillierCipher.encrypt(0, pk);
			
			for (int j = 0; j < lookup_table_column.length; j++) {
				if(scanAPs[j].equals(lookup_table_column[j])) {
					S1_Row = PaillierCipher.add_plaintext(S1_Row, BigInteger.valueOf(RSS_ij.get(i)[j] * RSS_ij.get(i)[j]), pk);
					S2_Row = PaillierCipher.add(S2_Row, PaillierCipher.multiply(S2[j], RSS_ij.get(i)[j], pk), pk);
					S3_Row = PaillierCipher.add(S3_Row, S3_comp[j], pk);
				}
			}
			d = PaillierCipher.add(S1_Row, S3_Row, pk);
			d = PaillierCipher.add(d, S2_Row, pk);
			encryptedDistance.add(d);
			resultList.add(new LocalizationResult(coordinates.get(i)[0], coordinates.get(i)[1], d, count));
		}
		return resultList;
	}

	/**
	 * Performs phase 3 of secure centroid finding using Paillier encryption.
	 * Computes encrypted location coordinates based on the k-minimum distances and their weights.
	 *
	 * @param Niu instance of Alice for secure computation
	 * @return array of encrypted location coordinates [x, y]
	 * @throws ClassNotFoundException if a database class is not found
	 * @throws IOException if I/O error occurs
	 * @throws IllegalArgumentException if arguments are invalid or protocol fails
	 * @throws HomomorphicException if Paillier encryption fails
	 */
	public BigInteger[] Phase3(alice Niu)
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException {
		// Get the K-minimum distances!
		BigInteger [] k_min = Niu.getKValues(encryptedDistance, k, true);
		// TODO: If it is DMA, you need to divide distances first right??
		int index;
		Object x;
		BigInteger divisor;
		BigInteger [] weights = new BigInteger[Distance.k];
		
		divisor = PaillierCipher.sum(k_min, pk, Distance.k);
		Niu.writeObject(divisor);
		
		// Get the plain text value
		x = Niu.readObject();
		if(x instanceof BigInteger) {
			divisor = (BigInteger) x;
		} else {
			throw new IllegalArgumentException("DID NOT GET d decrypted!");
		}
		
		// Now I get the k distances and divide by divisor
		/*
		 * Original:
		 * (1 - d_i/sum(d_i))/(k - 1)
		 * 
		 * = 1/k-1 - d_i/sum_(d_i)(k - 1)
		 * MULTIPLY BY 100 THE WHOLE THING
		 * 100/(k - 1) - 100d_i/sum(d_i)/(k - 1)
		 */
		for (int i = 0; i < Distance.k; i++) {
			weights[i] = PaillierCipher.multiply(k_min[i], Distance.FACTOR, pk);
			weights[i] = Niu.division(weights[i], divisor.longValue() * (k - 1));
			weights[i] = PaillierCipher.subtract(PaillierCipher.encrypt(FACTOR/(k - 1), pk), weights[i], pk);
		}
		
		encryptedLocation[0] = PaillierCipher.encrypt(0, pk);
		encryptedLocation[1] = PaillierCipher.encrypt(0, pk);
		
		// Now I multiply it with all scalars. (x, y)
		for (int i = 0; i < Distance.k; i++) {
			// NOTE; IT WILL NOT GIVE ME CORRECT X_I, Y_I since it is NOT sorted.
			// So I need to get the correct index!
			index = distance_index(k_min[i]);
			encryptedLocation[0] = PaillierCipher.add(encryptedLocation[0], PaillierCipher.multiply(weights[i] , resultList.get(index).getX().longValue(), pk), pk);
			encryptedLocation[1] = PaillierCipher.add(encryptedLocation[1], PaillierCipher.multiply(weights[i] , resultList.get(index).getY().longValue(), pk), pk);
		}
		return encryptedLocation;
	}	
}
