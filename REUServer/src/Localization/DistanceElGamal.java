package Localization;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import Localization.structs.LocalizationResult;
import Localization.structs.SendLocalizationData;
import security.elgamal.ElGamalCipher;
import security.elgamal.ElGamalPublicKey;
import security.elgamal.ElGamal_Ciphertext;
import security.socialistmillionaire.alice;

public class DistanceElGamal extends Distance
{
	private List<ElGamal_Ciphertext> S2;
	private	ElGamal_Ciphertext S3;
	private List<ElGamal_Ciphertext> S3_comp;
	
	private ElGamalPublicKey pk = null;
	private boolean isREU2017;	
	
	// Final answers
	protected ElGamal_Ciphertext [] e_xy = new ElGamal_Ciphertext [2];
	private List<ElGamal_Ciphertext> e_distances = new ArrayList<ElGamal_Ciphertext>();
	
	public DistanceElGamal(SendLocalizationData in)
			throws ClassNotFoundException, SQLException
	{
		pk = in.e_pk;
		S2 = in.e_S2;
		S3 = in.e_S3;
		S3_comp = in.e_S3_comp;
		
		isREU2017 = in.isREU2017;
		scanAPs = in.APs;
		if(column == null)
		{
			column = LocalizationLUT.getColumnMAC(in.map);
		}
		
		// Read from Database, get S_1 and S_2 Parts!
		if(server.multi_phone)
		{
			MultiphoneLocalization.getPlainLookup(this.RSS_ij, this.coordinates, in.phone_data, in.map);
		}
		else
		{
			LocalizationLUT.getPlainLookup(this.RSS_ij, this.coordinates, in.map);
		}
		//MINIMUM_AP_MATCH = (int) (VECTOR_SIZE * FSF);
		// THIS NUMBER SHOULD ALWAYS BE >= 1 FOR THE FOLLOWING REASONS
		// 1- Inform User if they are not in floor map
		// 2- MCA/DMA will break because division by 0 becomes possible!
		MINIMUM_AP_MATCH = 1;
	}

	protected ArrayList<LocalizationResult> MinimumDistance(alice Niu)
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{
		this.MissConstantAlgorithm();
		// REU 2015, let the phone do the work!
		if(!isREU2017)
		{
			return resultList;
		}
		
		// 1- Encrypt and Store coordinates
		for(int i = 0; i < resultList.size(); i++)
		{
			resultList.get(i).add_secret_coordinates(pk);
		}
		// 2- Shuffle Result List
		Collections.shuffle(resultList);
		
		// 3- Get Min and return ([[x]], [[y]])
		ElGamal_Ciphertext min = Niu.getKMin_ElGamal(e_distances, 1).get(0);
		for(LocalizationResult l: resultList)
		{
			if(l.e_d.equals(min))
			{
				this.e_xy[0] = l.e_xy[0];
				this.e_xy[1] = l.e_xy[1];
				break;
			}
		}
		System.out.println("Size: " + resultList.size());
		return resultList;
	}

	protected ArrayList<LocalizationResult> MissConstantAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{
		long count = 0;
		long S1_temp = 0;
		ElGamal_Ciphertext d = null;
		ElGamal_Ciphertext S1_Row = null;
		ElGamal_Ciphertext S2_Row = null;
		
		for (int i = 0; i < RSS_ij.size();i++)
		{	
			// Step 1, Compute FSF
			count = 0;
			for (int j = 0; j < VECTOR_SIZE; j++)
			{
				if(scanAPs[j].equals(column[j]))
				{
					++count;
				}
			}
			
			// Step 2, if FSF is NOT satisfied, skip this step!
			if(count < MINIMUM_AP_MATCH)
			{
				continue;
			}
			
			// Repeat MCA/DMA as shown in the paper to compute distance
			S2_Row = ElGamalCipher.encrypt(pk, 0);
			
			for (int j = 0; j < VECTOR_SIZE;j++)
			{
				if(scanAPs[j].equals(column[j]))
				{
					S1_temp += RSS_ij.get(i)[j] * RSS_ij.get(i)[j];
					S2_Row = ElGamalCipher.add(S2_Row, ElGamalCipher.multiply(S2.get(j), RSS_ij.get(i)[j], pk), pk);
				}
				else
				{
					S1_temp += Distance.v_c * Distance.v_c;
					S2_Row = ElGamalCipher.add(S2_Row, ElGamalCipher.multiply(S2.get(j), v_c, pk), pk);
				}
			}
			S1_Row = ElGamalCipher.encrypt(pk, S1_temp);
			d = ElGamalCipher.add(S1_Row, S3, pk);
			d = ElGamalCipher.add(d, S2_Row, pk);
			e_distances.add(d);
			resultList.add(new LocalizationResult(coordinates.get(i)[0], coordinates.get(i)[1], d, null));
		}
		return resultList;
	}

