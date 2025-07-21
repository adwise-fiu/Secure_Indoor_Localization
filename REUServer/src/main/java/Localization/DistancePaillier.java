package Localization;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;

import Localization.structs.LocalizationResult;
import Localization.structs.SendLocalizationData;
import edu.fiu.adwise.homomorphic_encryption.misc.HomomorphicException;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierCipher;
//import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPublicKey;
import edu.fiu.adwise.homomorphic_encryption.socialistmillionaire.alice;


public class DistancePaillier extends Distance
{
	private BigInteger [] S2;
	private	BigInteger S3;
	private BigInteger [] S3_comp;
	
	private boolean isREU2017;
	private PaillierPublicKey pk = null;

	public DistancePaillier(SendLocalizationData in)
			throws ClassNotFoundException, SQLException {
		pk = in.pk;
		S2 = in.S2;
		S3 = in.S3;
		S3_comp = in.S3_comp;
		isREU2017 = in.isREU2017;
		scanAPs = in.APs;
		if(column == null) {
			column = LocalizationLUT.getColumnMAC(in.map);
		}
		
		// Read from Database, get S_1 and S_2 Parts!
		if(server.multi_phone) {
			MultiphoneLocalization.getPlainLookup(this.RSS_ij, this.coordinates, in.phone_data, in.map);	
		}
		else {
			LocalizationLUT.getPlainLookup(this.RSS_ij, this.coordinates, in.map);
		}
		//MINIMUM_AP_MATCH = (int) (VECTOR_SIZE * FSF);
		// THIS NUMBER SHOULD ALWAYS BE >= 1 FOR THE FOLLOWING REASONS
		// 1- Inform User if they are not in floor map
		// 2- MCA/DMA will break because division by 0 becomes possible!	
		MINIMUM_AP_MATCH = 1;
	}

	protected ArrayList<LocalizationResult> MinimumDistance(alice Niu)
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException {
		resultList = this.MissConstantAlgorithm();
		// REU 2015, let the phone do the work!
		if(!isREU2017) {
			return resultList;
		}
		
		// 1- Encrypt and Store coordinates
		for(int i = 0; i < resultList.size(); i++) {
			resultList.get(i).add_secret_coordinates(pk);
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

	protected ArrayList<LocalizationResult> MissConstantAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException {
		long count = 0;
		BigInteger d = null;
		BigInteger S1_Row = null;
		BigInteger S2_Row = null;
		
		for (int i = 0; i < RSS_ij.size();i++) {
			// Step 1, Compute FSF
			count = 0;
			for (int j = 0; j < VECTOR_SIZE; j++) {
				if(scanAPs[j].equals(column[j])) {
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
			
			for (int j = 0; j < VECTOR_SIZE;j++) {
				if(scanAPs[j].equals(column[j])) {
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

	protected ArrayList<LocalizationResult> DynamicMatchingAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException {
		long count = 0;
		BigInteger d = null;
		BigInteger S1_Row = null;
		BigInteger S2_Row = null;
		BigInteger S3_Row = null;
		
		for (int i = 0; i < RSS_ij.size();i++) {
			// Step 1, Compute FSF
			count = 0;
			for (int j = 0; j < VECTOR_SIZE; j++) {
				if(scanAPs[j].equals(column[j])) {
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
			
			for (int j = 0; j < VECTOR_SIZE;j++) {
				if(scanAPs[j].equals(column[j])) {
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
	
	protected BigInteger[] Phase3(alice Niu)
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException {
		// Get the K-minimum distances!
		BigInteger [] k_min = Niu.getKValues(encryptedDistance, k, true);
		// TODO: If it is DMA, you need to divide distances first right??
		int index = -1;
		Object x = null;
		BigInteger divisor = null;
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
			// NOTE, IT WILL NOT GIVE ME CORRECT X_I, Y_I since it is NOT sorted.
			// So I need to get the correct index!
			index = distance_index(k_min[i]);
			encryptedLocation[0] = PaillierCipher.add(encryptedLocation[0], PaillierCipher.multiply(weights[i] , resultList.get(index).getX().longValue(), pk), pk);
			encryptedLocation[1] = PaillierCipher.add(encryptedLocation[1], PaillierCipher.multiply(weights[i] , resultList.get(index).getY().longValue(), pk), pk);
		}
		return encryptedLocation;
	}	
}
