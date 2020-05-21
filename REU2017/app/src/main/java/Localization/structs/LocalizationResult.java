package Localization.structs;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;

import security.DGK.DGKOperations;
import security.DGK.DGKPrivateKey;
import security.DGK.DGKPublicKey;
import security.elgamal.ElGamalCipher;
import security.elgamal.ElGamalPrivateKey;
import security.elgamal.ElGamalPublicKey;
import security.elgamal.ElGamal_Ciphertext;
import security.paillier.PaillierCipher;
import security.paillier.PaillierPrivateKey;
import security.paillier.PaillierPublicKey;

public class LocalizationResult implements Serializable, Comparable<LocalizationResult>
{
    private static final long serialVersionUID = -1884589588377067950L;

    public final Double [] coordinates = new Double[2];
    public final Long matches;
    public Long plainDistance;
    public final BigInteger encryptedDistance;
    public final BigInteger [] encryptedCoordinates = new BigInteger[2];
    
    // ElGamal Encrypted Distance
    public final ElGamal_Ciphertext e_d;
    
    // For ElGamal Encrypted coordinates
    public ElGamal_Ciphertext [] e_xy = new ElGamal_Ciphertext[2];
    
    // PlainText Distances
    public LocalizationResult(Double x, Double  y, Long distance, Long matches)
    {
    	this.coordinates[0] = x;
        this.coordinates[1] = y;
        this.plainDistance = distance;
        this.matches = matches;
        this.encryptedDistance = null;
        this.e_d = null;
    }
    
    // Paillier/DGK Distances
    public LocalizationResult(Double x, Double y, BigInteger distance, Long matches)
    {
    	this.coordinates[0] = x;
        this.coordinates[1] = y;
        this.encryptedDistance = distance;
        this.e_d = null;
        this.matches = matches;
        this.plainDistance = null;
    }
    
    // ElGamal
    public LocalizationResult(Double x, Double y, ElGamal_Ciphertext distance, Long matches)
    {
    	this.coordinates[0] = x;
        this.coordinates[1] = y;
        this.e_d = distance;
        this.encryptedDistance = null;
        this.matches = matches;
        this.plainDistance = null;
    }
    
    public void add_secret_coordinates(PaillierPublicKey pk)
    {
    	// Encrypt Coordinates
    	encryptedCoordinates[0] = PaillierCipher.encrypt(coordinates[0].longValue(), pk);
    	encryptedCoordinates[1] = PaillierCipher.encrypt(coordinates[1].longValue(), pk);
    	
    	// The coordinate stored in plain text get nullified
    	coordinates[0] = null;
    	coordinates[1] = null;
    }
    
    public void add_secret_coordinates(DGKPublicKey pk)
    {
    	// Encrypt Coordinates
    	encryptedCoordinates[0] = DGKOperations.encrypt(coordinates[0].longValue(), pk);
    	encryptedCoordinates[1] = DGKOperations.encrypt(coordinates[1].longValue(), pk);
    	
    	// The coordinate stored in plain text get nullified
    	coordinates[0] = null;
    	coordinates[1] = null;
    }
    
	public void add_secret_coordinates(ElGamalPublicKey pk)
	{
    	// Encrypt Coordinates
    	e_xy[0] = ElGamalCipher.encrypt(pk, coordinates[0].longValue());
    	e_xy[1] = ElGamalCipher.encrypt(pk, coordinates[1].longValue());
    	
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
    
    private void readObject(ObjectInputStream aInputStream) 
    		throws ClassNotFoundException, IOException
    {
        aInputStream.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream aOutputStream) 
    		throws IOException
    {
    	aOutputStream.defaultWriteObject();
    }
    
	public int compareTo(LocalizationResult o)
	{
		return plainDistance.compareTo(o.plainDistance);
	}
	
	// Used to decrypt, if DMA, just divide by distance as well!
	public void decrypt_all(PaillierPrivateKey sk)
	{
		this.plainDistance = PaillierCipher.decrypt(encryptedDistance, sk).longValue();
		if(matches != null)
		{
			if(matches.longValue() == 0)
			{
				this.plainDistance = Long.MAX_VALUE;
			}
			else
			{
				this.plainDistance = plainDistance/matches;
			}
		}
	}
	
	// Used to decrypt, if DMA, just divide by distance as well!
	public void decrypt_all(DGKPrivateKey sk)
	{
		this.plainDistance = DGKOperations.decrypt(sk, this.encryptedDistance);
		if(this.matches != null)
		{
			if(matches.longValue() == 0)
			{
				this.plainDistance = Long.MAX_VALUE;
			}
			else
			{
				this.plainDistance = plainDistance/matches;
			}
		}
	}
	
	// Used to decrypt, if DMA, just divide by distance as well!
	public void decrypt_all(ElGamalPrivateKey sk)
	{
		try
		{
			plainDistance = ElGamalCipher.decrypt(sk, e_d).longValue();
			if(matches.longValue() == 0)
			{
				this.plainDistance = Long.MAX_VALUE;
			}
			else
			{
				this.plainDistance = plainDistance/matches;
			}
		}
		catch(IllegalArgumentException e)
		{
			plainDistance = Long.MAX_VALUE;
		}
	}
	
	// Used only for Plain-DMA, divide by matches!
	public void plain_decrypt()
	{
		if(matches.longValue() == 0)
		{
			this.plainDistance = Long.MAX_VALUE;
		}
		else
		{
			this.plainDistance = plainDistance/matches;
		}
	}
	
	public String toString()
	{
		String answer = "";
	    answer += "(x=" + coordinates[0] + ", y=" + coordinates[1] + " ";
	    if(plainDistance != null)
	    {
	    	answer += "d=" + plainDistance;
	    }
	    else
	    {
	    	answer += "[[d]]";
	    }
	    answer += "m=" + matches;
		return answer;
	}
}