	protected ArrayList<LocalizationResult> DynamicMatchingAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{
		long count = 0;
		long S1_temp = 0;
		ElGamal_Ciphertext d = null;
		ElGamal_Ciphertext S1_Row = null;
		ElGamal_Ciphertext S2_Row = null;
		ElGamal_Ciphertext S3_Row = null;
		
		for (int i = 0; i < RSS_ij.size();i++)
		{	
			// Step 1, Compute FSF
			count = 0;
			for (int j = 0; j < VECTOR_SIZE; j++)
			{
				if(scanAPs[j].equals(column[j]))
				{
					++count;
				}
			}
			
			// Step 2, if FSF is NOT satisfied, skip this step!
			if(count < MINIMUM_AP_MATCH)
			{
				continue;
			}
			
			// Repeat MCA/DMA as shown in the paper to compute distance
			S2_Row = ElGamalCipher.encrypt(pk, 0);
			S3_Row = ElGamalCipher.encrypt(pk, 0);
			
			for (int j = 0; j < VECTOR_SIZE;j++)
			{
				if(scanAPs[j].equals(column[j]))
				{
					S1_temp += RSS_ij.get(i)[j] * RSS_ij.get(i)[j];
					S2_Row = ElGamalCipher.add(S2_Row, ElGamalCipher.multiply(S2.get(j), RSS_ij.get(i)[j], pk), pk);
					S3_Row = ElGamalCipher.add(S3_Row, S3_comp.get(j), pk);
				}
			}
			S1_Row = ElGamalCipher.encrypt(pk, S1_temp);
			d = ElGamalCipher.add(S1_Row, S3_Row, pk);
			d = ElGamalCipher.add(d, S2_Row, pk);
			e_distances.add(d);
			resultList.add(new LocalizationResult(coordinates.get(i)[0], coordinates.get(i)[1], d, count));
		}
		return resultList;
	}
	
	// Return Encrypted Coordinates in ElGamal
	protected BigInteger[] Phase3(alice Niu)
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{	
		// It is DMA, you need to divide distances by matches!!
		// Problem is that Alice needs to read the number of matches to do this?
		// I mean I guess to make this painless I can just pass result list and iterate from there?
		/*
		for(int i = 0; i < resultList.size();i++)
		{
			resultList.get(i).e_d = Niu.division(resultList.get(i).e_d, resultList.get(i).matches);
		}
		*/
		
		// Get the K-minimum distances!
		int index = -1;
		List<ElGamal_Ciphertext> k_min = Niu.getKMin_ElGamal(e_distances, Distance.k);
		Object x;
		BigInteger d = null;
		ElGamal_Ciphertext divisor = ElGamalCipher.sum(k_min, pk, Distance.k);
		List<ElGamal_Ciphertext> weights = new ArrayList<ElGamal_Ciphertext>();
		
		Niu.writeObject(divisor);
		
		// Get the plain text value
		x = Niu.readObject();
		if(x instanceof BigInteger)
		{
			d = (BigInteger) x;
		}
		else
		{
			throw new IllegalArgumentException("DID NOT GET d decrypted!");
		}
		
		// Now I get the k distances and divide by divisor
		/*
		 * Original:
		 * (1 - d_i/sum(d_i))/(k - 1)
		 * 
		 * 100 * (1 - d_i/sum(d_i))/(k - 1)
		 * 100 - 100d_i/sum(d_i)/(k - 1)
		 */
		for (int i = 0; i < Distance.k; i++)
		{
			weights.set(i, ElGamalCipher.multiply(k_min.get(i), Distance.FACTOR, pk));
			weights.set(i, Niu.division(weights.get(i), d.longValue() * (k - 1)));
			weights.set(i, ElGamalCipher.subtract(ElGamalCipher.encrypt(pk, FACTOR/(k - 1)), weights.get(i), pk));
		}
		
		e_xy[0] = ElGamalCipher.encrypt(pk, 0);
		e_xy[1] = ElGamalCipher.encrypt(pk, 0);
		
		// Now I multiply it with all scalars. (x, y)
		for (int i = 0; i < Distance.k; i++)
		{
			index = elgamal_distance_index(k_min.get(i));
			e_xy[0] = ElGamalCipher.add(e_xy[0], ElGamalCipher.multiply(weights.get(i), resultList.get(index).getX().longValue(), pk), pk);
			e_xy[1] = ElGamalCipher.add(e_xy[1], ElGamalCipher.multiply(weights.get(i), resultList.get(index).getY().longValue(), pk), pk);
		}
		return encryptedLocation;
	}

	// DMA NORMALIZATION
	protected void DMA_Normalization(alice Niu) 
			throws IOException, ClassNotFoundException, IllegalArgumentException
	{
		Niu.writeObject(resultList);
		for (int i = 0; i < resultList.size(); i++)
		{
			ElGamal_Ciphertext d = Niu.division(resultList.get(i).e_d, resultList.get(i).matches);
			resultList.get(i).setElGamalEncryptedDistance(d);
		}
	}
}
