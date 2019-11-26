package edu.ucla.cs.zyuan.multihopwfd;

import android.app.Activity;
import android.util.Log;

import edu.ucla.cs.zyuan.multihopwfd.ServerPacketHandler.AnonymousPacketHandler;
import edu.ucla.cs.zyuan.multihopwfd.ServerPacketHandler.IServerPacketHandler;
import com.google.protobuf.CodedInputStream;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WifiDirectServer extends Thread {
    public int port;
    public static final int maxPacketLength = 1024;

    private static AtomicInteger packetNum = new AtomicInteger((int)(Math.random() * 1000000));
    public static int getPacketNum()
    {
        return packetNum.getAndIncrement();
    }

    private Activity mActivity;
    public Activity getActivity()
    {
        return mActivity;
    }

    private DuplicatePacketDetection duplicatePacketDetection = new DuplicatePacketDetection();

    public interface IOnReceivePacket {
        void onReceivePacket(PacketProtos.WifiDirectPacket packet);
    }

    public interface IOnReceiveAnonymousPacket {
        void onReceiveAnonymPacket(byte[] myShare, byte[] otherShare, byte[] decoded);
    }

    public HashMap<Integer, byte[]> keyPktSet = new HashMap<>(), encryptPktSet = new HashMap<>();
    public AtomicInteger currentRole = new AtomicInteger(AnonymousPacketHandler.ROLE_NORMAL);

    WifiDirectServer(Activity a, int port)
    {
        this.mActivity = a;
        this.port = port;
    }

    private IOnReceivePacket onReceivePackethandler = null;
    public void setOnReceivePacketHandler(IOnReceivePacket handler) {
        onReceivePackethandler = handler;
    }

    public IOnReceiveAnonymousPacket onReceiveAnonymPackethandler = null;
    public void setOnReceiveAnonymPackethandler(IOnReceiveAnonymousPacket handler) {
        onReceiveAnonymPackethandler = handler;
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        HashMap<PacketProtos.WifiDirectPacket.Type, IServerPacketHandler> handlers = new HashMap<>();
        AnonymousPacketHandler anonymousPacketHandler = new AnonymousPacketHandler();
        handlers.put(PacketProtos.WifiDirectPacket.Type.ANONYMITY_KEY, anonymousPacketHandler);
        handlers.put(PacketProtos.WifiDirectPacket.Type.ANONYMITY_ENCRYPT, anonymousPacketHandler);
        handlers.put(PacketProtos.WifiDirectPacket.Type.ANONYMITY_REPORT, anonymousPacketHandler);

        try {
            socket = new DatagramSocket(port);
            Log.d(WiFiDirectActivity.TAG, "UDPServer: Socket opened");
            byte[] buf = new byte[maxPacketLength];
            while (true) {
                DatagramPacket raw_packet = new DatagramPacket(buf, buf.length);
                socket.receive(raw_packet);
                CodedInputStream inputStream = CodedInputStream.newInstance(raw_packet.getData(), 0, raw_packet.getLength());
                PacketProtos.WifiDirectPacket pkt = PacketProtos.WifiDirectPacket.parseFrom(inputStream);
                DuplicatePacketDetection.PacketLabel pl = new DuplicatePacketDetection.PacketLabel(pkt.getMacAddress(), pkt.getPacketNum());
                if (!duplicatePacketDetection.duplicate(pl)) {
                    duplicatePacketDetection.add(pl);

                    IServerPacketHandler handler = handlers.get(pkt.getType());
                    if (handler != null) {
                        handler.onReceive(this, pkt);
                    }

                    if (onReceivePackethandler != null) {
                        onReceivePackethandler.onReceivePacket(pkt);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(WiFiDirectActivity.TAG, e.getMessage());
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
