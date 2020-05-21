#!/bin/bash

# Only run from Root!
javac -cp ".:./lib/crypto.jar:./lib/mysql-connector.jar" -sourcepath "./src" ./Localization/server.java 

echo "Java compilation complete!"

# Run the program
java -cp ".:./lib/crypto.jar:./lib/mysql-connector.jar:./bin" Localization.server

