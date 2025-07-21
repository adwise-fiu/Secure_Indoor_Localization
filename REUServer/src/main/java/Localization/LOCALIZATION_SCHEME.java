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
	PAILLIER_DMA(9);
	
	public final int value;

	LOCALIZATION_SCHEME(int value) {
		this.value = value;
	}
	
	public static LOCALIZATION_SCHEME from_int(Integer x) {
		switch(x) {
			case -5:
				return UNDO;
			case -4:
				return RESET;
			case -3:
				return GETXY;
			case -2:
				return PROCESS;
			case -1:
				return GET_COLUMN;
			case 0:
				return TRAIN;
			case 1:
				return PLAIN_MIN;
			case 2:
				return DGK_MIN;
			case 3:
				return PAILLIER_MIN;
			case 4:
				return PLAIN_MCA;
			case 5:
				return DGK_MCA;
			case 6:
				return PAILLIER_MCA;
			case 7:
				return PLAIN_DMA;
			case 8:
				return DGK_DMA;
			case 9:
				return PAILLIER_DMA;
		}
		return null;
	}
}