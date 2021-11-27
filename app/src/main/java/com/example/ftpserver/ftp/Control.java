package com.example.ftpserver.ftp;

import com.example.ftpserver.MainActivity;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Control extends Thread {
    public static final int controlPort = 8080;
    private static final int dataPort = 8081;
    public MainActivity mainActivity;
    public String serverIP;

    @Override
    public void run() {
        Session session;
        try (ServerSocket controlListener = new ServerSocket(controlPort);
             ServerSocket dataListener = new ServerSocket(dataPort)) {
            Socket clientSocket;
            while ((clientSocket = controlListener.accept()) != null) {
                session = new Session(clientSocket, dataListener, mainActivity, serverIP);
                session.run();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

