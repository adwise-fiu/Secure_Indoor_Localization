#!/bin/bash
clear
# Only run from Root!
javac -cp "./src/main/java/:./lib/crypto.jar:./lib/mysql-connector.jar" -sourcepath "./src/main/java/" ./src/main/java/Localization/server.java

echo "Java compilation complete!"

# Run the program
java -cp "./src/main/java/:./lib/crypto.jar:./lib/mysql-connector.jar" Localization.server
