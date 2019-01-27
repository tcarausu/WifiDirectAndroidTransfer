package com.example.cup20.wifi_transfer;

import android.Manifest;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String ROOT_PATH = "/sdcard";
    private ArrayList<String> names = null;
    private ArrayList<String> paths = null;
    private ListView listView;
    private TextView pathText;
    private String currentPath;
    private Dialog sendFileDialog;
    private Dialog chosenDialog;
    private Dialog sureDialog;
    private Dialog waitForSearchPeers;
    private Dialog waiting;
    private Button disConnect;
    private Button connect;
    private boolean isSender;
    private String mac;
    private WifiManager wifiManager = null;
    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;
    private WifiP2pDeviceList deviceList;
    private TextView textView;
    private String groupOwnerAddress = "";
    final String TAG = "WIFI_P2P";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        initialization();
        wifiManager.setWifiEnabled(true);
        chosenDialog.show();
        showFileDir(ROOT_PATH);
        setClickFunction();
    }

    private void setClickFunction() {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                currentPath = paths.get(position);
                File file = new File(currentPath);
                if (file.exists() && file.canRead()) {
                    if (file.isDirectory()) {
                        showFileDir(currentPath);
                    } else {
                        Toast.makeText(getApplicationContext(), file.getPath(),
                                Toast.LENGTH_SHORT).show();
                        sendFileDialog.show();
                    }
                }
                pathText.setText(currentPath);
            }
        });
        disConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wifiManager.setWifiEnabled(false);
                textView.setText("disconnected");
            }
        });
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>(deviceList.getDeviceList());
                p2pConnectToPeer(peers.get(0));
            }
        });
    }

    private void initialization() {
        isSender = false;
        wifiManager = (WifiManager) super.getSystemService(Context.WIFI_SERVICE);
        listView = (ListView) findViewById(R.id.file);
        pathText = (TextView) findViewById(R.id.path);
        disConnect = (Button) findViewById(R.id.disconnect);
        connect = (Button) findViewById(R.id.connect);
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        textView = (TextView) findViewById(R.id.status);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        sendFileDialog = new AlertDialog.Builder(this).
                setMessage("Send the File?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new FileClientAsyncTask(MainActivity.this, groupOwnerAddress, currentPath).execute();
            }
        }).setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }).setCancelable(false).
                create();
        chosenDialog = new AlertDialog.Builder(this).
                setMessage("Choose which side").setPositiveButton("Sender", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                isSender = true;
                disConnect.setVisibility(View.INVISIBLE);
                dialog.dismiss();
                sureDialog.show();
            }
        }).setNegativeButton("Receiver", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                isSender = false;
                connect.setVisibility(View.INVISIBLE);
                dialog.dismiss();
                sureDialog.show();
                findViewById(R.id.file).setVisibility(View.GONE);
            }
        }).setCancelable(false).
                create();
        sureDialog = new AlertDialog.Builder(this).
                setMessage("Make sure that the app is called after nfc tag is touched by you phone" + "\n" + "Press 'YES' to continue ").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (isSender == false) {
                    writeMACIntoTag();
                } else {
                    readMACFromTAG(getIntent());
                }
                dialog.dismiss();
                waitForSearchPeers.show();
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                sureDialog.show();
            }
        }).setCancelable(false).
                create();
        waiting = new AlertDialog.Builder(this).setMessage("Waiting").setCancelable(false).create();
        waitForSearchPeers = new AlertDialog.Builder(this).setMessage("To scan all peers?").setCancelable(false).setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                waiting.show();
                waitForSearchPeers();
            }
        }).create();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }

    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    private void waitForSearchPeers() {
        p2pDiscoverPeers();
        new Handler().postDelayed(new Runnable() {
            public void run() {
                waiting.dismiss();
                if (isSender == false)
                    new FileServerAsyncTask(MainActivity.this, pathText).execute();
                Toast.makeText(getApplicationContext(), "Peers discover completed and start try to connect the specific peer",
                        Toast.LENGTH_SHORT).show();
            }
        }, 6000);
    }

    private void writeMACIntoTag() {
        Tag detectedTag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
        try {
            write(getMac(), detectedTag);
            Toast.makeText(getApplicationContext(), "your mac: " + getMac() + " has been written",
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        } catch (FormatException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void readMACFromTAG(Intent intent) {

        NdefMessage[] msgs;
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
                NdefRecord[] records = msgs[0].getRecords();
                for (NdefRecord ndefRecord : records) {
                    if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                        byte[] payload = ndefRecord.getPayload();
                        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
                        int languageCodeLength = payload[0] & 0063;
                        try {
                            mac = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
                            Toast.makeText(getApplicationContext(), mac + "received",
                                    Toast.LENGTH_SHORT).show();
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                            Toast.makeText(getApplicationContext(), e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }
    }

    private void write(String text, Tag tag) throws IOException, FormatException {
        NdefRecord[] records = {createRecord(text)};
        NdefMessage message = new NdefMessage(records);
        Ndef ndef = Ndef.get(tag);
        ndef.connect();
        ndef.writeNdefMessage(message);
        ndef.close();
    }

    private NdefRecord createRecord(String text) throws UnsupportedEncodingException {
        String lang = "en";
        byte[] textBytes = text.getBytes();
        byte[] langBytes = lang.getBytes("US-ASCII");
        int langLength = langBytes.length;
        int textLength = textBytes.length;
        byte[] payload = new byte[1 + langLength + textLength];
        payload[0] = (byte) langLength;

        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT,
                new byte[0], payload);
        return recordNFC;
    }

    private WifiP2pManager.ConnectionInfoListener listenerConnectionInfoAvailable = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            InetAddress groupOwnerAddress = info.groupOwnerAddress;
            MainActivity.this.groupOwnerAddress = groupOwnerAddress.getHostAddress();
            if (info.groupFormed == true) {
                textView.setText("Connected");
            } else {
                textView.setText("Wrong");
            }
            Log.d(TAG, info.toString());// + "Server IP Address " + MainActivity.this.groupOwnerAddress);
        }
    };

    private WifiP2pManager.PeerListListener myPeerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peers) {
            deviceList = peers;
            for (WifiP2pDevice d : deviceList.getDeviceList()) {
                Log.d(TAG, "Found: " + d.deviceName + " " + d.deviceAddress + "\n");
            }
        }
    };

    public void p2pDiscoverPeers() {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Listener: Discovering peers was success");
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Listener: Discovering peers was not success: " + reasonCode);
            }
        });
    }

    public void p2pConnectToPeer(WifiP2pDevice device) {
        //obtain a peer from the WifiP2pDeviceList
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = mac;
        config.groupOwnerIntent = 0;
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Listener: Connecting to peer was success");
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Listener: Connecting to peer was not success: " + reasonCode);
            }
        });
    }

    /**
     * A BroadcastReceiver that notifies of important Wi-Fi p2p events.
     */
    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

        private WifiP2pManager mManager;
        private WifiP2pManager.Channel mChannel;
        private MainActivity mActivity;

        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                           MainActivity activity) {
            super();
            this.mManager = manager;
            this.mChannel = channel;
            this.mActivity = activity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    // Wifi P2P is enabled
                    Log.d(TAG, "WIFI P2P enabled");
                } else {
                    // Wi-Fi P2P is not enabled
                    Log.d(TAG, "WIFI P2P not enabled");
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // Call WifiP2pManager.requestPeers() to get a list of current peers
                // request available peers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on PeerListListener.onPeersAvailable()
                if (mManager != null) {
                    mManager.requestPeers(mChannel, myPeerListListener);
                }

            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Respond to new connection or disconnections
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()) {
                    Log.d(TAG, "Connection connected");
                    mManager.requestConnectionInfo(mChannel, listenerConnectionInfoAvailable);
                } else {
                    Log.d(TAG, "Connection disconnected");
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
            }
        }
    }

    private void showFileDir(String path) {
        names = new ArrayList<String>();
        paths = new ArrayList<String>();
        File file = new File(path);
        File[] files = file.listFiles();

        if (!ROOT_PATH.equals(path)) {
            names.add("@1");
            paths.add(ROOT_PATH);

            names.add("@2");
            paths.add(file.getParent());
        }
        for (File f : files) {
            names.add(f.getName());
            paths.add(f.getPath());
        }
        listView.setAdapter(new FileAdapter(this, names, paths));
    }

    private String getMac() {
        String macSerial = null;
        String str = "";

        try {
            Process pp = Runtime.getRuntime().exec("cat /sys/class/net/wlan0/address ");
            InputStreamReader ir = new InputStreamReader(pp.getInputStream());
            LineNumberReader input = new LineNumberReader(ir);

            for (; null != str; ) {
                str = input.readLine();
                if (str != null) {
                    macSerial = str.trim();
                    break;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        String[] macSplit = macSerial.trim().split(":");
        char[] wrongMac = macSplit[0].trim().toCharArray();
        wrongMac[1] = (char) (wrongMac[1] + 2);
        macSplit[0] = String.valueOf(wrongMac);
        macSerial = macSplit[0] + ":" + macSplit[1] + ":" + macSplit[2] + ":" + macSplit[3] + ":" + macSplit[4] + ":" + macSplit[5];
        return macSerial;
    }
}
