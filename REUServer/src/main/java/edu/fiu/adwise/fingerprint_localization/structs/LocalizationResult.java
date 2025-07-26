package edu.fiu.adwise.fingerprint_localization.structs;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;

import edu.fiu.adwise.homomorphic_encryption.dgk.DGKOperations;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPublicKey;
import edu.fiu.adwise.homomorphic_encryption.elgamal.ElGamalCipher;
import edu.fiu.adwise.homomorphic_encryption.elgamal.ElGamalPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.elgamal.ElGamalPublicKey;
import edu.fiu.adwise.homomorphic_encryption.elgamal.ElGamal_Ciphertext;
import edu.fiu.adwise.homomorphic_encryption.misc.HomomorphicException;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierCipher;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPublicKey;

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
public class LocalizationResult implements Serializable, Comparable<LocalizationResult> {
	/** Serialization identifier for compatibility. */
	@Serial
	private static final long serialVersionUID = -1884589588377067950L;

	/** Number of matching access points or features used in localization. */
	public final Long matches;

	/** Plaintext distance value (if available). */
	private Long plainDistance;

	/** Encrypted distance value (Paillier/DGK). */
	public BigInteger encryptedDistance;

	/** Encrypted distance value (ElGamal). */
	public ElGamal_Ciphertext e_d;

	/** Plaintext coordinates [x, y]. */
	public final Double[] coordinates = new Double[2];

	/** Encrypted coordinates [x, y] (Paillier/DGK). */
	public final BigInteger[] encryptedCoordinates = new BigInteger[2];

	/** Encrypted coordinates [x, y] (ElGamal). */
	public final ElGamal_Ciphertext[] e_xy = new ElGamal_Ciphertext[2];

	/**
	 * Constructs a result with plaintext coordinates and distance.
	 *
	 * @param x        X coordinate
	 * @param y        Y coordinate
	 * @param distance Plaintext distance
	 * @param matches  Number of matches
	 */
    public LocalizationResult(Double x, Double  y, Long distance, Long matches) {
    	this.coordinates[0] = x;
        this.coordinates[1] = y;
        this.plainDistance = distance;
        this.matches = matches;
        this.encryptedDistance = null;
        this.e_d = null;
    }

	/**
	 * Constructs a result with encrypted distance (Paillier/DGK).
	 *
	 * @param x        X coordinate
	 * @param y        Y coordinate
	 * @param distance Encrypted distance
	 * @param matches  Number of matches
	 */
    public LocalizationResult(Double x, Double y, BigInteger distance, Long matches) {
    	this.coordinates[0] = x;
        this.coordinates[1] = y;
        this.encryptedDistance = distance;
        this.e_d = null;
        this.matches = matches;
        this.plainDistance = null;
    }

	/**
	 * Constructs a result with encrypted distance (ElGamal).
	 *
	 * @param x        X coordinate
	 * @param y        Y coordinate
	 * @param distance Encrypted distance (ElGamal)
	 * @param matches  Number of matches
	 */
    public LocalizationResult(Double x, Double y, ElGamal_Ciphertext distance, Long matches) {
    	this.coordinates[0] = x;
        this.coordinates[1] = y;
        this.e_d = distance;
        this.encryptedDistance = null;
        this.matches = matches;
        this.plainDistance = null;
    }

	/**
	 * Encrypts coordinates using Paillier and nullifies plaintext values.
	 *
	 * @param pk Paillier public key
	 * @throws HomomorphicException if encryption fails
	 */
    public void add_secret_coordinates(PaillierPublicKey pk) throws HomomorphicException {
    	// Encrypt Coordinates
    	encryptedCoordinates[0] = PaillierCipher.encrypt(coordinates[0].longValue(), pk);
    	encryptedCoordinates[1] = PaillierCipher.encrypt(coordinates[1].longValue(), pk);
    	
    	// The coordinate stored in plain text get nullified
    	coordinates[0] = null;
    	coordinates[1] = null;
    }

