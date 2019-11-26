package edu.ucla.cs.zyuan.multihopwfd.ServerPacketHandler;

import edu.ucla.cs.zyuan.multihopwfd.PacketProtos;
import edu.ucla.cs.zyuan.multihopwfd.WifiDirectServer;


public interface IServerPacketHandler {
    // return value means needs more operation
    boolean onReceive(WifiDirectServer server, PacketProtos.WifiDirectPacket packet);
}
