package edu.ucla.cs.zyuan.multihopwfd;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NetworkUtility {
    public static final String NetInterfaceWlanP2P = "p2p-wlan";
    public static final String NetInterfaceWlan = "wlan";

    private static final String NetInterfaceWlanP2P_LgTribute = "p2p-p2p0";

    private static NetworkInterface wlanp2p = null, wlan = null;

    public static NetworkInterface getNetworkInterface(String sni)
    {
        if (wlanp2p !=null && sni.equals(NetInterfaceWlanP2P))
        {
            return wlanp2p;
        }
        if (wlan != null && sni.equals(NetInterfaceWlan))
        {
            return wlan;
        }
        NetworkInterface nif = null;
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.getName().indexOf(sni) == 0) {
                    nif = intf;
                    break;
                }
            }
            //try a new possibility
            if (nif == null && !sni.equals(NetInterfaceWlanP2P_LgTribute))
            {
                nif = getNetworkInterface(NetInterfaceWlanP2P_LgTribute);
            }

        } catch (Exception e) {
            return null;
        }
        if (nif != null)
        {
            if ( sni.equals(NetInterfaceWlanP2P))
            {
                wlanp2p = nif;
            }
            if (sni.equals(NetInterfaceWlan))
            {
                wlan = nif;
            }
        }
        return nif;
    }

    public static Inet6Address getIPV6Address(String sni)
    {
        NetworkInterface nif = NetworkUtility.getNetworkInterface(NetworkUtility.NetInterfaceWlanP2P);
        return getIPV6Address(nif);
    }

    public static Inet6Address getIPV6Address(NetworkInterface nif)
    {
        if (nif == null)
        {
            return null;
        }
        List<InetAddress> addrs = Collections.list(nif.getInetAddresses());
        for (InetAddress addr : addrs)
        {
            if (addr instanceof Inet6Address)
            {
                return (Inet6Address)addr;
            }
        }
        return null;
    }

    public static long getMacAddress()
    {
        NetworkInterface nif = WiFiDirectActivity.getWlanP2PInterface();
        if (nif == null)
        {
            return 0;
        }
        long macAddr = 0 ;
        try {
            byte[] macAddrBytes = nif.getHardwareAddress();
            macAddr = NetworkUtility.bytesToLong(macAddrBytes);
        } catch (SocketException e) {
        }
        return macAddr;
    }



    public static void broadcasrUDP(NetworkInterface nif, int port, byte[] con)
    {
        try {
            sendUDP(nif, port, con, InetAddress.getByName("ff02::1").getAddress());
        }catch (UnknownHostException e)
        {
            Log.d(WiFiDirectActivity.TAG, e.toString());
        }
    }
    private static List<IpAndInetAddr> ipInet = new ArrayList<IpAndInetAddr>();

    public static void sendUDP(NetworkInterface nif, int port, byte[] con, byte[] ip)
    {
        sendUDP(nif,port,con,ip,null);
    }

    public static void sendUDP(NetworkInterface nif, int port, byte[] con, byte[] ip, DatagramSocket sock)
    {
        boolean closeSock = false;
        try {
            if (sock == null) {
                closeSock = true;
                sock = new DatagramSocket();
            }
            DatagramPacket dpkt = new DatagramPacket(con, con.length);
            if (ip.length == 16) {
                if (nif == null) {
                    Log.d(WiFiDirectActivity.TAG, "NULL Interface");
                    return;
                }
            }
            InetAddress inet = null;
            for (int i=0;i<ipInet.size();++i)
            {
                if (Arrays.equals(ipInet.get(i).ip, ip) && (nif == ipInet.get(i).nif))
                {
                    //Log.d(WiFiDirectActivity.TAG, NetworkUtility.bytesToHex(ipInet.get(i).ip));
                    //Log.d(WiFiDirectActivity.TAG, "MATCH");
                    inet = ipInet.get(i).inet;
                }
            }
            if (inet == null) {
                if (ip.length == 16) {
                    inet = Inet6Address.getByAddress("", ip, nif);
                }
                else {
                    inet = InetAddress.getByAddress(ip);
                }
                IpAndInetAddr ipAndInetAddr = new IpAndInetAddr();
                ipAndInetAddr.ip = ip;
                ipAndInetAddr.nif = nif;
                ipAndInetAddr.inet = inet;
                ipInet.add(ipAndInetAddr);
            }
            dpkt.setAddress(inet);
            dpkt.setPort(port);
            sock.send(dpkt);

        }
        catch (UnknownHostException e)
        {
        }
        catch (IOException e)
        {
            Log.e(WiFiDirectActivity.TAG, e.toString() + "NETWORKUTILITY" );
        }
        finally {
            if (sock != null && closeSock)
               sock.close();
        }
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    public static long bytesToLong(byte[] by)
    {
        long value = 0;
        for (int i = 0; i < by.length; i++)
        {
            value += ((long) by[i] & 0xffL) << (8 * i);
        }
        return value;
    }
}
