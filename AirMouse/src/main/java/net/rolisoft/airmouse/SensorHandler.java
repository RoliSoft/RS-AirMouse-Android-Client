package net.rolisoft.airmouse;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Represents a class which handles sensor measurements.
 *
 * @author RoliSoft
 */
public class SensorHandler implements SensorEventListener {

    private int _selectedSensor;
    private SensorManager _sensorManager;
    private Connection _connection;
    private MainActivity _mainActivity;

    /**
     * Initializes this instance.
     *
     * @param mainActivity The attached main activity instance.
     */
    public SensorHandler(MainActivity mainActivity) {
        _sensorManager = (SensorManager)mainActivity.getSystemService(Context.SENSOR_SERVICE);
        _mainActivity  = mainActivity;
    }

    /**
     * Gets the currently selected sensor's ID.
     *
     * @return Selected sensor ID.
     */
    public int getSelectedSensor() {
        return _selectedSensor;
    }

    /**
     * Switches the sensor to the newly specified sensor ID.
     *
     * @param sensor ID of the newly selected sensor.
     */
    public void setSelectedSensor(int sensor) {
        unregisterSensor();

        _selectedSensor = sensor;

        if (_connection != null && _connection.canSend()) {
            _connection.send("type " + (_selectedSensor + 1));
        }

        registerSensor();
    }

    /**
     * Gets the currently active connection.
     *
     * @return Active connection.
     */
    public Connection getConnection() {
        return _connection;
    }

    /**
     * Sets the currently active connection.
     *
     * @param connection Active connection.
     */
    public void setConnection(Connection connection) {
        _connection = connection;
    }

    /**
     * Occurs when a new measurement is available from the selected sensor.
     * This measurement will be forwarded through the active connection.
     *
     * @param event Sensor measurement data.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (_connection != null && _connection.canSend()) {
            _connection.send("data " + event.values[0] + "," + event.values[1] + "," + event.values[2]);
        } else {
            _mainActivity.disconnect(false);
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
     * Given that a connection is alive, registers for sensor measurement data.
     */
    public void registerSensor() {
        if (_connection != null && _connection.canSend()) {
            _sensorManager.registerListener(this, _sensorManager.getDefaultSensor(_selectedSensor == 0 ? Sensor.TYPE_ACCELEROMETER : Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /**
     * Un-registers from the sensor measurement data.
     * This does not mean the connection was lost, it can also happen when the activity is being suspended
     * or the sensor is simply being switched.
     */
    public void unregisterSensor() {
        _sensorManager.unregisterListener(this);
    }

}
