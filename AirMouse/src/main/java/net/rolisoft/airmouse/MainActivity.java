package net.rolisoft.airmouse;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class MainActivity extends Activity implements SensorEventListener {

    private Menu _menu;
    private int _selSensor;
    private Socket _socket;
    private PrintWriter _writer;
    private SensorManager _sensorMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }

        _sensorMgr = (SensorManager)getSystemService(SENSOR_SERVICE);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        _menu = menu;
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect: {
                showConnectDialog();
                return true;
            }

            case R.id.action_sensor: {
                showSensorDialog();
                return true;
            }

            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void showConnectDialog() {
        if (_socket != null && _socket.isConnected()) {
            disconnect(true);
            return;
        }

        ConnectivityManager conMan = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInf = conMan.getActiveNetworkInfo();

        if (netInf == null || !netInf.isConnected()) {
            Toast.makeText(MainActivity.this, "No active connection found!", Toast.LENGTH_SHORT).show();
            return;
        }

        showDiscoveryConnectDialog();
    }

    private void showDiscoveryConnectDialog() {
        AsyncTask<Void, Void, Tuple<Exception, ArrayList<Tuple<String, Integer>>>> task = new AsyncTask<Void, Void, Tuple<Exception, ArrayList<Tuple<String, Integer>>>>() {

            private ProgressDialog _pd;

            @Override
            protected void onPreExecute() {
                _pd = new ProgressDialog(MainActivity.this);
                _pd.setMessage("Discovering servers...");
                _pd.setIndeterminate(true);
                _pd.setCancelable(false);
                _pd.show();
            }

            @Override
            protected Tuple<Exception, ArrayList<Tuple<String, Integer>>> doInBackground(Void... arg0) {
                ArrayList<Tuple<String, Integer>> discovered = new ArrayList<>();

                DatagramSocket socket = null;

                try {
                    WifiManager wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
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

            @Override
            protected void onPostExecute(Tuple<Exception, ArrayList<Tuple<String, Integer>>> result) {
                if (_pd != null) {
                    _pd.dismiss();
                }

                if (result.x != null) {
                    AlertDialog.Builder dlg  = new AlertDialog.Builder(MainActivity.this);
                    dlg.setTitle("Discovery Error");
                    dlg.setMessage(result.x.getMessage());
                    dlg.setPositiveButton("OK", null);
                    dlg.setCancelable(true);
                    dlg.create().show();

                    if (result.y == null || result.y.size() == 0) {
                        showManualConnectDialog();
                        return;
                    }
                } else {
                    if (result.y == null || result.y.size() == 0) {
                        Toast.makeText(MainActivity.this, "No servers were found on the LAN.", Toast.LENGTH_SHORT).show();
                        showManualConnectDialog();
                    } else {
                        //showConnectDialog(result.y.get(0).x, result.y.get(0).y);
                        showSelectConnectDialog(result.y);
                    }
                }
            }

        };

        task.execute((Void[])null);
    }

    private void showSelectConnectDialog(final ArrayList<Tuple<String, Integer>> servers) {
        AlertDialog dialog;
        AlertDialog.Builder dlgbuild = new AlertDialog.Builder(this);

        CharSequence items[] = new CharSequence[servers.size() + 1];

        for (int i = 0; i < servers.size(); i++) {
            items[i] = servers.get(i).x + ":" + servers.get(i).y;
        }

        items[servers.size()] = "Specify manually ->";

        dlgbuild.setTitle(R.string.select_server)
                .setItems(items, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == servers.size()) {
                            showManualConnectDialog();
                        } else {
                            showConnectDialog(servers.get(which).x, servers.get(which).y);
                        }

                        dialog.dismiss();
                    }

                });

        dialog = dlgbuild.create();
        dialog.show();
    }

    private void showManualConnectDialog() {
        AlertDialog dialog;
        AlertDialog.Builder dlgbuild = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_connect, null);

        final EditText addr = (EditText)view.findViewById(R.id.addressText);
        final EditText port = (EditText)view.findViewById(R.id.portText);

        dlgbuild.setTitle(R.string.connect_server)
                .setView(view)
                .setPositiveButton(R.string.connect, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // placeholder
                    }

                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                });

        final AlertDialog dtmp = dialog = dlgbuild.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (addr.getText().toString().trim().length() == 0) {
                    Toast.makeText(MainActivity.this, "Address cannot be empty!", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (port.getText().toString().trim().length() == 0) {
                    Toast.makeText(MainActivity.this, "Port cannot be empty!", Toast.LENGTH_SHORT).show();
                    return;
                }

                dtmp.dismiss();
                showConnectDialog(addr.getText().toString().trim(), Integer.parseInt(port.getText().toString().trim()));
            }

        });
    }

    private void showSensorDialog() {
        AlertDialog dialog;
        AlertDialog.Builder dlgbuild = new AlertDialog.Builder(this);

        dlgbuild.setTitle(R.string.select_sensor)
                .setSingleChoiceItems(R.array.sensors, _selSensor, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (_selSensor != which) {
                            unregisterSensor();

                            _selSensor = which;

                            if (_socket != null && _socket.isConnected() && _writer != null && !_writer.checkError()) {
                                _writer.println("type " + (which + 1));
                                _writer.flush();
                            }

                            registerSensor();

                            Toast.makeText(MainActivity.this, "Sensor switched to " + getResources().obtainTypedArray(R.array.sensors).getString(which).toLowerCase() + ".", Toast.LENGTH_SHORT).show();
                        }

                        dialog.dismiss();
                    }

                });

        dialog = dlgbuild.create();
        dialog.show();
    }

    private void showConnectDialog(final String host, final int port) {
        AsyncTask<Void, Void, Tuple<Boolean, Exception>> task = new AsyncTask<Void, Void, Tuple<Boolean, Exception>>() {

            private ProgressDialog _pd;

            @Override
            protected void onPreExecute() {
                _pd = new ProgressDialog(MainActivity.this);
                _pd.setMessage("Connecting to " + host + ":" + port + "...");
                _pd.setIndeterminate(true);
                _pd.setCancelable(false);
                _pd.show();
            }

            @Override
            protected Tuple<Boolean, Exception> doInBackground(Void... arg0) {
                try {
                    _socket = new Socket(host, port);
                    _writer = new PrintWriter(_socket.getOutputStream());

                    _writer.println("RS-AirMouse " + (Build.MANUFACTURER + " " + android.os.Build.MODEL).replace(' ', '_') + " " + (_selSensor + 1));
                    _writer.flush();

                    return new Tuple(true, null);
                } catch (IOException e) {
                    e.printStackTrace();
                    return new Tuple(false, e);
                }
            }

            @Override
            protected void onPostExecute(Tuple<Boolean, Exception> result) {
                if (_pd != null) {
                    _pd.dismiss();
                }

                if (result.x) {
                    registerSensor();
                    Toast.makeText(MainActivity.this, "Successfully connected!", Toast.LENGTH_SHORT).show();
                    _menu.findItem(R.id.action_connect).setTitle("Disconnect");
                } else {
                    AlertDialog.Builder dlg  = new AlertDialog.Builder(MainActivity.this);
                    dlg.setTitle("Connection Error");
                    dlg.setMessage(result.y == null ? "Connection timed out." : result.y.getMessage());
                    dlg.setPositiveButton("OK", null);
                    dlg.setCancelable(true);
                    dlg.create().show();
                }
            }

        };

        task.execute((Void[]) null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerSensor();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterSensor();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (_socket != null && _socket.isConnected() && _writer != null && !_writer.checkError()) {
            _writer.println("data " + event.values[0] + "," + event.values[1] + "," + event.values[2]);
            _writer.flush();
        } else {
            disconnect(false);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void disconnect(boolean voluntary) {
        _menu.findItem(R.id.action_connect).setTitle("Connect");

        unregisterSensor();

        if (_socket != null) {
            try {
                _socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        _socket = null;
        _writer = null;

        Toast.makeText(MainActivity.this, voluntary ? "Successfully disconnected." : "Connection lost.", Toast.LENGTH_LONG).show();
    }

    private void registerSensor() {
        if (_socket != null && _socket.isConnected() && _writer != null) {
            _sensorMgr.registerListener(this, _sensorMgr.getDefaultSensor(_selSensor == 0 ? Sensor.TYPE_LINEAR_ACCELERATION : Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void unregisterSensor() {
        _sensorMgr.unregisterListener(this);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }

}