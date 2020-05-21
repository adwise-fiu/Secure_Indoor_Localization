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
    		String OS, String Device, String Model, String Product)
    {
    	// Coordinates
        X_coordinate = x;
        Y_coordinate = y;
        
        // (AP, RSS)
        MACAddress = m;
        RSS = in;
        
        // Phone Data
        this.OS = OS;
        this.Device = Device;
        this.Model = Model;
        this.Product = Product;
    }

    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException
    {
        aInputStream.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream aOutputStream) throws IOException
    {
        aOutputStream.defaultWriteObject();
    }

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
