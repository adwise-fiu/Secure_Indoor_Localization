package Localization.structs;

import androidx.annotation.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;

import edu.fiu.adwise.homomorphic_encryption.dgk.DGKOperations;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPublicKey;
import edu.fiu.adwise.homomorphic_encryption.misc.HomomorphicException;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierCipher;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPrivateKey;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPublicKey;

public class LocalizationResult implements Serializable, Comparable<LocalizationResult> {
    @Serial
	private static final long serialVersionUID = -1884589588377067950L;

    public final Double [] coordinates = new Double[2];
    public final Long matches;
    public Long plainDistance;
    public final BigInteger encryptedDistance;
    public final BigInteger [] encryptedCoordinates = new BigInteger[2];
    
    // PlainText Distances
    public LocalizationResult(Double x, Double  y, Long distance, Long matches) {
    	this.coordinates[0] = x;
        this.coordinates[1] = y;
        this.plainDistance = distance;
        this.matches = matches;
        this.encryptedDistance = null;
    }
    
    // Paillier/DGK Distances
    public LocalizationResult(Double x, Double y, BigInteger distance, Long matches) {
    	this.coordinates[0] = x;
        this.coordinates[1] = y;
        this.encryptedDistance = distance;
        this.matches = matches;
        this.plainDistance = null;
    }
    
    public void add_secret_coordinates(PaillierPublicKey pk) throws HomomorphicException {
    	// Encrypt Coordinates
    	encryptedCoordinates[0] = PaillierCipher.encrypt(coordinates[0].longValue(), pk);
    	encryptedCoordinates[1] = PaillierCipher.encrypt(coordinates[1].longValue(), pk);
    	
    	// The coordinate stored in plain text get nullified
    	coordinates[0] = null;
    	coordinates[1] = null;
    }
    
    public void add_secret_coordinates(DGKPublicKey pk) throws HomomorphicException {
    	// Encrypt Coordinates
    	encryptedCoordinates[0] = DGKOperations.encrypt(coordinates[0].longValue(), pk);
    	encryptedCoordinates[1] = DGKOperations.encrypt(coordinates[1].longValue(), pk);
    	
    	// The coordinate stored in plain text get nullified
    	coordinates[0] = null;
    	coordinates[1] = null;
    }
    
    public Double getX()
    {
    	return coordinates[0];
    }

    public Double getY()
    {
    	return coordinates[1];
    }
    
	public int compareTo(LocalizationResult o)
	{
		return plainDistance.compareTo(o.plainDistance);
	}
	
	// Used to decrypt, if DMA, just divide by distance as well!
	public void decrypt_all(PaillierPrivateKey sk) throws HomomorphicException {
        assert encryptedDistance != null;
        this.plainDistance = PaillierCipher.decrypt(encryptedDistance, sk).longValue();
		if(matches != null) {
			if(matches == 0) {
				this.plainDistance = Long.MAX_VALUE;
			} else {
				this.plainDistance = plainDistance/matches;
			}
		}
	}
	
	// Used to decrypt, if DMA, just divide by distance as well!
	public void decrypt_all(DGKPrivateKey sk) throws HomomorphicException {
        assert this.encryptedDistance != null;
        this.plainDistance = DGKOperations.decrypt(this.encryptedDistance, sk);
		if(this.matches != null) {
			if(matches == 0) {
				this.plainDistance = Long.MAX_VALUE;
			} else {
				this.plainDistance = plainDistance/matches;
			}
		}
	}
	
	// Used only for Plain-DMA, divide by matches!
	public void plain_decrypt()
	{
		if(matches == 0)
		{
			this.plainDistance = Long.MAX_VALUE;
		}
		else
		{
			this.plainDistance = plainDistance/matches;
		}
	}
	
	@NonNull
	public String toString() {
		String answer = "";
	    answer += "(x=" + coordinates[0] + ", y=" + coordinates[1] + " ";
	    if(plainDistance != null) {
	    	answer += "d=" + plainDistance;
	    }
	    else {
	    	answer += "[[d]]";
	    }
	    answer += "m=" + matches;
		return answer;
	}
}