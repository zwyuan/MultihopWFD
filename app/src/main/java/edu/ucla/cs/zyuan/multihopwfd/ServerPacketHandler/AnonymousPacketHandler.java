package edu.ucla.cs.zyuan.multihopwfd.ServerPacketHandler;

import android.content.Intent;
import android.util.Log;

import edu.ucla.cs.zyuan.multihopwfd.FileTransferService;
import edu.ucla.cs.zyuan.multihopwfd.NetworkUtility;
import edu.ucla.cs.zyuan.multihopwfd.PacketProtos;
import edu.ucla.cs.zyuan.multihopwfd.WiFiDirectActivity;
import edu.ucla.cs.zyuan.multihopwfd.WifiDirectServer;
import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

public class AnonymousPacketHandler implements IServerPacketHandler {

    public static class AnonymReport {
        public int type;
        int[] seqNums;
        byte[] x;
        public AnonymReport() { }
    }

    // For TYPE_ANONYMITY_REPORT packets
    public static final int ANONYMITY_REPORT_TYPE_KEY = 1;
    public static final int ANONYMITY_REPORT_TYPE_ENCRYPT = 2;

    public static final int ROLE_NORMAL = 0;
    public static final int ROLE_ANONYM_SERVER_1 = 1;
    public static final int ROLE_ANONYM_SERVER_2 = 2;
    public static final int ROLE_TEST = 3;

    public static final int ANONYMITY_DATA_MAX_LEN = 4;
    public static final int ANONYMITY_DATA_NUM_SLOT = 8;
    public static final int ANONYMITY_DATA_SIZE = ANONYMITY_DATA_MAX_LEN * ANONYMITY_DATA_NUM_SLOT;

