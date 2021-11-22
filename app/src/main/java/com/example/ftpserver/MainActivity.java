package com.example.ftpserver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.example.ftpserver.ftp.Control;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {
    private static final int GET_ROOT_DIR = 4;
    private TextView rootDirText;
    private TextView hostText, portText;
    private Button setRootDirButton;
    private Button startButton;
    private Button closeServerButton;
    private String ipAddress;

    private Context context;

    private DocumentFile rootDir = null;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.context = MainActivity.this;

        this.rootDirText = findViewById(R.id.root_dir);
        this.setRootDirButton = findViewById(R.id.set_root_dir);
        this.startButton = findViewById(R.id.start_server);
        this.closeServerButton = findViewById(R.id.close_server);
        this.hostText = findViewById(R.id.host);
        this.portText = findViewById(R.id.port);

        ipAddress = getLocalIpAddress();
        hostText.setText(ipAddress);
        portText.setText(Integer.toString(Control.controlPort));

        if (!Patterns.IP_ADDRESS.matcher(ipAddress).matches()) {
            showAlert(R.string.alert_title, R.string.ip_unavailable);
            startButton.setEnabled(false);
            hostText.setText("N/A");
            portText.setText("N/A");
        }

    }

    private void showAlert(Integer title, Integer message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        AlertDialog alert = builder.setIcon(R.mipmap.ic_launcher_round)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                })
                .create();
        alert.show();
    }

    public void setRootDir(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, GET_ROOT_DIR);
    }

    public void closeServer(View view) {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GET_ROOT_DIR)
            handleDirectorySelection(data, resultCode);
    }

    private void handleDirectorySelection(Intent resultData, int resultCode) {
        if (resultCode != RESULT_OK || resultData == null) {
            showAlert(R.string.alert_title, R.string.alert_dir_not_exist);
            return;
        }

        Uri uri = resultData.getData();
        rootDir = DocumentFile.fromTreeUri(this, uri);
        if (rootDir == null) {
            showAlert(R.string.alert_title, R.string.alert_dir_not_exist);
            return;
        }
        rootDirText.setText(rootDir.getName());
    }

    public void startServer(View view) {
        if (rootDir == null) {
            showAlert(R.string.alert_title, R.string.alert_root_not_set);
            return;
        }

        Control control = new Control();
        control.mainActivity = this;
        control.serverIP = ipAddress;
        control.start();

        setRootDirButton.setEnabled(false);
        startButton.setEnabled(false);
        closeServerButton.setEnabled(true);
        startButton.setText(R.string.server_running);
    }

    public InputStream getInputStream(String fileName) throws FileNotFoundException {
        DocumentFile file = rootDir.findFile(fileName);
        if (file == null || !file.canRead()) {
            return null;
        }

        return getContentResolver().openInputStream(file.getUri());
    }

    public OutputStream createFile(String filename) throws FileNotFoundException {
        if (rootDir.findFile(filename) != null)
            return null;

        DocumentFile file = rootDir.createFile("*/*", filename);
        if (file == null) return null;
        return getContentResolver().openOutputStream(file.getUri());
    }

    public String getLocalIpAddress() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            @SuppressLint("MissingPermission") WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return ipAddressToString(wifiInfo.getIpAddress());
        }
        return "";
    }

    public static String ipAddressToString(int ipAddress) {

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray)
                    .getHostAddress();
        } catch (UnknownHostException ex) {
            ipAddressString = "NaN";
        }

        return ipAddressString;
    }


}