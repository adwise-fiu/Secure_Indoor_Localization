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

    public final Long matches;
    
    // Distances
    private Long plainDistance;
    public BigInteger encryptedDistance;
    public ElGamal_Ciphertext e_d;
    
    // Coordinates
    public final Double [] coordinates = new Double[2];
    public final BigInteger [] encryptedCoordinates = new BigInteger[2];
    public final ElGamal_Ciphertext [] e_xy = new ElGamal_Ciphertext[2];
    
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
    	this.encryptedCoordinates[0] = DGKOperations.encrypt(this.coordinates[0].longValue(), pk);
    	this.encryptedCoordinates[1] = DGKOperations.encrypt(this.coordinates[1].longValue(), pk);
    	
    	// The coordinate stored in plain text get nullified
    	this.coordinates[0] = null;
    	this.coordinates[1] = null;
    }
    
	public void add_secret_coordinates(ElGamalPublicKey pk)
	{
    	// Encrypt Coordinates
		this.e_xy[0] = ElGamalCipher.encrypt(pk, this.coordinates[0].longValue());
		this.e_xy[1] = ElGamalCipher.encrypt(pk, this.coordinates[1].longValue());
    	
    	// The coordinate stored in plain text get nullified
		this.coordinates[0] = null;
		this.coordinates[1] = null;
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
			this.plainDistance = plainDistance/matches;
		}
	}
	
	// Used to decrypt, if DMA, just divide by distance as well!
	public void decrypt_all(DGKPrivateKey sk)
	{
		this.plainDistance = DGKOperations.decrypt(sk, this.encryptedDistance);
		if(this.matches != null)
		{
			this.plainDistance = plainDistance/matches;
		}
	}
	
	// Used to decrypt, if DMA, just divide by distance as well!
	public void decrypt_all(ElGamalPrivateKey sk)
	{
		try
		{
			plainDistance = ElGamalCipher.decrypt(sk, e_d).longValue();
		}
		catch(IllegalArgumentException e)
		{
			plainDistance = Long.MAX_VALUE;
		}
		if(matches != null)
		{
			plainDistance = plainDistance/matches;
		}
	}
	
	// Used only for Plain-DMA, divide by matches!
	public void plain_decrypt()
	{
		this.plainDistance = plainDistance/matches;
	}
	
	public Long getPlainDistance()
	{
		return this.plainDistance;
	}
	
	public void setEncryptedDistance(BigInteger d)
	{
		this.encryptedDistance = d;
	}
	
	public void setElGamalEncryptedDistance(ElGamal_Ciphertext d)
	{
		this.e_d = d;
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