    public static ArrayList<PacketProtos.WifiDirectPacket.Builder> getAnonymityPackets(long macAddr, byte[] content, int anonymSeq)
    {
        Random generator = new Random(System.currentTimeMillis());
        byte[] key = new byte[ANONYMITY_DATA_SIZE];
        byte[] encrypt = new byte[ANONYMITY_DATA_SIZE];
        byte[] xorResult = new byte[ANONYMITY_DATA_SIZE];

        // Generate key
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) generator.nextInt(256);
        }
        // Generate slotted msg
        int slot = generator.nextInt(ANONYMITY_DATA_NUM_SLOT);
        Log.d(WiFiDirectActivity.TAG, "Choose slot " + String.valueOf(slot));
        Arrays.fill(xorResult, (byte) 0);
        for (int i = 0; i < ANONYMITY_DATA_MAX_LEN && i < content.length; i++) {
            xorResult[slot * ANONYMITY_DATA_MAX_LEN + i] = (byte) content[i];
        }
        // Encrypt msg using XOR
        for (int i = 0; i < encrypt.length; i++) {
            encrypt[i] = (byte) (key[i] ^ xorResult[i]);
        }
        Log.d(WiFiDirectActivity.TAG, WiFiDirectActivity.bytesToHex(key));
        Log.d(WiFiDirectActivity.TAG, WiFiDirectActivity.bytesToHex(encrypt));
        Log.d(WiFiDirectActivity.TAG, WiFiDirectActivity.bytesToHex(xorResult));

        // Generate key and encrypt packets
        PacketProtos.WifiDirectPacket.Builder builderKey = PacketProtos.WifiDirectPacket.newBuilder()
                .setType(PacketProtos.WifiDirectPacket.Type.ANONYMITY_KEY)
                .setMacAddress(macAddr);
        ByteBuffer keyPacket = ByteBuffer.allocate(4 + key.length);
        keyPacket.putInt(anonymSeq);
        keyPacket.put(key);
        builderKey.setContent(ByteString.copyFrom(keyPacket.array()));

        PacketProtos.WifiDirectPacket.Builder builderEncrypt = PacketProtos.WifiDirectPacket.newBuilder()
                .setType(PacketProtos.WifiDirectPacket.Type.ANONYMITY_ENCRYPT)
                .setMacAddress(macAddr);
        ByteBuffer encryptPacket = ByteBuffer.allocate(4 + encrypt.length);
        encryptPacket.putInt(anonymSeq);
        encryptPacket.put(encrypt);
        builderEncrypt.setContent(ByteString.copyFrom(encryptPacket.array()));

        ArrayList<PacketProtos.WifiDirectPacket.Builder> ret = new ArrayList<>();
        ret.add(builderKey);
        ret.add(builderEncrypt);
        return ret;
    }

    public static PacketProtos.WifiDirectPacket.Builder getAnonymReportPacket(long macAddr, AnonymReport report) {
        assert(report.seqNums.length >= 2);
        assert(report.x.length == ANONYMITY_DATA_SIZE);

        ByteBuffer cb = ByteBuffer.allocate(4 + 4 + 4 * report.seqNums.length + report.x.length);
        cb.putInt(report.type);
        cb.putInt(report.seqNums.length);
        for (int i = 0; i < report.seqNums.length; i++) {
            cb.putInt(report.seqNums[i]);
        }
        cb.put(report.x);

        byte[] content = cb.array();
        ///ByteBuffer buffer = createdHeader(new Header(TYPE_ANONYMITY_REPORT, content.length, macAddr));
        ///buffer.put(content);
        PacketProtos.WifiDirectPacket.Builder builder = PacketProtos.WifiDirectPacket.newBuilder()
                .setType(PacketProtos.WifiDirectPacket.Type.ANONYMITY_REPORT)
                .setMacAddress(macAddr)
                .setContent(ByteString.copyFrom(content));

        return builder;
    }

    public static AnonymReport parseAnonymReport(PacketProtos.WifiDirectPacket packet) {
        ///WifiDirectProtocol.Header dummy = new WifiDirectProtocol.Header();
        ///byte[] con = (byte[]) WifiDirectProtocol.parsePacket(is, dummy);

        AnonymReport ret = new AnonymReport();

        ByteBuffer bb = packet.getContent().asReadOnlyByteBuffer();
        ret.type = bb.getInt();
        int nSeqNums = bb.getInt();
        ret.seqNums = new int[nSeqNums];
        for (int i = 0; i < nSeqNums; i++) {
            ret.seqNums[i] = bb.getInt();
        }
        ret.x = new byte[ANONYMITY_DATA_SIZE];
        assert(packet.getContent().size() - bb.position() == ret.x.length);
        bb.get(ret.x, 0, ret.x.length);
        return ret;
    }

    private void processAnonymReport (WifiDirectServer server, AnonymReport report) {
        boolean valuableReport = false;
        byte[] myShare = new byte[ANONYMITY_DATA_SIZE];
        Arrays.fill(myShare, (byte) 0);

        if (server.currentRole.get() == ROLE_ANONYM_SERVER_1 || server.currentRole.get() == ROLE_TEST) {
            if (report.type == ANONYMITY_REPORT_TYPE_ENCRYPT) {
                Log.d(WiFiDirectActivity.TAG, "AnonymReport: type=ENCRYPT, seqNums=" + Arrays.toString(report.seqNums));
                valuableReport = true;
                for (Integer seq: report.seqNums) {
                    if (!server.keyPktSet.containsKey(seq)) {
                        valuableReport = false;
                        break;
                    }
                }
            }
            if (valuableReport) {
                for (Integer seq: report.seqNums) {
                    byte[] row = server.keyPktSet.get(seq);
                    for (int j = 0; j < ANONYMITY_DATA_SIZE; j++) {
                        myShare[j] ^= row[j];
                    }
                    server.keyPktSet.remove(seq);
                }
                Log.d(WiFiDirectActivity.TAG, "After AnonymReport: keyPktSet=" + Arrays.toString(server.keyPktSet.keySet().toArray()));
            }
        }
        if (!valuableReport && (server.currentRole.get() == ROLE_ANONYM_SERVER_2 || server.currentRole.get() == ROLE_TEST)) {
            if (report.type == ANONYMITY_REPORT_TYPE_KEY) {
                Log.d(WiFiDirectActivity.TAG, "AnonymReport: type=KEY, seqNums=" + Arrays.toString(report.seqNums));
                valuableReport = true;
                for (Integer seq: report.seqNums) {
                    if (!server.encryptPktSet.containsKey(seq)) {
                        valuableReport = false;
                        break;
                    }
                }
            }
            if (valuableReport) {
                for (Integer seq: report.seqNums) {
                    byte[] row = server.encryptPktSet.get(seq);
                    for (int j = 0; j < ANONYMITY_DATA_SIZE; j++) {
                        myShare[j] ^= row[j];
                    }
                    server.encryptPktSet.remove(seq);
                }
            }
        }
        if (valuableReport) {
            Log.d(WiFiDirectActivity.TAG, "Valuable AnonymReport");
            byte[] decoded = new byte[ANONYMITY_DATA_SIZE];
            for (int j = 0; j < ANONYMITY_DATA_SIZE; j++) {
                decoded[j] = (byte) (report.x[j] ^ myShare[j]);
            }
            Log.d(WiFiDirectActivity.TAG, "x=" + WiFiDirectActivity.bytesToHex(report.x) +
                    ", my=" + WiFiDirectActivity.bytesToHex(myShare) +
                    ", decoded=" + WiFiDirectActivity.bytesToHex(decoded));
            if (server.onReceiveAnonymPackethandler != null) {
                server.onReceiveAnonymPackethandler.onReceiveAnonymPacket(myShare, report.x, decoded);
            }
        }
    }

    private void sendAnonymReport(WifiDirectServer server) {

        HashMap<Integer, byte[]> toBeReported = null;
        if (server.currentRole.get() == ROLE_ANONYM_SERVER_1 && server.keyPktSet.size() >= 2) {
            toBeReported = server.keyPktSet;
        } else if (server.currentRole.get() == ROLE_ANONYM_SERVER_2 && server.encryptPktSet.size() >= 2) {
            toBeReported = server.encryptPktSet;
        }
        if (toBeReported != null) {
            AnonymReport report = new AnonymReport();
            report.type = (server.currentRole.get() == ROLE_ANONYM_SERVER_1
                    ? ANONYMITY_REPORT_TYPE_KEY
                    : ANONYMITY_REPORT_TYPE_ENCRYPT);
            report.seqNums = new int[toBeReported.size()];
            report.x = new byte[ANONYMITY_DATA_SIZE];
            Arrays.fill(report.x, (byte) 0);
            int i = 0;
            for (HashMap.Entry<Integer, byte[]> entry : toBeReported.entrySet()) {
                report.seqNums[i] = entry.getKey();
                byte[] row = entry.getValue();
                for (int j = 0; j < ANONYMITY_DATA_SIZE; j++) {
                    report.x[j] ^= row[j];
                }
                i++;
            }

            //NetworkInterface nif = WiFiDirectActivity.getWlanP2PInterface();
            //if (nif == null) {
            //    return;
            //}


            //byte[] con = WifiDirectProtocol.getAnonymReportPacket(macAddr, report);
            PacketProtos.WifiDirectPacket.Builder builder = getAnonymReportPacket(NetworkUtility.getMacAddress(), report);
            builder.setPacketNum(WifiDirectServer.getPacketNum());

            Intent serviceIntent = new Intent(server.getActivity(), FileTransferService.class);
            serviceIntent.setAction(FileTransferService.ACTION_BROADCAST_IPV6_P2P);
            serviceIntent.putExtra(FileTransferService.EXTRAS_PACKET_CONTENT, builder.build().toByteArray());
            server.getActivity().startService(serviceIntent);
        }

    }


    @Override
    public boolean onReceive(WifiDirectServer server, PacketProtos.WifiDirectPacket packet)
    {
        ByteBuffer bb = packet.getContent().asReadOnlyByteBuffer();
        int anonymSeq = bb.getInt();

        byte[] x = new byte[ANONYMITY_DATA_SIZE];
        bb.get(x, 0, ANONYMITY_DATA_SIZE);
        if (packet.getType() == PacketProtos.WifiDirectPacket.Type.ANONYMITY_KEY || packet.getType() ==PacketProtos.WifiDirectPacket.Type.ANONYMITY_ENCRYPT) {
            if (packet.getType() == PacketProtos.WifiDirectPacket.Type.ANONYMITY_KEY) {

                Log.d(WiFiDirectActivity.TAG, "Anonym Msg: anonymSeq=" + String.valueOf(anonymSeq) +
                        ", key=" + WiFiDirectActivity.bytesToHex(x));
                if (server.currentRole.get() == ROLE_ANONYM_SERVER_1 || server.currentRole.get() == ROLE_TEST) {
                    if (!server.keyPktSet.containsKey(anonymSeq)) {
                        server.keyPktSet.put(anonymSeq, x);
                        Log.d(WiFiDirectActivity.TAG, "Added to keyPktSet");
                    }
                }
            } else {    // TYPE_ANONYMITY_ENCRYPT
                Log.d(WiFiDirectActivity.TAG, "Anonym Msg: anonymSeq=" + String.valueOf(anonymSeq) +
                        ", encrypt=" + WiFiDirectActivity.bytesToHex(x));
                if (server.currentRole.get() == ROLE_ANONYM_SERVER_2 || server.currentRole.get() == ROLE_TEST) {
                    if (!server.encryptPktSet.containsKey(anonymSeq)) {
                        server.encryptPktSet.put(anonymSeq, x);
                        Log.d(WiFiDirectActivity.TAG, "Added to encryptPktSet");
                    }
                }
            }

            // Send anonymous report
            if (server.currentRole.get() ==  ROLE_TEST)
            {
                server.currentRole.set(ROLE_ANONYM_SERVER_1);
                sendAnonymReport(server);
                server.currentRole.set(ROLE_ANONYM_SERVER_2);
                sendAnonymReport(server);
                server.currentRole.set(ROLE_TEST);
            }
            else {
                sendAnonymReport(server);
            }
        }
        else if (packet.getType() == PacketProtos.WifiDirectPacket.Type.ANONYMITY_REPORT) {
            AnonymReport report = parseAnonymReport(packet);
            processAnonymReport(server, report);
        }

        return false;
    }
}
