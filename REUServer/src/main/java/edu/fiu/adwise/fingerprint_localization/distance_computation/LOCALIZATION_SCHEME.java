package edu.fiu.adwise.fingerprint_localization.distance_computation;

/**
 * Enumeration of supported localization schemes and server commands for Wi-Fi fingerprint localization.
 * <p>
 * Each constant represents a specific operation or algorithm, including plaintext and homomorphic encryption
 * (DGK, Paillier) variants for minimum, MCA, and DMA computations, as well as server control commands.
 * </p>
 *
 * <ul>
 *   <li>UNDO, RESET, GETXY, PROCESS, GET_COLUMN: Server control commands</li>
 *   <li>TRAIN: Training mode</li>
 *   <li>PLAIN_MIN, DGK_MIN, PAILLIER_MIN: Minimum distance localization (plain/encrypted)</li>
 *   <li>PLAIN_MCA, DGK_MCA, PAILLIER_MCA: MCA localization (plain/encrypted)</li>
 *   <li>PLAIN_DMA, DGK_DMA, PAILLIER_DMA: DMA localization (plain/encrypted)</li>
 * </ul>
 *
 * Each enum constant is associated with an integer value for protocol or configuration mapping.
 *
 * @author Andrew Quijano
 * @since 2017-07-06
 */

public enum LOCALIZATION_SCHEME {
	/** Undo training data insersetion operation. */
	UNDO(-5),
	/** Reset server, delete all training points and lookup table */
	RESET(-4),
	/** Get XY coordinates. */
	GETXY(-3),
	/** Process lookup tables using training data */
	PROCESS(-2),
	/** Get column information. a list of MAC Addresses */
	GET_COLUMN(-1),
	/** Place training data */
	TRAIN(0),
	/** Plaintext minimum distance localization. */
	PLAIN_MIN(1),
	/** DGK-encrypted minimum distance localization. */
	DGK_MIN(2),
	/** Paillier-encrypted minimum distance localization. */
	PAILLIER_MIN(3),
	/** Plaintext MCA localization. */
	PLAIN_MCA(4),
	/** DGK-encrypted MCA localization. */
	DGK_MCA(5),
	/** Paillier-encrypted MCA localization. */
	PAILLIER_MCA(6),
	/** Plaintext DMA localization. */
	PLAIN_DMA(7),
	/** DGK-encrypted DMA localization. */
	DGK_DMA(8),
	/** Paillier-encrypted DMA localization. */
	PAILLIER_DMA(9);

	/** Integer value associated with the scheme or command. */
	public final int value;

	/**
	 * Constructs a localization scheme with the specified integer value.
	 *
	 * @param value the integer value representing the scheme or command
	 */
	LOCALIZATION_SCHEME(int value) {
		this.value = value;
	}

	/**
	 * Returns the {@code LOCALIZATION_SCHEME} corresponding to the given integer value.
	 *
	 * @param x the integer value
	 * @return the matching {@code LOCALIZATION_SCHEME}, or {@code null} if not found
	 */
	public static LOCALIZATION_SCHEME from_int(Integer x) {
        return switch (x) {
            case -5 -> UNDO;
            case -4 -> RESET;
            case -3 -> GETXY;
            case -2 -> PROCESS;
            case -1 -> GET_COLUMN;
            case 0 -> TRAIN;
            case 1 -> PLAIN_MIN;
            case 2 -> DGK_MIN;
            case 3 -> PAILLIER_MIN;
            case 4 -> PLAIN_MCA;
            case 5 -> DGK_MCA;
            case 6 -> PAILLIER_MCA;
            case 7 -> PLAIN_DMA;
            case 8 -> DGK_DMA;
            case 9 -> PAILLIER_DMA;
            default -> null;
        };
    }
}