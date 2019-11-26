/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.ucla.cs.zyuan.multihopwfd;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;


import edu.ucla.cs.zyuan.multihopwfd.ServerPacketHandler.AnonymousPacketHandler;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    protected static final int RESULT_FOR_BROADCAST_HELLO_CODE = 21;
    protected static final int RESULT_FOR_UNICAST_HELLO_CODE = 22;
    protected static final int RESULT_FOR_UNICAST_IPV6_HELLO_CODE = 23;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;

    private Handler mHandler;
    private Thread UDPThread, TCPThread, V6UDPThread;
    public DeviceDetailFragment()
    {
        mHandler = new Handler(){

            @Override
            public void handleMessage(Message msg) {
                String str = (String)msg.obj;
                DebugPanel dp = (DebugPanel)mContentView.getRootView().findViewById(R.id.debug_panel);
                dp.appendNewLine(str);
            }
        };
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        /*
        UDPThread = new Thread() {
            @Override
            public void run() {
                DatagramSocket socket = null;
                try {
                    socket = new DatagramSocket(8987);
                    Log.d(WiFiDirectActivity.TAG, "UDPServer: Socket opened");
                    byte[] buf = new byte[1024];
                    while (true) {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);
                        addStringToDP( new String(packet.getData(), 0, packet.getLength()));
                    }
                } catch (IOException e) {
                    Log.e(WiFiDirectActivity.TAG, e.getMessage());
                } finally {
                    if (socket != null) {
                        socket.close();
                    }
                }
            }
        };
        UDPThread.start();
        TCPThread = new Thread() {
            @Override
            public void run() {
                ServerSocket serverSocket = null;
                try{
                    serverSocket = new ServerSocket(8985);
                    Log.d(WiFiDirectActivity.TAG, "TCPServer: Socket opened");
                    while (true) {
                        Socket client = serverSocket.accept();
                        Log.d(WiFiDirectActivity.TAG, "Server: connection done");
                        InputStream inputstream = client.getInputStream();
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        copyFile(inputstream, output);
                        addStringToDP(output.toString());
                    }
                } catch (IOException e) {
                    Log.e(WiFiDirectActivity.TAG, e.getMessage());
                } finally {
                    if (serverSocket != null) {
                        try {
                            serverSocket.close();
                        }catch (IOException e){}
                    }
                }
            }
        };
        TCPThread.start();
        V6UDPThread = new Thread()
        {
            @Override //port 8984
            public void run() {
                MulticastSocket multicastSocket = null;
                try {
                    multicastSocket = new MulticastSocket(8984);
                    multicastSocket.joinGroup( InetAddress.getByName("FF7E:230::1237"));
                    while (true) {
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        multicastSocket.receive(receivePacket);
                        addStringToDP(receivePacket.getData().toString());
                    }
                } catch (Exception e) {
                    Log.d(WiFiDirectActivity.TAG, e.toString());
                } finally {
                    multicastSocket.close();
                }
            }
        };
        V6UDPThread.start();
        */
    }

    @Override
    public void onDestroy() {
        /*
        V6UDPThread.interrupt();
        TCPThread.interrupt();
        UDPThread.interrupt();*/
        super.onDestroy();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
                        , new DialogInterface.OnCancelListener() {

                            @Override
                            public void onCancel(DialogInterface dialog) {
                                ((DeviceListFragment.DeviceActionListener) getActivity()).cancelDisconnect();
                            }
                        }
                );
                ((DeviceListFragment.DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceListFragment.DeviceActionListener) getActivity()).disconnect();
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });

        mContentView.findViewById(R.id.btn_broad_cast).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        //Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        //intent.setType("image/*");
                        //startActivityForResult(intent, RESULT_FOR_BROADCAST_HELLO_CODE);
                        onActivityResult(RESULT_FOR_BROADCAST_HELLO_CODE, 0, null);
                    }
                });

        mContentView.findViewById(R.id.btn_uni_cast).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onActivityResult(RESULT_FOR_UNICAST_HELLO_CODE, 0, null);
                    }
                });

        mContentView.findViewById(R.id.btn_uni_cast_ipv6).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onActivityResult(RESULT_FOR_UNICAST_IPV6_HELLO_CODE, 0, null);
                    }
                }
        );

        mContentView.findViewById(R.id.btn_broad_cast_ipv6).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                serviceIntent.setAction(FileTransferService.ACTION_BROADCAST_IPV6_HELLO);
                getActivity().startService(serviceIntent);
            }
        });

        mContentView.findViewById(R.id.btn_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NetworkInterface nif = WiFiDirectActivity.getWlanP2PInterface();
                if (nif == null) {
                    return;
                }

                long macAddr = 0 ;
                try {
                    byte[] macAddrBytes = nif.getHardwareAddress();
                    macAddr = NetworkUtility.bytesToLong(macAddrBytes);
                } catch (SocketException e) {
                }

                byte[] con = (new String("hello")).getBytes();
                PacketProtos.WifiDirectPacket pkt = PacketProtos.WifiDirectPacket.newBuilder()
                        .setType(PacketProtos.WifiDirectPacket.Type.DATA)
                        .setPacketNum(WifiDirectServer.getPacketNum())
                        .setMacAddress(macAddr)
                        .setContent(ByteString.copyFrom(con))
                        .build();

                Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                serviceIntent.setAction(FileTransferService.ACTION_BROADCAST_IPV6_P2P);
                serviceIntent.putExtra(FileTransferService.EXTRAS_PACKET_CONTENT, pkt.toByteArray());
                getActivity().startService(serviceIntent);

            }
        });

        mContentView.findViewById(R.id.btn_sendtext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NetworkInterface nif = WiFiDirectActivity.getWlanP2PInterface();
                if (nif == null) {
                    return;
                }

                long macAddr = 0 ;
                try {
                    byte[] macAddrBytes = nif.getHardwareAddress();
                    macAddr = NetworkUtility.bytesToLong(macAddrBytes);
                } catch (SocketException e) {
                }

                byte[] con = ((EditText)mContentView.findViewById(R.id.et_message)).getText().toString().getBytes();
                //byte[] pkt = WifiDirectProtocol.getDataPacket(macAddr, con);
                PacketProtos.WifiDirectPacket pkt = PacketProtos.WifiDirectPacket.newBuilder()
                        .setType(PacketProtos.WifiDirectPacket.Type.DATA)
                        .setPacketNum(WifiDirectServer.getPacketNum())
                        .setMacAddress(macAddr)
                        .setContent(ByteString.copyFrom(con))
                        .build();

                Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                serviceIntent.setAction(FileTransferService.ACTION_BROADCAST_IPV6_P2P);
                serviceIntent.putExtra(FileTransferService.EXTRAS_PACKET_CONTENT, pkt.toByteArray());

                getActivity().startService(serviceIntent);

            }
        });

        mContentView.findViewById(R.id.btn_send_anonymity_text).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

