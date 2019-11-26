// Copyright 2011 Google Inc. All Rights Reserved.

package edu.ucla.cs.zyuan.multihopwfd;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "edu.ucla.cs.zyuan.multihopwfd.SEND_FILE";
    public static final String ACTION_BROADCAST_HELLO = "edu.ucla.cs.zyuan.multihopwfd.BROADCAST_HELLO";
    public static final String ACTION_UNICAST_HELLO = "edu.ucla.cs.zyuan.multihopwfd.UNICAST_HELLO";
    public static final String ACTION_UNICAST_IPV6_HELLO = "edu.ucla.cs.zyuan.multihopwfd.UNICAST_IPV6_HELLO";
    public static final String ACTION_BROADCAST_IPV6_HELLO = "edu.ucla.cs.zyuan.multihopwfd.BROADCAST_IPV6_HELLO";
    public static final String ACTION_BROADCAST_IPV6_P2P = "edu.ucla.cs.zyuan.multihopwfd.ACTION_BROADCAST_IPV6_P2P";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";
    public static final String EXTRAS_PACKET_CONTENT = "packet_content";

    public FileTransferService(String name) {
        super(name);
    }

    public FileTransferService() {
        super("FileTransferService");
    }

    /*
     * (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            String fileUri = intent.getExtras().getString(EXTRAS_FILE_PATH);
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            Socket socket = new Socket();
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);
            try {
                Log.d(WiFiDirectActivity.TAG, "Opening client socket - ");
                /*
                ArrayList<String> localHostAddress = new ArrayList<String>() ;
                List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface intf : interfaces) {
                    List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                    for (InetAddress addr : addrs) {
                        if (!addr.isLoopbackAddress()) {
                            String sAddr = addr.getHostAddress();
                            if (sAddr.indexOf("192.168.49") >= 0)
                                localHostAddress.add(sAddr);
                        }
                    }
                }

                for (String addr : localHostAddress)
                {
                    Log.d("MultihopWFD", addr);
                }
                InetSocketAddress hostSocketAddr;


                {
                    hostSocketAddr = new InetSocketAddress(localHostAddress.get(1), 1778);
                    Log.d("MultihopWFD", localHostAddress.get(1));
                }
                socket.bind(hostSocketAddr);
                */

                socket.bind(null);
                Log.d("MultihopWFD", socket.getLocalAddress().toString() + " " + socket.getLocalPort() + " to " + host.toString() + port);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                Log.d(WiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());
                OutputStream stream = socket.getOutputStream();
                ContentResolver cr = context.getContentResolver();
                InputStream is = null;
                try {
                    is = cr.openInputStream(Uri.parse(fileUri));
                } catch (FileNotFoundException e) {
                    Log.d(WiFiDirectActivity.TAG, e.toString());
                }
                DeviceDetailFragment.copyFile(is, stream);
                Log.d(WiFiDirectActivity.TAG, "Client: Data written");
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Give up
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        else if (intent.getAction().equals(ACTION_BROADCAST_HELLO))
        {
            DatagramSocket socket = null;
            try {
                int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);
                socket = new DatagramSocket(port);
                /*
                WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                DhcpInfo dhcp = wifi.getDhcpInfo();
                // handle null somehow

                int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
                byte[] quads = new byte[4];
                for (int k = 0; k < 4; k++)
                    quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
                InetAddress ipAddr = InetAddress.getByAddress(quads);*/
                InetAddress ipAddr = InetAddress.getByName("192.168.49.255");
                //Log.d("MultihopWFD", ipAddr.toString());
                String hello = "udp:hello";
                for (int i = 0; i < 20; ++i) {
                    String numhello = hello + i;
                    DatagramPacket packet = new DatagramPacket(numhello.getBytes(), numhello.length(),
                            ipAddr, port + 1);
                    socket.send(packet);
                }
            }catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
        }

        else if (intent.getAction().equals((ACTION_UNICAST_HELLO)))
        {
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);

            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);
            try {

                String hello = "TCPHello";
                for (int i = 0; i < 20; i++)
                {
                    Socket socket = new Socket();
                    socket.bind(null);
                    socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);
                    OutputStream stream = socket.getOutputStream();
                    String numhello = hello + i + " ";
                    stream.write(numhello.getBytes());
                    socket.close();
                }
                Log.d(WiFiDirectActivity.TAG, "Client: Data written");
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
            }
        }

        else if (intent.getAction().equals(ACTION_UNICAST_IPV6_HELLO))
        {
            String hello = "TCPV6Hello";
            try {
                NetworkInterface nif = WiFiDirectActivity.getNetworkInterface(WiFiDirectActivity.NetInterfaceWlanP2P);

                //NetworkInterface nif = NetworkInterface.getByInetAddress(InetAddress.getByName("192.168.49.187"));
                Inet6Address ip = Inet6Address.getByAddress("", InetAddress.getByName("fe80::8455:a5ff:fe98:503c").getAddress(), nif);
                //Inet6Address ip = Inet6Address.getByAddress("", InetAddress.getByName("fe80::784b:87ff:fe6d:a126").getAddress(), nif);

                //Inet6Address ip = Inet6Address.getByAddress("", buffer, nif);
                Socket socket = new Socket(ip, intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT));
                OutputStream stream = socket.getOutputStream();
                stream.write(hello.getBytes());
                socket.close();
            }catch (Exception e){
                Log.d(WiFiDirectActivity.TAG, e.getMessage());
            }

        }

        else if (intent.getAction().equals(ACTION_BROADCAST_IPV6_HELLO))
        {
            String hello = "UDPV6Hello";
            byte[] hello_byte = hello.getBytes();

            try {
                DatagramSocket sock = new DatagramSocket();
                DatagramPacket pkt = new DatagramPacket(hello_byte, hello_byte.length);
                //NetworkInterface nif = NetworkInterface.getByInetAddress(InetAddress.getByName("192.168.49.187"));
                NetworkInterface nif = NetworkInterface.getByInetAddress(InetAddress.getByName("192.168.49.188"));

                //pkt.setAddress(InetAddress.getByName("FF7E:230::1237"));
                //pkt.setPort(8984);

                Inet6Address ipv6 =Inet6Address.getByAddress("",InetAddress.getByName("ff02::1").getAddress(), nif);
                pkt.setAddress(ipv6);
                pkt.setPort(8987);
                sock.send(pkt);
                sock.close();
            }catch (Exception e){
                Log.d(WiFiDirectActivity.TAG, e.getMessage());
            }
        }

        else if (intent.getAction().equals(ACTION_BROADCAST_IPV6_P2P))
        {
            byte[] pkt = intent.getByteArrayExtra(EXTRAS_PACKET_CONTENT);
            try {
                DatagramSocket sock = new DatagramSocket();
                DatagramPacket dpkt = new DatagramPacket(pkt, pkt.length);
                NetworkInterface nif = WiFiDirectActivity.getNetworkInterface(WiFiDirectActivity.NetInterfaceWlanP2P);

                Inet6Address ipv6 =Inet6Address.getByAddress("",InetAddress.getByName("ff02::1").getAddress(), nif);
                dpkt.setAddress(ipv6);
                dpkt.setPort(WiFiDirectActivity.wifiDirectServer_port);
                sock.send(dpkt);
                sock.close();
            }catch (Exception e){
                Log.d(WiFiDirectActivity.TAG, e.getMessage());
            }
        }

    }


}
