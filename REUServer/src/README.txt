This Folder contains the code to run the Wi-Fi Localization Server.
This project is another situation of the Socialist Millionaire's problem. Where there must be a way for
the server to know to compare two encrypted values without anyone know the values being compared.

The solution this project uses is from a paper:
"Improving the DGK Comparison Protocol" By Thjis Veugen, Published 2012 in IEEE Transactions.

If you just want to code to compare encrypted numbers (Paillier and DGK). The modules you need are
- CompareClient
- CompareThread (Use with a Multi-Threaded Server, see Server.java and run CompareThread instead of LocalizationThread).
- DGKOperations, DGKPublicKey, DGKPrivateKey, and Paillier Public/Private Key classes.