package edu.fiu.adwise.fingerprint_localization.ui;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import static edu.fiu.adwise.fingerprint_localization.ui.MainActivity.SQLDatabase;
import static edu.fiu.adwise.fingerprint_localization.ui.MainActivity.TIMEOUT;
import static edu.fiu.adwise.fingerprint_localization.ui.MainActivity.portNumber;

public class test_connection implements Runnable {
    boolean connected = false;
    public void run() {
        try (Socket ClientSocket = new Socket()) {
            ClientSocket.connect(new InetSocketAddress(SQLDatabase, portNumber), TIMEOUT);
            try(ObjectOutputStream toServer = new ObjectOutputStream(ClientSocket.getOutputStream())) {
                toServer.writeObject("Hello");
                toServer.flush();
            }
            connected = true;
        }
        catch (IOException e) {
            connected = false;
        }
    }
}
