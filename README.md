# Security of Smart Things REU 2017  
This repository contains all the code used to complete the Security of Smart Things REU project of privacy preserving indoor localization.

1- REUServer directory contains all the Java code running on the Fingerprint Server
2- REU2017 contains the Android Client Side Code

This program requires the MySQL Driver to communicate with FingerPrint server, found here:  
https://dev.mysql.com/downloads/connector/j/

The JAR file containing all the homomorphic encryption schemes can be located at the following repository and with its documentation:  
https://github.com/AndrewQuijano/Homomorphic_Encryption.git  

## Installation

** Server Installation
The Java code was written using an Eclipse project. Upon downloading the repository import the project located in REUServer folder.

You can either create a Runnable Jar file to run the server or run it from Eclipse.
If you are in a Linux environment you can run the **compile.sh**

** Android Installation
Please note the Android installation has only been tested on Samsung Galaxy 3. But it should work with all other Android devices.

Upon downloading the repository, open the REU2017 folder using Android Studio. Follow the instructions here on how to load an application onto your phone:
https://developer.android.com/training/basics/firstapp/running-app

## Usage

** Server set up
Please place your MySQL server login crendetials on the empty login.properties file
./REUServer/src/Localization/login.properties

Please note you should format it as follows:  
username=<username>  
password=<password>  

Note: Upon initialization, it will expect MySQL to be running! If it isn't the server will not turn on. 

Upon initializing the server will  
- Read login.properties for MySQL credentials
- If there is preprocessed data, determine the number of APs used for each column
- Assuming a port number was provided, check if it is a valid port number and open the server socket. Default is port 9254
- Create the database called "FIU" and a Table called "trainingpoints" to store the raw Wi-Fi scans.

It will also create a shell to do the following operations:  


** Phone setup
This is the 
![Main Menu]((https://github.com/AndrewQuijano/SST_REU_2017/blob/master/images/main-menu.png?raw=true)

- Reset: Delete the Lookup table on the server
- Undo: Delete the last scan of Access Points/Wi-Fi signals
- Train Database: Open new menu, which opens a floor map to train with Wi-Fi APs and scans
- Process DB: Create lookup tables based on the training data
- Scan: TO BE UPDATED. Update server of new maps
- Localize: Open up new menu, and select which floor map you want to find your location in.
- Localization Scheme: On the bottom right, you can select a combination of server/client side, homormorphic encryption scheme, and localization algorithm.

** Training Workflow

** Localization Workflow

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## Authors and acknowledgment

A. Quijano and K. Akkaya, "Server-Side Fingerprint-Based Indoor Localization Using Encrypted Sorting," 2019 IEEE 16th International Conference on Mobile Ad Hoc and Sensor Systems Workshops (MASSW), Monterey, CA, USA, 2019, pp. 53-57, doi: 10.1109/MASSW.2019.00017.https://arxiv.org/abs/2008.11612

P. Armengol, R. Tobkes, K. Akkaya, B. S. Çiftler and I. Güvenç, "Efficient Privacy-Preserving Fingerprint-Based Indoor Localization Using Crowdsourcing," 2015 IEEE 12th International Conference on Mobile Ad Hoc and Sensor Systems, Dallas, TX, 2015, pp. 549-554, doi: 10.1109/MASS.2015.76.

The work to create this repository was initially funded by the US NSF REU Site at FIU under the grant number REU CNS-1461119.

## License
[MIT](https://choosealicense.com/licenses/mit/)

## Project status
The project is functional at its current state. However some optimizations do need to made for ease of use. This would only work best if I can reobtain access to Broadway to test on multiple floors/add new floors as needed.
