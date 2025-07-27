package edu.fiu.adwise.fingerprint_localization.structs;

import java.io.*;
import java.math.BigInteger;
import java.util.List;

import edu.fiu.adwise.fingerprint_localization.distance_computation.LOCALIZATION_SCHEME;
import edu.fiu.adwise.homomorphic_encryption.dgk.DGKPublicKey;
import edu.fiu.adwise.homomorphic_encryption.elgamal.ElGamalPublicKey;
import edu.fiu.adwise.homomorphic_encryption.elgamal.ElGamal_Ciphertext;
import edu.fiu.adwise.homomorphic_encryption.paillier.PaillierPublicKey;

/**
 * Represents a data structure for transmitting localization data between client and server.
 * <p>
 * Supports both plaintext and secure (homomorphic encryption) representations of Wi-Fi fingerprint data,
        * including RSS values, access point identifiers, and cryptographic triples for privacy-preserving localization.
        * </p>
        * <p>
 * The class is serializable for network communication and supports multiple encryption schemes:
        * DGK, Paillier, and ElGamal. It also includes device metadata and map information for context-aware localization.
        * </p>
        *
        * <ul>
 *   <li>Plaintext mode: RSS and APs are sent directly.</li>
        *   <li>Secure mode: RSS and APs are replaced by encrypted triples and public keys.</li>
        * </ul>
        *
        * @author Andrew Quijano
 * @since 2017-07-06
 */
public class SendLocalizationData implements Serializable {

    /** The localization scheme used (e.g., plaintext, DGK, Paillier, ElGamal). */
    public final LOCALIZATION_SCHEME LOCALIZATION_SCHEME;

    /** Received Signal Strength (RSS) values for each access point (plaintext mode). */
    public final Integer[] RSS;

    /** Access point MAC addresses */
    public final String[] APs;

    /** Encrypted S2 values for secure triple computation (Paillier/DGK). */
    public final BigInteger[] S2;

    /** Encrypted S3 comparison values for secure triple computation (Paillier/DGK). */
    public final BigInteger[] S3_comp;

    /** Encrypted S3 value for secure triple computation (Paillier/DGK). */
    public final BigInteger S3;

    /** Encrypted S2 values for secure triple computation (ElGamal). */
    public final List<ElGamal_Ciphertext> e_S2;

    /** Encrypted S3 comparison values for secure triple computation (ElGamal). */
    public final List<ElGamal_Ciphertext> e_S3_comp;

    /** Encrypted S3 value for secure triple computation (ElGamal). */
    public final ElGamal_Ciphertext e_S3;

    /** Serialization identifier for compatibility. */
    @Serial
    private static final long serialVersionUID = 201194517759072124L;

    /** ElGamal public key for encryption (ElGamal mode). */
    public final ElGamalPublicKey e_pk;

    /** DGK public key for encryption (DGK mode). */
    public final DGKPublicKey pubKey;

    /** Paillier public key for encryption (Paillier mode) */
    public final PaillierPublicKey pk;

    /** Indicates if the REU2017 mode is enabled (legacy compatibility). */
    public final boolean isREU2017;

    /** Device metadata (e.g., OS, model, product) for filtering or context. */
    public final String[] phone_data;

    /** Map identifier for the localization context. */
    public final String map;

    /**
     * Constructs a plaintext localization data object.
     *
     * @param APs        array of access point MAC addresses
     * @param RSS        array of RSS values
     * @param pubKey     DGK public key (for future secure operations)
     * @param local      localization scheme
     * @param isREU2017  REU2017 mode flag
     * @param phone_data device metadata
     * @param map        map identifier
     */
    public SendLocalizationData(String[] APs, Integer[] RSS, DGKPublicKey pubKey,
                                LOCALIZATION_SCHEME local, boolean isREU2017,
                                String[] phone_data, String map) {
        this.RSS = RSS;
        this.APs = APs;
        this.LOCALIZATION_SCHEME = local;

        this.S2 = null;
        this.S3 = null;
        this.S3_comp = null;
        this.e_S2 = null;
        this.e_S3 = null;
        this.e_S3_comp = null;

        this.pubKey = pubKey;
        this.pk = null;
        this.e_pk = null;
        this.isREU2017 = isREU2017;
        this.phone_data = phone_data;
        this.map = map;
    }

