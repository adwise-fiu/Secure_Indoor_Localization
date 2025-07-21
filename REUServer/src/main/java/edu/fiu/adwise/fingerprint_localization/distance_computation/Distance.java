package edu.fiu.adwise.fingerprint_localization.distance_computation;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;

import edu.fiu.adwise.fingerprint_localization.structs.LocalizationResult;
import edu.fiu.adwise.homomorphic_encryption.elgamal.ElGamal_Ciphertext;
import edu.fiu.adwise.homomorphic_encryption.misc.HomomorphicException;
import edu.fiu.adwise.homomorphic_encryption.socialistmillionaire.alice;

public abstract class Distance {
	public static int v_c = -120;
	public static int VECTOR_SIZE = -1;
	// Filter out ONLY 90% of APs
	public static double FSF = 0.9;
	protected long MINIMUM_AP_MATCH;
	protected final static long FACTOR = 10; 

	protected String [] scanAPs;
	protected Integer [] scanRSS;
	protected ArrayList<BigInteger> encryptedDistance = new ArrayList<BigInteger>();	
	protected ArrayList<LocalizationResult> resultList = new ArrayList<LocalizationResult>();
	
	// Other variables variable
	protected static String [] column = null;

	public Double [] location = new Double [2];
	public BigInteger [] encryptedLocation = new BigInteger[2];
	
	public static int k = 2;

	// Three Distance Methods..
	
	// Obtain data from Database for distance Computation
	protected ArrayList<Long []> RSS_ij = new ArrayList<Long []>();
	protected ArrayList<Double []> coordinates = new ArrayList<Double []>();
	
	// Select smallest distance. This uses MCA!
	protected abstract ArrayList<LocalizationResult> MinimumDistance(alice Niu) 
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException, 
			IllegalArgumentException, HomomorphicException;
	
	// MCA, if miss RSS -120
	protected abstract ArrayList<LocalizationResult> MissConstantAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException;
	
	// DMA, if AP is not on RSS scan, skip it!
	protected abstract ArrayList<LocalizationResult> DynamicMatchingAlgorithm() 
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException;
	
	// Phase 3, for DGK and Paillier
	protected abstract BigInteger[] Phase3(alice Niu)
			throws ClassNotFoundException, IOException, IllegalArgumentException, HomomorphicException;
	
	// DMA NORMALIZATION
	protected void DMA_Normalization(alice Niu) 
			throws IOException, ClassNotFoundException, IllegalArgumentException, HomomorphicException {
		Niu.writeObject(resultList);
        for (LocalizationResult localizationResult : resultList) {
            BigInteger d = Niu.division(localizationResult.encryptedDistance, localizationResult.matches);
            localizationResult.setEncryptedDistance(d);
        }
	}
	
	protected int distance_index(BigInteger min) {
		for(int i = 0; i < resultList.size(); i++) {
			if(resultList.get(i).encryptedDistance.equals(min)) {
				return i;
			}
		}
		return -1;
	}
	
	protected int elgamal_distance_index(ElGamal_Ciphertext min) {
		for(int i = 0; i < resultList.size(); i++) {
			if(resultList.get(i).e_d.equals(min)) {
				return i;
			}
		}
		return -1;
	}
}