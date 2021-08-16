package Localization.structs;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

// Created by Andrew on 7/6/2017.

public class SendTrainingArray implements Serializable
{
    private final Double X_coordinate;
    private final Double Y_coordinate;
    private final String[] MACAddress;
    private final Integer[] RSS;
    
    private final String OS;
    private final String Device;
    private final String Model;
    private final String Product;

    private static final long serialVersionUID = 3907495506938576258L;

    public SendTrainingArray(Double x, Double y, String[] m, Integer[] in,
    		String _OS, String _Device, String _Model, String _Product)
    {
    	// Coordinates
        X_coordinate = x;
        Y_coordinate = y;
        
        // (AP, RSS)
        MACAddress = m;
        RSS = in;
        
        // Phone Data
        OS = _OS;
        Device = _Device;
        Model = _Model;
        Product = _Product;
    }

    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException
    {
        aInputStream.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream aOutputStream) throws IOException
    {
        aOutputStream.defaultWriteObject();
    }

    public Double getX() 			{ return X_coordinate;}
    public Double getY() 			{ return Y_coordinate;}
    public String[] getMACAddress() { return MACAddress; }
    public Integer[] getRSS() 		{ return RSS;		 }

    public String getOS()			{ return OS;		 }
    public String getDevice()		{ return Device;	 }
    public String getModel()		{ return Model; 	 }
    public String getProduct()		{ return Product;	 }

    public String toString()
    {
        String train = "";
        train += "(" + X_coordinate + ", " + Y_coordinate + ")\n";
        train += "OS=" + OS + ", DEVICE=" + Device + ", MODEL=" + Model + ", PRODUCT=" + Product + "\n";
        train += Arrays.toString(MACAddress) + '\n';
        train += Arrays.toString(RSS);
        return train;
    }
}