    /**
     * Constructs a Paillier-encrypted localization data object.
     *
     * @param APs        array of access point MAC addresses
     * @param S2         encrypted S2 values
     * @param S3         encrypted S3 value
     * @param S3_comp    encrypted S3 comparison values
     * @param pk         Paillier public key
     * @param _pubKey    DGK public key (for comparison protocol)
     * @param local      localization scheme
     * @param isREU2017  REU2017 mode flag
     * @param phone_data device metadata
     * @param map        map identifier
     */
    public SendLocalizationData(String[] APs, BigInteger[] S2, BigInteger S3,
                                BigInteger[] S3_comp, PaillierPublicKey pk,
                                DGKPublicKey _pubKey, LOCALIZATION_SCHEME local,
                                boolean isREU2017, String[] phone_data, String map) {
        this.RSS = null;
        this.APs = APs;
        this.S2 = S2;
        this.S3 = S3;
        this.S3_comp = S3_comp;
        this.e_S2 = null;
        this.e_S3 = null;
        this.e_S3_comp = null;

        this.LOCALIZATION_SCHEME = local;
        this.pubKey = _pubKey;
        this.pk = pk;
        this.e_pk = null;
        this.isREU2017 = isREU2017;
        this.phone_data = phone_data;
        this.map = map;
    }

    /**
     * Constructs a DGK-encrypted localization data object.
     *
     * @param APs        array of access point MAC addresses
     * @param S2         encrypted S2 values
     * @param S3         encrypted S3 value
     * @param S3_comp    encrypted S3 comparison values
     * @param pubKey     DGK public key
     * @param local      localization scheme
     * @param isREU2017  REU2017 mode flag
     * @param phone_data device metadata
     * @param map        map identifier
     */
    public SendLocalizationData(String[] APs, BigInteger[] S2,
                                BigInteger S3, BigInteger[] S3_comp,
                                DGKPublicKey pubKey, LOCALIZATION_SCHEME local,
                                boolean isREU2017, String[] phone_data, String map) {
        this.RSS = null;
        this.APs = APs;
        this.S2 = S2;
        this.S3 = S3;
        this.S3_comp = S3_comp;
        this.e_S2 = null;
        this.e_S3 = null;
        this.e_S3_comp = null;

        this.LOCALIZATION_SCHEME = local;
        this.pubKey = pubKey;
        this.pk = null;
        this.e_pk = null;
        this.isREU2017 = isREU2017;
        this.phone_data = phone_data;
        this.map = map;
    }

    /**
     * Constructs an ElGamal-encrypted localization data object.
     *
     * @param APs        array of access point MAC addresses
     * @param e_S2       encrypted S2 values (ElGamal)
     * @param e_S3       encrypted S3 value (ElGamal)
     * @param e_S3_comp  encrypted S3 comparison values (ElGamal)
     * @param pk         ElGamal public key
     * @param pubKey     DGK public key (for comparison protocol)
     * @param local      localization scheme
     * @param isREU2017  REU2017 mode flag
     * @param phone_data device metadata
     * @param map        map identifier
     */
    public SendLocalizationData(String[] APs, List<ElGamal_Ciphertext> e_S2,
                                ElGamal_Ciphertext e_S3,
                                List<ElGamal_Ciphertext> e_S3_comp,
                                ElGamalPublicKey pk, DGKPublicKey pubKey, LOCALIZATION_SCHEME local,
                                boolean isREU2017, String[] phone_data, String map) {
        this.RSS = null;
        this.APs = APs;
        this.S2 = null;
        this.S3 = null;
        this.S3_comp = null;
        this.e_S2 = e_S2;
        this.e_S3 = e_S3;
        this.e_S3_comp = e_S3_comp;
        this.LOCALIZATION_SCHEME = local;
        this.pubKey = pubKey;
        this.pk = null;
        this.e_pk = pk;
        this.isREU2017 = isREU2017;
        this.phone_data = phone_data;
        this.map = map;
    }
}