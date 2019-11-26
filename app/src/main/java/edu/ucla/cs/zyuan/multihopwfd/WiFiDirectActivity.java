package edu.ucla.cs.zyuan.multihopwfd;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import edu.ucla.cs.zyuan.multihopwfd.ServerPacketHandler.AnonymousPacketHandler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WiFiDirectActivity extends AppCompatActivity implements WifiP2pManager.ChannelListener, DeviceListFragment.DeviceActionListener {

    public static final String TAG = "wifidirectdemo";

    public static final int wifiDirectServer_port = 9341;
    public static final String NetInterfaceWlanP2P = "p2p-wlan", NetInterfaceWlanP2P_LgTribute = "p2p-p2p0";
    public static final String NetInterfaceWlan = "wlan";

    private WifiP2pManager manager;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;

    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;

    private Handler mHandler;
    private WifiDirectServer wifiDirectServer;

    public ArrayList<String>  anonymityBuffer = new ArrayList<>();
    private String mLatestAnonymDecodeResult = null;


    //for test
    public static final int I_PERF_RECEIVER_PORT = 9243;

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;

        mHandler = new Handler(){

            @Override
            public void handleMessage(Message msg) {
                String str = (String)msg.obj;
                DebugPanel dp = (DebugPanel) findViewById(R.id.debug_panel);
                dp.appendNewLine(str);
            }
        };
    }

    public void changeRole(int r) {
        wifiDirectServer.currentRole.set(r);
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // add necessary intent values to be matched.

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        DebugPanel dp = (DebugPanel)findViewById(R.id.debug_panel);
        dp.appendNewLine("Welcome To DebugPanel");

        //DebugPanel dp = (DebugPanel)mContentView.findViewById(R.id.debug_panel);

        wifiDirectServer = new WifiDirectServer(this, wifiDirectServer_port);

        wifiDirectServer.setOnReceivePacketHandler(new WifiDirectServer.IOnReceivePacket() {
            @Override
            public void onReceivePacket(PacketProtos.WifiDirectPacket packet) {
                //WifiDirectProtocol.Header header = new WifiDirectProtocol.Header();
                //byte[] con = (byte[])WifiDirectProtocol.parsePacket(new ByteArrayInputStream(pkt), header);
                if (packet.getType() == PacketProtos.WifiDirectPacket.Type.ANONYMITY_KEY) {
                    anonymityBuffer.add(new String(packet.getContent().toByteArray()));
                } else if (packet.getType() == PacketProtos.WifiDirectPacket.Type.ANONYMITY_ENCRYPT) {
                    anonymityBuffer.add(new String(packet.getContent().toByteArray()));
                } else if (packet.getType() == PacketProtos.WifiDirectPacket.Type.DATA) {
                    addStringToDP("p2p received! " + new String(packet.getContent().toByteArray()));
                }
                //Log.d(WiFiDirectActivity.TAG, "P2P packet length:" + pkt.length);

                NetworkInterface nif = getNetworkInterface(NetInterfaceWlan);
                broadcasrUDP(nif, wifiDirectServer_port, packet.getContent().toByteArray());
                nif = getWlanP2PInterface();
                broadcasrUDP(nif, wifiDirectServer_port, packet.getContent().toByteArray());
            }

        });

        wifiDirectServer.setOnReceiveAnonymPackethandler(new WifiDirectServer.IOnReceiveAnonymousPacket() {
            @Override
            public void onReceiveAnonymPacket(byte[] myShare, byte[] otherShare, byte[] decoded) {
                StringBuilder sb = new StringBuilder();
                sb.append("Secret Share 1: \n");
                sb.append(bytesToHex(myShare));
                sb.append("\nSecret Share 2: \n");
                sb.append(bytesToHex(otherShare));
                sb.append("\nDecrypted msgs: \n");
                sb.append("[");
                boolean first = true;
                for (int i = 0; i < AnonymousPacketHandler.ANONYMITY_DATA_NUM_SLOT; i++) {
                    if (first) {
                        sb.append("\"");
                        first = false;
                    } else {
                        sb.append(", \"");
                    }
                    for (int j = 0; j < AnonymousPacketHandler.ANONYMITY_DATA_MAX_LEN; j++) {
                        byte b = decoded[i * AnonymousPacketHandler.ANONYMITY_DATA_MAX_LEN + j];
                        if (b == 0) {
                            break;
                        }
                        sb.append((char) b);
                    }
                    sb.append("\"");
                }
                sb.append("]");
                mLatestAnonymDecodeResult = sb.toString();
            }
        });

        findViewById(R.id.btn_test2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = (mLatestAnonymDecodeResult != null? mLatestAnonymDecodeResult: "No decoded msgs");
                new AlertDialog.Builder(WiFiDirectActivity.this)
                        .setTitle("Decrypted Anonymous Msgs")
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // nothing
                            }
                        })
                        .show();
            }
        });

        wifiDirectServer.start();
    }

    @Override
    protected void onDestroy() {
        wifiDirectServer.interrupt();
        super.onDestroy();
    }

    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_group_owner:
                if (manager != null && channel != null) {
                    manager.createGroup(channel, new ActionListener() {
                        @Override
                        public void onSuccess() {
                            //to do
                        }

                        @Override
                        public void onFailure(int reason) {
                            Toast.makeText(WiFiDirectActivity.this, "Creating Owner Fails",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                return true;

            case R.id.atn_change_role:
                ChangeRoleDialog dialog = new ChangeRoleDialog();
                Bundle args = new Bundle();
                args.putInt("currentRole", wifiDirectServer.currentRole.get());
                dialog.setArguments(args);
                dialog.show(WiFiDirectActivity.this.getFragmentManager(), TAG);
                return true;

            case R.id.atn_direct_enable:
                if (manager != null && channel != null) {

                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.

                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                } else {
                    Log.e(TAG, "channel or manager is null");
                }
                return true;

            case R.id.atn_direct_discover:
                if (!isWifiP2pEnabled) {
                    Toast.makeText(WiFiDirectActivity.this, R.string.p2p_off_warning,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                        .findFragmentById(R.id.frag_list);
                fragment.onInitiateDiscovery();
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Initiated",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Failed : " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void showDetails(WifiP2pDevice device) {
        DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);

    }

    @Override
    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(WiFiDirectActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.resetViews();
        manager.removeGroup(channel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {
                fragment.getView().setVisibility(View.GONE);
            }

        });
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            resetData();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                manager.cancelConnect(channel, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

    }

    public void addStringToDP(String str)
    {
        Message msg = mHandler.obtainMessage(0, str);
        msg.sendToTarget();
    }

    public static NetworkInterface getWlanP2PInterface() {
        NetworkInterface nif = WiFiDirectActivity.getNetworkInterface(WiFiDirectActivity.NetInterfaceWlanP2P);
        if (nif == null) {
            nif = WiFiDirectActivity.getNetworkInterface(WiFiDirectActivity.NetInterfaceWlanP2P_LgTribute);
        }
        return nif;
    }

    public static NetworkInterface getNetworkInterface(String sni)
    {
        NetworkInterface nif = null;
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.getName().indexOf(sni) == 0) {
                    nif = intf;
                    break;
                }
            }
            //nif = NetworkInterface.getByName("p2p-wlan0-0");
        } catch (Exception e) {
            return null;
        }
        return nif;
    }

    public static void broadcasrUDP(NetworkInterface nif, int port, byte[] con)
    {
        try {
            if (nif == null)
            {
                Log.d(WiFiDirectActivity.TAG, "NULL Interface");
                return ;
            }
            //nif = NetworkInterface.getByName("wlan0");
            DatagramSocket sock = new DatagramSocket();
            DatagramPacket dpkt = new DatagramPacket(con, con.length);

            Inet6Address ipv6 =Inet6Address.getByAddress("",InetAddress.getByName("ff02::1").getAddress(), nif);
            dpkt.setAddress(ipv6);
            dpkt.setPort(port);
            sock.send(dpkt);
            sock.close();
        }
        catch (UnknownHostException e)
        {
        }
        catch (IOException e)
        {
            Log.e(WiFiDirectActivity.TAG, e.toString());
        }
    }
}
