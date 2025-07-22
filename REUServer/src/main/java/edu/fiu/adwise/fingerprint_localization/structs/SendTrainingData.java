package edu.fiu.adwise.fingerprint_localization.structs;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Represents a training data sample for indoor localization using Wi-Fi fingerprints.
 * <p>
 * This class encapsulates the map identifier, coordinates, Wi-Fi access point MAC addresses,
 * received signal strengths (RSS), and device metadata.
 * It is serializable for network or file transfer.
 * </p>
 *
 * <p>
 * Typical usage:
 * <pre>
 *     SendTrainingData data = new SendTrainingData(
 *         "Map1", 10.5, 20.3,
 *         new String[]{"00:11:22:33:44:55"}, new Integer[]{-65},
 *         "Android", "Pixel", "Pixel 5", "Google"
 *     );
 * </pre>
 * </p>
 *
 * @author Andrew
 * @since 7/6/2017
 */
public class SendTrainingData implements Serializable {
    /**
     * The map identifier where the training data was collected.
     */
    private final String Map;
    /**
     * The X coordinate of the training data location.
     */
    private final Double X_coordinate;
    /**
     * The Y coordinate of the training data location.
     */
    private final Double Y_coordinate;
    /**
     * Array of Wi-Fi access point MAC addresses detected at the location.
     */
    private final String [] MACAddress;
    /**
     * Array of received signal strength (RSS) values corresponding to the MAC addresses.
     */
    private final Integer[] RSS;
    /**
     * Operating system of the device used for data collection.
     */
    private final String OS;
    /**
     * Device name or identifier.
     */
    private final String Device;
    /**
     * Device model.
     */
    private final String Model;
    /**
     * Device product name.
     */
    private final String Product;

    /**
     * Serialization identifier for compatibility.
     */
    @Serial
    private static final long serialVersionUID = 3907495506938576258L;

    /**
     * Constructs a new {@code SendTrainingData} instance with all required fields.
     *
     * @param Map      the map identifier
     * @param x        the X coordinate
     * @param y        the Y coordinate
     * @param m        array of MAC addresses
     * @param in       array of RSS values
     * @param _OS      operating system
     * @param _Device  device name
     * @param _Model   device model
     * @param _Product device product name
     */
    public SendTrainingData(String Map, Double x, Double y, String[] m, Integer[] in,
    		String _OS, String _Device, String _Model, String _Product) {
    	this.Map = Map;
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

    /**
     * Gets the map identifier.
     *
     * @return the map name
     */
    public String getMap() {
        return Map;
    }

    /**
     * Gets the X coordinate.
     *
     * @return the X coordinate
     */
    public Double getX() {
        return X_coordinate;
    }

    /**
     * Gets the Y coordinate.
     *
     * @return the Y coordinate
     */
    public Double getY() {
        return Y_coordinate;
    }

    /**
     * Gets the array of MAC addresses.
     *
     * @return the MAC address array
     */
    public String[] getMACAddress() {
        return MACAddress;
    }

    /**
     * Gets the array of RSS values.
     *
     * @return the RSS value array
     */
    public Integer[] getRSS() {
        return RSS;
    }

    /**
     * Gets the operating system of the device.
     *
     * @return the operating system
     */
    public String getOS() {
        return OS;
    }

    /**
     * Gets the device name.
     *
     * @return the device name
     */
    public String getDevice() {
        return Device;
    }

    /**
     * Gets the device model.
     *
     * @return the device model
     */
    public String getModel() {
        return Model;
    }

    /**
     * Gets the device product name.
     *
     * @return the product name
     */
    public String getProduct() {
        return Product;
    }

    /**
     * Returns a string representation of the training data, including map, coordinates,
     * device information, MAC addresses, and RSS values.
     *
     * @return a formatted string of the training data
     */
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
