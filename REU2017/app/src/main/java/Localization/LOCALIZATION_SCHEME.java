package Localization;
public enum LOCALIZATION_SCHEME {
	UNDO(-5),
	RESET(-4),
	GETXY(-3),
	PROCESS(-2),
	GET_COLUMN(-1),
	TRAIN(0),
	PLAIN_MIN(1), 
	DGK_MIN(2),
	PAILLIER_MIN(3),
	PLAIN_MCA(4),
	DGK_MCA(5),
	PAILLIER_MCA(6),
	PLAIN_DMA(7),
	DGK_DMA(8),
	PAILLIER_DMA(9),
	EL_GAMAL_MIN(10),
	EL_GAMAL_MCA(11),
	EL_GAMAL_DMA(12);
	
	public final int value;

	LOCALIZATION_SCHEME(int value)
	{
		this.value = value;
	}
	
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
            case 10 -> EL_GAMAL_MIN;
            case 11 -> EL_GAMAL_MCA;
            case 12 -> EL_GAMAL_DMA;
            default -> null;
        };
    }
}