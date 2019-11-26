package edu.ucla.cs.zyuan.multihopwfd;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;

public class DuplicatePacketDetection {
    public static class PacketLabel
    {
        private long macAddr;
        private int pktNum;
        PacketLabel(){}

        PacketLabel(long macAddr, int pktNum)
        {
            this.macAddr = macAddr;
            this.pktNum = pktNum;
        }

        int getPktNum()
        {
            return pktNum;
        }

        void setPktNum(int pktNum)
        {
            this.pktNum = pktNum;
        }

        @Override
        public boolean equals(Object o) {
            if (getClass() != o.getClass())
                return false;
            PacketLabel pl = (PacketLabel)o;
            return macAddr == pl.macAddr && pktNum == pl.pktNum;
        }

        @Override
        public int hashCode() {
            return (int)(macAddr * 709 + pktNum * 701);
        }
    }

    public static final int MAX_CACHE_NUM = 1000;

    private HashSet<PacketLabel> hashSet = new HashSet<>();
    private Deque<PacketLabel> queue = new ArrayDeque<>();

    boolean duplicate(PacketLabel pl)
    {
        return hashSet.contains(pl);
    }

    void add(PacketLabel pl)
    {
        hashSet.add(pl);
        queue.add(pl);

        if (queue.size() > MAX_CACHE_NUM) {
            hashSet.remove(queue.getFirst());
            queue.pollFirst();
        }
    }

    void clear()
    {
        hashSet.clear();
        queue.clear();
    }
}
