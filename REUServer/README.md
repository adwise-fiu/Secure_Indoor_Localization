# Fingerprint-Based Indoor Localization Server

Please refer to this [document for MySql set-up](https://www.digitalocean.com/community/tutorials/how-to-install-mysql-on-ubuntu-22-04).

If you want to run the tests locally, you will need to install MySQL.
```bash
sudo apt install mysql-server
sudo systemctl start mysql.service
```

## Server Installation
You can run the server using Gradle as following

```bash
./gradlew run
```

## Server set-up
To run the server, you need to populate the environment variables `MYSQL_USER` and `MYSQL_PASSWORD` to access the Fingerprint database.

Note: Upon initialization, it will expect MySQL to be running! If it isn't the server will not turn on. 

Upon initializing the server will  
- If there is preprocessed data, determine the number of APs used for each column
- Assuming a port number was provided, check if it is a valid port number and open the server socket. Default is port 9254
- Create the database called "FIU" and a Table called "trainingpoints" to store the raw Wi-Fi scans.

It will also create a shell to do the following operations:  
- **clr** Clear the shell
- **print** Assuming the fingerprint database preprocessed the training data, print the lookup tables to a CSV file.
- **frequency** Print a frequency map of all detected Access Points. Use this to tune your FSF parameter.
- **k <int>** Disabled for now. 
- **test-FSF [0, 1]** For a value between 0 and 1, determine how many access points would get filtered.
- **FSF [0, 1]** Sets new FSF parameter. However, if you want to see this change, you will need to build a new lookup table.
- **process** Build the lookup table for all floor maps
- **reset** delete all lookup tables
- **exit** close the shell and server

Upon each iteration of the shell, it will update you on:
- If the fingerprint server has a lookup table
- Number of columns in the lookup table
- Number of Access Points detected in training data
- Number of fingerprints of each floor map
- Current value of K, for MCA/DMA (default set to 2). Currently, this is hard set as I fail to see the benefit in changing this value.

## Training Workflow

## Localization Workflow

## Contributing
Pull requests are welcome. 
For major changes, please open an issue first to discuss what you would like to change.

## Authors and acknowledgment
Code Author: Andrew Quijano

A. Quijano and K. Akkaya, ["Server-Side Fingerprint-Based Indoor Localization Using Encrypted Sorting,"](https://arxiv.org/abs/2008.11612) 
2019 IEEE 16th International Conference on Mobile Ad Hoc and Sensor Systems Workshops (MASSW), Monterey, CA, USA, 2019, 
pp. 53-57, doi: 10.1109/MASSW.2019.00017

P. Armengol, R. Tobkes, K. Akkaya, B. S. Ciftler and I. Guvenc, 
"Efficient Privacy-Preserving Fingerprint-Based Indoor Localization Using Crowdsourcing," 
2015 IEEE 12th International Conference on Mobile Ad Hoc and Sensor Systems, Dallas, TX, 2015, pp. 549-554, 
doi: 10.1109/MASS.2015.76.

The work to create this repository was initially funded by the US NSF REU Site at FIU under the grant number REU CNS-1461119.

## License
[MIT](https://choosealicense.com/licenses/mit/)

## Project status
The project is functional at its current state. However, some optimizations 
do need to made for ease of use. 
This would only work best if I can re-obtain access to Broadway to 
test on multiple floors/add new floors as needed.

Also, there are a few things I want to upgrade:
- Streamline the process of the client uploading/receiving floor maps.
- See how to allow the client to set the value **k** for localizations.
- I will provide screenshots on the Localization/Training Phase later this week.