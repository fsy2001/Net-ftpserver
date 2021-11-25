package com.example.ftpserver.ftp;

import android.annotation.SuppressLint;

import com.example.ftpserver.MainActivity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Session {
    private static final int dataPort = 8081;

    private final ServerSocket dataListener;
    private final Socket controlSocket;
    private final PrintWriter out;
    private final BufferedReader in;
    private final MainActivity activity;
    private final String serverIP;

    /* 连接上下文变量 */

    private boolean authenticated = false;
    private boolean binaryMode = true;
    private boolean passive = true;
    private String clientIp;
    private Integer clientPort = null;

    public Session(Socket controlSocket, MainActivity mainActivity, String serverIP) throws IOException {
        this.controlSocket = controlSocket;
        this.dataListener = new ServerSocket(dataPort);
        out = new PrintWriter(controlSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        clientIp = controlSocket.getInetAddress().getHostAddress();
        this.activity = mainActivity;
        this.serverIP = serverIP;
    }

    public void run() {
        boolean alive = true;
        try {
            while (alive) {
                String message = in.readLine();
                String[] parts = message.split("\\s+");
                if (parts.length == 0)
                    out.println("503 syntax error");
                String command = parts[0];

                switch (command.toUpperCase()) {
                    case "USER":
                        handleLogin(parts);
                        break;

                    // PASS已经涵盖在handleLogin中，单独的PASS是语法错误，故此处没有专门分支

                    case "TYPE":
                        switchType(parts);
                        break;

                    case "PORT":
                        parseAddress(parts);
                        break;

                    case "PASV":
                        setPassive();
                        break;

                    case "RETR":
                        sendFile(parts);
                        break;

                    case "STOR":
                        receiveFile(parts);
                        break;

                    case "NOOP":
                    case "MODE":
                    case "STRU":
                        out.println("200 ok");
                        break;

                    case "QUIT":
                        out.println("221 bye");
                        alive = false;
                        break;

                    default:
                        out.println("503 syntax error");
                }
            }
            controlSocket.close();
        } catch (IOException ignored) {

        }
    }

    private void handleLogin(String[] parts) throws IOException {
        /* 校验用户名 */
        if (parts.length < 2) {
            out.println("503 syntax error");
            return;
        }
        String username = parts[1];
        if (!username.equals("admin")) { // TODO: 改成真正的校验逻辑
            out.println("530 access denied");
            return;
        }
        out.println("331 password required");

        /* 校验密码 */
        String[] followup = in.readLine().split("\\s+");
        if (followup.length < 2 || !followup[0].equalsIgnoreCase("PASS")) { // 短路
            out.println("503 syntax error");
            return;
        }
        String password = followup[1];
        if (password.equals("123456")) { // TODO: 改成真正的校验逻辑
            out.println(String.format("230 user %s logged in", username));
            authenticated = true;
        } else {
            out.println("530 password incorrect");
        }
    }

    private void switchType(String[] parts) {
        if (parts.length < 2) {
            out.println("503 syntax error");
            return;
        }
        String type = parts[1];
        switch (type.toUpperCase()) {
            case "A":
                binaryMode = false;
                out.println("200 type set to A");
                break;

            case "I":
                binaryMode = true;
                out.println("200 type set to I");
                break;
            default:
                out.println("503 syntax error");
        }
    }

    private void parseAddress(String[] parts) {
        /* 检查输入格式是否合法，参数须为逗号分割的6个数字 */
        if (parts.length < 2) {
            out.println("503 syntax error");
            return;
        }
        String param = parts[1];
        String[] addressParts = param.split(",");
        if (addressParts.length != 6) {
            out.println("503 syntax error");
            return;
        }

        try {
            /* 检查IP地址格式是否合法 */
            int[] ipArray = {Integer.parseInt(addressParts[0]),
                    Integer.parseInt(addressParts[1]),
                    Integer.parseInt(addressParts[2]),
                    Integer.parseInt(addressParts[3])};
            for (Integer i : ipArray) {
                if (i < 0 || i >= 256) {
                    out.println("503 syntax error");
                    return;
                }
            }
            @SuppressLint("DefaultLocale")
            String ip = String.format("%d.%d.%d.%d", ipArray[0], ipArray[1], ipArray[2], ipArray[3]);

            /* 检查端口号是否合法 */
            int port = Integer.parseInt(addressParts[4]) * 256 + Integer.parseInt(addressParts[5]);
            if (port < 0) {
                out.println("503 syntax error");
                return;
            }

            /* 存储到上下文 */
            clientIp = ip;
            clientPort = port;
            passive = false;
            out.println("200 PORT command successful");
        } catch (NumberFormatException e) {
            out.println("503 syntax error");
        }
    }

    private void setPassive() {
        @SuppressLint("DefaultLocale")
        String port = String.format("%d,%d", dataPort / 256, dataPort % 256);
        String ip = serverIP.replace('.', ',');
        passive = true;
        out.println(String.format("227 %s,%s", ip, port));
    }

    private void sendFile(String[] parts) {
        /* 校验 */
        if (parts.length < 2) {
            out.println("503 syntax error");
            return;
        }

        if (!authenticated) {
            out.println("530 access denied");
            return;
        }

        String filename = parts[1];

        InputStream fileStream;
        try {
            fileStream = activity.getInputStream(filename);
            if (fileStream == null) throw new FileNotFoundException();
        } catch (FileNotFoundException e) {
            out.println("451 file not found");
            return;
        }

        /* 开始传输 */
        try {
            out.println(String.format("150 opening %s data connection", binaryMode ? "BINARY" : "ASCII"));

            Socket dataConn = createDataConnection();

            if (binaryMode)
                binaryDump(fileStream, dataConn.getOutputStream());
            else
                textDump(fileStream, dataConn.getOutputStream());

            dataConn.close();
            out.println("226 transfer complete");
        } catch (IOException e) {
            out.println("425 could not create connection");
        }


    }

    private void receiveFile(String[] parts) {
        /* 校验 */
        if (parts.length < 2) {
            out.println("503 syntax error");
            return;
        }

        if (!authenticated) {
            out.println("530 access denied");
            return;
        }
        String filename = parts[1];

        OutputStream fileStream;
        try {
            fileStream = activity.createFile(filename);
            if (fileStream == null) throw new FileNotFoundException();
        } catch (FileNotFoundException e) {
            out.println("452 conflict filename");
            return;
        }

        /* 开始传输 */
        try {
            out.println(String.format("150 opening %s data connection", binaryMode ? "BINARY" : "ASCII"));
            Socket dataConn = createDataConnection();

            if (binaryMode)
                binaryDump(dataConn.getInputStream(), fileStream);
            else
                textDump(dataConn.getInputStream(), fileStream);

            dataConn.close();
            out.println("226 transfer complete");
        } catch (IOException e) {
            out.println("425 could not create connection");
        }
    }

    private Socket createDataConnection() throws IOException {
        if (passive) {
            return dataListener.accept();
        } else {
            return new Socket(clientIp, clientPort);
        }
    }

    private void binaryDump(InputStream in, OutputStream out) throws IOException {
        int bufferSize = 1460; // 一个TCP payload的大小
        byte[] buffer = new byte[bufferSize];
        BufferedInputStream src = new BufferedInputStream(in, bufferSize);
        BufferedOutputStream target = new BufferedOutputStream(out, bufferSize);
        int size;
        while ((size = src.read(buffer)) != -1)
            target.write(buffer, 0, size);
        src.close();
        target.close();
    }

    private void textDump(InputStream in, OutputStream out) throws IOException {
        BufferedReader src = new BufferedReader(new InputStreamReader(in));
        PrintWriter target = new PrintWriter(out);
        String line;
        while ((line = src.readLine()) != null)
            target.write(line);
        src.close();
        target.close();
    }
}

