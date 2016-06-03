package com.niranjan.tahiti.debugger;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class Server extends AppCompatActivity implements SensorEventListener {

    private List<WebSocket> _sockets;
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private float yaw, pitch, roll;
    private TextView clientStatus;
    float[] gravityVectors, geomagneticVectors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        TextView ipInfo = (TextView) findViewById(R.id.ip_info);
        clientStatus = (TextView) findViewById(R.id.client_status);

        clientStatus.setText("Waiting for client");
        ipInfo.setText("Point ground station to " + getIpAddress()+":5000");


        _sockets = new ArrayList<>();

        AsyncHttpServer server = new AsyncHttpServer();

        server.websocket("/", new AsyncHttpServer.WebSocketRequestCallback() {
            @Override
            public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {

                clientStatus.setText("Ground station connected.");
                webSocket.send("Hello");
                _sockets.add(webSocket);

                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        try {
                            if (ex != null)
                                Log.e("WebSocket", "Error");
                        } finally {
                            _sockets.remove(webSocket);
                        }
                    }
                });

                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        clientStatus.setText("Client connected.");
                    }
                });

            }
        });

        server.listen(5000);

        initialiseSensors();
    }

    private void initialiseSensors(){
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_server, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void pingClients(){
        String json = "{}";
        if(_sockets.size()>0) {
            JSONObject obj = new JSONObject();
            try {

                obj.put("yaw",yaw);
                obj.put("pitch",pitch);
                obj.put("roll",roll);
                json = obj.toString();

            } catch (JSONException e) {
                Log.e("tahiti.debugger", e.toString());
            }
        }
        for (WebSocket socket : _sockets) {
            socket.send(json);
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            gravityVectors = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            geomagneticVectors = event.values;
        if (gravityVectors != null && geomagneticVectors != null) {
            float coordinateRotationMatrix[] = new float[9];
            float geomagneticRotationMatrix[] = new float[9];
            boolean readRotation = SensorManager.getRotationMatrix(
                    coordinateRotationMatrix, geomagneticRotationMatrix,
                    gravityVectors, geomagneticVectors);
            if (readRotation) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(coordinateRotationMatrix, orientation);
                yaw = orientation[0];
                pitch = orientation[1];
                roll = orientation[2];
            }
            pingClients();
        }


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onPause() {
        super.onPause();
        if(sensorManager != null){
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(sensorManager != null){
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public static String getIpAddress() {
        try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = (NetworkInterface) en.nextElement();
                for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()&&inetAddress instanceof Inet4Address) {
                        String ipAddress= inetAddress.getHostAddress();
                        Log.e("IP address",""+ipAddress);
                        return ipAddress;
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("Socket exception", ex.toString());
        }
        return null;
    }
}
