package edu.ucla.cs.zyuan.multihopwfd;

public class QueuedTransferService extends  Thread{
    public static class Option
    {
        public enum Media
        {
            WLANP2P(1),
            WLAN(2),
            BOTH(3);

            public int mode;

            Media(int mode)
            {
                this.mode = mode;
            }
        }
        Media media = Media.BOTH;
    }

    @Override
    public void run()
    {

    }
}
