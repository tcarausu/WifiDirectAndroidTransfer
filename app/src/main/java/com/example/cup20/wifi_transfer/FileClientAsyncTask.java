package com.example.cup20.wifi_transfer;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.os.EnvironmentCompat;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class FileClientAsyncTask extends AsyncTask{

    private Context context;
    private String host;
    private String path;
    final String TAG = "P2P_client";

    public FileClientAsyncTask(Context context, String host,String path) {
        this.context = context;
        this.host = host;
        this.path = path;
    }

    @Override
    protected Object doInBackground(Object[] params) {
        int port = 8888;
        int len;
        Socket socket = new Socket();
        byte buf[]  = new byte[1024];

        try {
            /**
             * Create a client socket with the host,
             * port, and timeout information.
             */

            Log.d(TAG, "Starting client. Host: " + host);
            socket.bind(null);
            socket.connect((new InetSocketAddress(host, port)), 500);
            Log.d(TAG, "Connected");
            /**
             * Create a byte stream from a JPEG file and pipe it to the output stream
             * of the socket. This data will be retrieved by the server device.
             */
            OutputStream outputStream = socket.getOutputStream();
            ContentResolver cr = context.getContentResolver();
            InputStream inputStream = null;
            inputStream = cr.openInputStream(Uri.fromFile(new File(path)));
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG,"File not found: "+e.toString());
        } catch (IOException e) {
            Log.d(TAG,"IO: "+e.toString());
        }

        /**
         * Clean up any open sockets when done
         * transferring or if an exception occurred.
         */
        finally {
            if (socket != null) {
                if (socket.isConnected()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        //catch logic
                    }
                }
            }
        }
        return null;
    }
}
