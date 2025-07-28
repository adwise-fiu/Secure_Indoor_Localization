package ui;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import static ui.MainActivity.SQLDatabase;
import static ui.MainActivity.TIMEOUT;
import static ui.MainActivity.portNumber;

public class test_connection implements Runnable {
    boolean connected = false;
    public void run() {
        try (Socket ClientSocket = new Socket()) {
            ClientSocket.connect(new InetSocketAddress(SQLDatabase, portNumber), TIMEOUT);
            ObjectOutputStream toServer = new ObjectOutputStream(ClientSocket.getOutputStream());
            toServer.writeObject("Hello");
            toServer.flush();
            connected = true;
        }
        catch (IOException e) {
            connected = false;
        }
    }
}