//                NetworkInterface nif = null;
//
//                nif = WiFiDirectActivity.getWlanP2PInterface();
//                if (nif == null) {
//                    return;
//                }
//
//                byte[] macAddr = new byte[6];
//                try {
//                    macAddr = nif.getHardwareAddress();
//                } catch (SocketException e) {
//                }

                byte[] con = ((EditText) mContentView.findViewById(R.id.et_message)).getText().toString().getBytes();
//                ArrayList<byte[]> anonymPkts = WifiDirectProtocol.getAnonymityPackets(macAddr, con, (int) macAddr[4] * (int) con[0] * (int) con[1]);
                ArrayList<PacketProtos.WifiDirectPacket.Builder> anonymPkts = AnonymousPacketHandler.getAnonymityPackets(NetworkUtility.getMacAddress(), con, (int) (Math.random() * 10000));

                // Send key and encrypt packets
                for (int i = 0; i < 2; i++) {
                    Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                    serviceIntent.setAction(FileTransferService.ACTION_BROADCAST_IPV6_P2P);
                    serviceIntent.putExtra(FileTransferService.EXTRAS_PACKET_CONTENT, anonymPkts.get(i).setPacketNum(WifiDirectServer.getPacketNum()).build().toByteArray());
                    getActivity().startService(serviceIntent);
                }

            }
        });

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        if (requestCode == CHOOSE_FILE_RESULT_CODE) {
            Uri uri = data.getData();
            TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
            statusText.setText("Sending: " + uri);
            Log.d(WiFiDirectActivity.TAG, "Intent----------- " + uri);
            Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);

            serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
            serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                    info.groupOwnerAddress.getHostAddress());
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
            getActivity().startService(serviceIntent);
        }
        else if (requestCode == RESULT_FOR_BROADCAST_HELLO_CODE)
        {
            Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
            serviceIntent.setAction(FileTransferService.ACTION_BROADCAST_HELLO);
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8986);
            getActivity().startService(serviceIntent);
        }
        else if (requestCode == RESULT_FOR_UNICAST_HELLO_CODE)
        {
            Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
            serviceIntent.setAction(FileTransferService.ACTION_UNICAST_HELLO);
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                    "192.168.49.1");//info.groupOwnerAddress.getHostAddress());
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8985);
            getActivity().startService(serviceIntent);
        }
        else if (requestCode == RESULT_FOR_UNICAST_IPV6_HELLO_CODE)
        {
            Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
            serviceIntent.setAction(FileTransferService.ACTION_UNICAST_IPV6_HELLO);
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                    "192.168.49.1");//info.groupOwnerAddress.getHostAddress());
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8985);
            getActivity().startService(serviceIntent);
        }
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        DebugPanel debugPanel = (DebugPanel)mContentView.getRootView().findViewById(R.id.debug_panel);


        this.info = info;
        //DebugPanel dp = (DebugPanel)mContentView.findViewById(R.id.debug_panel);
        //1dp.appendNewLine(info.groupOwnerAddress.getHostAddress());
        this.getView().setVisibility(View.VISIBLE);

        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            //new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text))
             //       .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
           //mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);

        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            //mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
        }

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     * 
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.VISIBLE);
    }


    public void addStringToDP(String str)
    {
        Message msg = mHandler.obtainMessage(0, str);
        msg.sendToTarget();
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                ServerSocket serverSocket = new ServerSocket(8988);
                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(WiFiDirectActivity.TAG, "Server: connection done");
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();

                Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                statusText.setText("File copied - " + result);
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                context.startActivity(intent);
            }

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }

    }

    public static class UDPServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;
        private DebugPanel debugPanel;

        /**
         * @param context
         * @param debugPanel
         */
        public UDPServerAsyncTask(Context context, View debugPanel) {
            this.context = context;
            this.debugPanel = (DebugPanel)debugPanel;
        }

        @Override
        protected String doInBackground(Void... params) {

            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(8987);
                Log.d(WiFiDirectActivity.TAG, "UDPServer: Socket opened");
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                Log.d(WiFiDirectActivity.TAG, "UDPServer: Receive");
                return new String(packet.getData(), 0, packet.getLength());
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            } finally {
                if (socket!=null)
                {
                    socket.close();
                }
            }

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                debugPanel.appendNewLine(result);
            }

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
        }

    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }

}
