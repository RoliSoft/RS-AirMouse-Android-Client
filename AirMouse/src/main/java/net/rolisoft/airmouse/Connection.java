package net.rolisoft.airmouse;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Represents a connection to the server.
 *
 * @author RoliSoft
 */
public class Connection {

    private Socket _socket;
    private PrintWriter _writer;

    /**
     * Initializes a new instance.
     *
     * @param host The hostname or IP address to connect to.
     * @param port The port of the server to connect to.
     *
     * @throws IOException Signals a general, I/O-related error.
     */
    public Connection(String host, int port) throws IOException {
        _socket = new Socket(host, port);
        _writer = new PrintWriter(_socket.getOutputStream());
    }

    /**
     * Determines whether the socket is connected.
     *
     * @return True if connected; otherwise, false.
     */
    public boolean isConnected() {
        return _socket != null && _socket.isConnected();
    }

    /**
     * Determines whether the connection is still alive and writable.
     *
     * @return True if writable; otherwise, false.
     */
    public boolean canSend() {
        return _socket != null && _socket.isConnected() && _writer != null && !_writer.checkError();
    }

    /**
     * Sends the specified data over the network.
     *
     * @param data The text to send.
     */
    public void send(String data) {
        if (canSend()) {
            _writer.println(data);
            _writer.flush();
        }
    }

    /**
     * Disconnects the underlying socket.
     */
    public void disconnect() {
        if (_socket != null) {
            try {
                _socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        _socket = null;
        _writer = null;
    }

    /**
     * Sends UDP broadcast packets in order to discover servers on the local network.
     *
     * @param ctx The Android context.
     *
     * @return An exception or list of IP/port combos.
     */
    public static Tuple<Exception, ArrayList<Tuple<String, Integer>>> discoverServers(Context ctx) {
        ArrayList<Tuple<String, Integer>> discovered = new ArrayList<>();

        DatagramSocket socket = null;

        try {
            WifiManager wifi = (WifiManager)ctx.getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcp = wifi.getDhcpInfo();

            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++) {
                quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
            }

            InetAddress bcast = InetAddress.getByAddress(quads);
            byte[] data = "RS-AirMouse discover".getBytes();

            socket = new DatagramSocket(8773);
            socket.setBroadcast(true);
            socket.setSoTimeout(100);
            DatagramPacket packet = new DatagramPacket(data, data.length, bcast, 8337);
            socket.send(packet);

            long time = System.currentTimeMillis() + 1000;
            while (time > System.currentTimeMillis()) {
                try {
                    byte[] recv = new byte[15000];
                    DatagramPacket racket = new DatagramPacket(recv, recv.length);
                    socket.receive(racket);
                    String pckt = new String(recv).trim();
                    StringTokenizer st = new StringTokenizer(pckt);

                    if (!st.hasMoreTokens() || !st.nextToken().contentEquals("RS-AirMouse")) {
                        continue;
                    }

                    Tuple<String, Integer> host = new Tuple(st.nextToken(), Integer.parseInt(st.nextToken()));
                    discovered.add(host);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new Tuple(e, discovered);
        } finally {
            if (socket != null && socket.isBound()) {
                socket.close();
            }
        }

        return new Tuple(null, discovered);
    }

}
