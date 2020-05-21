# Security of Smart Things REU 2017  
1- REUServer directory holds all the Java code running on the Server  
2- REU2017 contains the Android Client Side Code  

This program requires the MySQL Driver to communicate with FingerPrint server, found here:  
https://dev.mysql.com/downloads/connector/j/  

The JAR file containing all the homomorphic encryption libaries can be located at the following repository:  
https://github.com/AndrewQuijano/Homomorphic_Encryption.git  

If you use this code/library, please cite the following papers:  
1- Efficient privacy-preserving fingerprint-based indoor localization using crowdsourcing  
2- Server-side Fingerprint-Based Indoor Localization
Using Encrypted Sorting  

To place to login credentials for your MySQLServer place a 
login.properities file on the following path:  
./REUServer/  

It will work when you run the compile.sh script or run from Eclipse.  

Please note you should format it as follows:  
username=<username>  
password=<password>  
