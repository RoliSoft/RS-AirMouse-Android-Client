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

/**
 * Main activity of the AirMouse application.
 */
public class MainActivity extends Activity implements SensorEventListener {

    private Menu _menu;
    private int _selSensor;
    private Connection _con;
    private SensorManager _sensorMgr;

    /**
     * Called when the activity is starting.
     *
     * @param savedInstanceState Contains previously supplied data.
     */
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

    /**
     * Creates the menu for the activity.
     *
     * @param menu Active menu instance.
     *
     * @return True if the initialization was successful.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        _menu = menu;
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * This hook is called when a menu item is tapped.
     *
     * @param item The selected menu item.
     *
     * @return Value indicating whether it was handled.
     */
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

            case R.id.action_recalibrate: {
                sendRecalibrate();
                return true;
            }

            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    /**
     * Prepares for showing the connect dialog.
     * If there is internet connection, starts the server discovery; otherwise, fails.
     */
    private void showConnectDialog() {
        if (_con != null && _con.isConnected()) {
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

    /**
     * Shows a progress dialog while server discovery is running.
     * If the server discovery was successful, a list will be displayed to the user.
     * If the server discovery was not successful, the user will be taken to the manual connect dialog.
     */
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
                return Connection.discoverServers(MainActivity.this);
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

    /**
     * Shows the list of discovered servers to the user for choice, additionally with the option to
     * input a manual address to connect to.
     *
     * @param servers The list of discovered server.
     *                The array items are IP/port pairs.
     */
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
                            showConnectProgressDialog(servers.get(which).x, servers.get(which).y);
                        }

                        dialog.dismiss();
                    }

                });

        dialog = dlgbuild.create();
        dialog.show();
    }

    /**
     * Shows a dialog which allows for manual entry of the IP address or hostname and port of the
     * server the user wishes to connect to. This is mostly being shown when the server discovery failed
     * or the user intentionally chose to enter a manual server location.
     */
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
                showConnectProgressDialog(addr.getText().toString().trim(), Integer.parseInt(port.getText().toString().trim()));
            }

        });
    }

    /**
     * Shows a dialog which lets the user choose from the list of supported sensors.
     * If the user changes the sensor, the change is propagated through the connection.
     */
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
                            _con.send("type " + (which + 1));

                            registerSensor();

                            Toast.makeText(MainActivity.this, "Sensor switched to " + getResources().obtainTypedArray(R.array.sensors).getString(which).toLowerCase() + ".", Toast.LENGTH_SHORT).show();
                        }

                        dialog.dismiss();
                    }

                });

        dialog = dlgbuild.create();
        dialog.show();
    }

    /**
     * Initiates a new connection to the specified server and shows a dialog while doing it.
     *
     * @param host The IP address or hostname of the remote server.
     * @param port The port number of the remote server.
     */
    private void showConnectProgressDialog(final String host, final int port) {
        AsyncTask<Void, Void, Tuple<Boolean, Exception>> task = new AsyncTask<Void, Void, Tuple<Boolean, Exception>>() {

            private ProgressDialog _pd;

            /**
             * Sets up the progress dialog.
             */
            @Override
            protected void onPreExecute() {
                _pd = new ProgressDialog(MainActivity.this);
                _pd.setMessage("Connecting to " + host + ":" + port + "...");
                _pd.setIndeterminate(true);
                _pd.setCancelable(false);
                _pd.show();
            }

            /**
             * Initiates a new connection.
             *
             * @param arg0 Unused arguments.
             *
             * @return A pair of values indicating whether the connection failed,
             *         and if so, the causing Exception is attached.
             */
            @Override
            protected Tuple<Boolean, Exception> doInBackground(Void... arg0) {
                try {
                    _con = new Connection(host, port);
                    _con.send("RS-AirMouse " + (Build.MANUFACTURER + " " + android.os.Build.MODEL).replace(' ', '_') + " " + (_selSensor + 1));

                    return new Tuple(true, null);
                } catch (IOException e) {
                    e.printStackTrace();
                    return new Tuple(false, e);
                }
            }

            /**
             * Informs the user of the successfulness of the connection.
             *
             * @param result A pair of values indicating whether the connection failed,
             *               and if so, the causing Exception is attached.
             */
            @Override
            protected void onPostExecute(Tuple<Boolean, Exception> result) {
                if (_pd != null) {
                    _pd.dismiss();
                }

                if (result.x) {
                    registerSensor();
                    Toast.makeText(MainActivity.this, "Successfully connected!", Toast.LENGTH_SHORT).show();
                    _menu.findItem(R.id.action_connect).setTitle("Disconnect");
                    _menu.findItem(R.id.action_recalibrate).setEnabled(true);
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

    /**
     * When the activity is resumed, re-registers for sensor readings.
     */
    @Override
    protected void onResume() {
        super.onResume();
        registerSensor();
    }

    /**
     * When the activity is paused, de-register from sensor readings.
     */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterSensor();
    }

    /**
     * Occurs when a new measurement is available from the selected sensor.
     * This measurement will be forwarded through the active connection.
     *
     * @param event Sensor measurement data.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (_con != null && _con.canSend()) {
            _con.send("data " + event.values[0] + "," + event.values[1] + "," + event.values[2]);
        } else {
            disconnect(false);
        }
    }

    /**
     * Occurs when the accuracy of the selected sensor has changed.
     * This function is not implemented, due to the lack of accuracy changes in the supported sensors.
     *
     * @param sensor The sensor instance on which the change happened.
     * @param accuracy The new accuracy on the sensor.
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * Disconnects an existing connection.
     *
     * @param voluntary Value indicating whether the disconnect was initiated by the user,
     *                  or happening due to connection loss.
     */
    private void disconnect(boolean voluntary) {
        _menu.findItem(R.id.action_connect).setTitle("Connect");
        _menu.findItem(R.id.action_recalibrate).setEnabled(false);

        unregisterSensor();

        if (_con != null) {
            _con.disconnect();
        }

        Toast.makeText(MainActivity.this, voluntary ? "Successfully disconnected." : "Connection lost.", Toast.LENGTH_LONG).show();
    }

    /**
     * Given that a connection is alive, registers for sensor measurement data.
     */
    private void registerSensor() {
        if (_con != null && _con.canSend()) {
            _sensorMgr.registerListener(this, _sensorMgr.getDefaultSensor(_selSensor == 0 ? Sensor.TYPE_ACCELEROMETER : Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /**
     * Un-registers from the sensor measurement data.
     * This does not mean the connection was lost, it can also happen when the activity is being suspended
     * or the sensor is simply being switched.
     */
    private void unregisterSensor() {
        _sensorMgr.unregisterListener(this);
    }

    /**
     * Sends recalibration packet to the server.
     */
    private void sendRecalibrate() {
        if (_con != null && _con.canSend()) {
            _con.send("reset");
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        /**
         * Initializes this instance.
         */
        public PlaceholderFragment() {

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }

}