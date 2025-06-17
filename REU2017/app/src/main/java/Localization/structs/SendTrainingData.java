package Localization.structs;

import androidx.annotation.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

public class SendTrainingData implements Serializable {
    private final String Map;
    private final Double X_coordinate;
    private final Double Y_coordinate;
    private final String[] MACAddress;
    private final Integer[] RSS;
    private final String OS;
    private final String Device;
    private final String Model;
    private final String Product;
    @Serial
    private static final long serialVersionUID = 3907495506938576258L;

    public SendTrainingData(String Map, Double x, Double y, String[] m, Integer[] in,
                            String OS, String Device, String Model, String Product) {
        this.Map = Map;
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

    @NonNull
    public String toString() {
        String train = "";
        train += Map + "\n";
        train += "(" + X_coordinate + ", " + Y_coordinate + ")\n";
        train += "OS=" + OS + ", DEVICE=" + Device + ", MODEL=" + Model + ", PRODUCT=" + Product + "\n";
        train += Arrays.toString(MACAddress) + '\n';
        train += Arrays.toString(RSS);
        return train;
    }
}
