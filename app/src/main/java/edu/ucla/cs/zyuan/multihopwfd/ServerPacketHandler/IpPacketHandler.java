package edu.ucla.cs.zyuan.multihopwfd.ServerPacketHandler;

import edu.ucla.cs.zyuan.multihopwfd.PacketProtos;
import edu.ucla.cs.zyuan.multihopwfd.WifiDirectServer;

public class IpPacketHandler implements IServerPacketHandler {
    @Override
    public boolean onReceive(WifiDirectServer server, PacketProtos.WifiDirectPacket packet) {
        if (packet.getType() == PacketProtos.WifiDirectPacket.Type.REQUEST_IP)
        {

        }
        else if (packet.getType() == PacketProtos.WifiDirectPacket.Type.ANSWER_IP)
        {

        }
        return false;
    }
}
