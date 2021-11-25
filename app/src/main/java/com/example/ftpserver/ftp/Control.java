package com.example.ftpserver.ftp;

import com.example.ftpserver.MainActivity;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Control extends Thread {
    public static final int controlPort = 8080;
    public MainActivity mainActivity;
    public String serverIP;

    @Override
    public void run() {
        Session session;
        try (ServerSocket controlListener = new ServerSocket(controlPort)) {

            Socket clientSocket = controlListener.accept();
            session = new Session(clientSocket, mainActivity, serverIP);
            session.run();
        } catch (IOException ignored) {

        }
    }
}