	/**
	 * Encrypts coordinates using DGK and nullifies plaintext values.
	 *
	 * @param pk DGK public key
	 * @throws HomomorphicException if encryption fails
	 */
    public void add_secret_coordinates(DGKPublicKey pk) throws HomomorphicException {
    	// Encrypt Coordinates
    	this.encryptedCoordinates[0] = DGKOperations.encrypt(this.coordinates[0].longValue(), pk);
    	this.encryptedCoordinates[1] = DGKOperations.encrypt(this.coordinates[1].longValue(), pk);
    	
    	// The coordinate stored in plain text get nullified
    	this.coordinates[0] = null;
    	this.coordinates[1] = null;
    }

	/**
	 * Encrypts coordinates using ElGamal and nullifies plaintext values.
	 *
	 * @param pk ElGamal public key
	 */
	public void add_secret_coordinates(ElGamalPublicKey pk) {
    	// Encrypt Coordinates
		this.e_xy[0] = ElGamalCipher.encrypt(this.coordinates[0].longValue(), pk);
		this.e_xy[1] = ElGamalCipher.encrypt(this.coordinates[1].longValue(), pk);
    	
    	// The coordinate stored in plain text get nullified
		this.coordinates[0] = null;
		this.coordinates[1] = null;
	}

	/**
	 * Gets the X coordinate (plaintext).
	 *
	 * @return X coordinate
	 */
    public Double getX() {
    	return coordinates[0];
    }

	/**
	 * Gets the Y coordinate (plaintext).
	 *
	 * @return Y coordinate
	 */
    public Double getY() {
    	return coordinates[1];
    }

	/**
	 * Compares this result to another by plaintext distance.
	 * This is particularly useful to sort a list of LocalizationResults by distance.
	 * @param o other result
	 * @return comparison value
	 */
	public int compareTo(LocalizationResult o) {
		return plainDistance.compareTo(o.plainDistance);
	}

	/**
	 * Decrypts the encrypted distance using Paillier and divides by matches if needed.
	 *
	 * @param sk Paillier private key
	 * @throws HomomorphicException if decryption fails
	 */
	public void decrypt_all(PaillierPrivateKey sk) throws HomomorphicException {
		this.plainDistance = PaillierCipher.decrypt(encryptedDistance, sk).longValue();
		if(matches != null) {
			this.plainDistance = plainDistance/matches;
		}
	}

	/**
	 * Decrypts the encrypted distance using DGK and divides by matches if needed.
	 *
	 * @param sk DGK private key
	 * @throws HomomorphicException if decryption fails
	 */
	public void decrypt_all(DGKPrivateKey sk) throws HomomorphicException {
		this.plainDistance = DGKOperations.decrypt(this.encryptedDistance, sk);
		if(this.matches != null) {
			this.plainDistance = plainDistance/matches;
		}
	}
	
	/**
	 * Decrypts the encrypted distance using ElGamal and divides by matches if needed.
	 *
	 * @param sk ElGamal private key
	 */
	public void decrypt_all(ElGamalPrivateKey sk) {
		try {
			plainDistance = ElGamalCipher.decrypt(e_d, sk).longValue();
		}
		catch(IllegalArgumentException e) {
			plainDistance = Long.MAX_VALUE;
		}
		if(matches != null) {
			plainDistance = plainDistance/matches;
		}
	}

	/**
	 * Divides the plaintext distance by matches (for DMA).
	 */
	public void plain_decrypt() {
		this.plainDistance = plainDistance/matches;
	}

	/**
	 * Gets the plaintext distance.
	 *
	 * @return plaintext distance
	 */
	public Long getPlainDistance() {
		return this.plainDistance;
	}

	/**
	 * Sets the encrypted distance (Paillier/DGK).
	 *
	 * @param d encrypted distance
	 */
	public void setEncryptedDistance(BigInteger d) {
		this.encryptedDistance = d;
	}

	/**
	 * Sets the encrypted distance (ElGamal).
	 *
	 * @param d encrypted distance
	 */
	public void setElGamalEncryptedDistance(ElGamal_Ciphertext d) {
		this.e_d = d;
	}

	/**
	 * Returns a string representation of the result, including coordinates, distance, and matches.
	 *
	 * @return formatted string
	 */
	public String toString() {
		String answer = "";
	    answer += "(x=" + coordinates[0] + ", y=" + coordinates[1] + " ";
	    if(plainDistance != null) {
	    	answer += "d=" + plainDistance;
	    } else {
	    	answer += "[[d]]";
	    }
	    answer += "m=" + matches;
		return answer;
	}
}