package jp.ac.titech.itpro.sdl.accelgraph;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Locale;

public class OrientationActivity extends Activity implements SensorEventListener {

    private final static String TAG = "OrientationActivity";

    private TextView rateView, accuracyView;
    private GraphView xView, yView, zView;

    private SensorManager sensorMgr;
    private Sensor accelerometer;
    private Sensor magneSensor;

    private final static long GRAPH_REFRESH_WAIT_MS = 20;

    private GraphRefreshThread th = null;
    private Handler handler;

    private float[] accel = new float[3];
    private float[] magnetic = new float[3];
    private float[] attitude = new float[3];
    private float vx, vy, vz;
    private float rate;
    private int accuracy;
    private long prevts;

    private final static float alpha = 0.75F;

    Long startTime;
    private Button startButton, stopButton;
    private Boolean writing = false;
    OutputStream out;
    PrintWriter writer;
    private final int REQUEST_PERMISSION = 1111;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        rateView = (TextView) findViewById(R.id.rate_view);
        accuracyView = (TextView) findViewById(R.id.accuracy_view);
        xView = (GraphView) findViewById(R.id.x_view);
        yView = (GraphView) findViewById(R.id.y_view);
        zView = (GraphView) findViewById(R.id.z_view);

        sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magneSensor = sensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (accelerometer == null) {
            Toast.makeText(this, getString(R.string.toast_no_accel_error),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (magneSensor == null) {
            Toast.makeText(this, getString(R.string.toast_no_magne_error),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        handler = new Handler();

        startButton = (Button) findViewById(R.id.startButton);
        stopButton = (Button) findViewById(R.id.stopButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkExternalStoragePermission();
                writing = true;
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writing = false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        startTime = System.currentTimeMillis();
        sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorMgr.registerListener(this, magneSensor, SensorManager.SENSOR_DELAY_FASTEST);
        th = new GraphRefreshThread();
        th.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        th = null;
        if (writer != null) writer.close();
        if (out != null) {
            try {
                out.close();
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
        writer = null;
        out = null;
        writing = false;
        sensorMgr.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                magnetic = event.values.clone();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accel = event.values.clone();
                break;
        }
        if (magnetic != null && accel != null) {
            float[] in = new float[9];
            float[] out = new float[9];
            SensorManager.getRotationMatrix( in, null, accel, magnetic);
            SensorManager.remapCoordinateSystem(in, SensorManager.AXIS_X, SensorManager.AXIS_Y, out);
            SensorManager.getOrientation(out, attitude);
        }
        vx = alpha * vx + (1 - alpha) * attitude[1];
        vy = alpha * vy + (1 - alpha) * attitude[2];
        vz = alpha * vz + (1 - alpha) * attitude[0];
        rate = ((float) (event.timestamp - prevts)) / (1000 * 1000);
        prevts = event.timestamp;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "onAccuracyChanged: ");
        this.accuracy = accuracy;
    }

    private class GraphRefreshThread extends Thread {
        public void run() {
            try {
                while (th != null) {
                    handler.post(new Runnable() {
                        public void run() {
                            rateView.setText(String.format(Locale.getDefault(), "%f", rate));
                            accuracyView.setText(String.format(Locale.getDefault(), "%d", accuracy));
                            xView.addData(vx*20/(float)Math.PI, true);
                            yView.addData(vy*20/(float)Math.PI, true);
                            zView.addData(vz*20/(float)Math.PI, true);

                        }
                    });
                    if (writing && writer != null) {
                        Long now = System.currentTimeMillis() - startTime;
                        String str = now/1000+"."+now%1000 + ": " + vx + " " + vy + " " + vz;
                        writer.println(str);
                        writer.flush();
                    }
                    Thread.sleep(GRAPH_REFRESH_WAIT_MS);
                }
            }
            catch (InterruptedException e) {
                Log.e(TAG, e.toString());
                th = null;
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu");
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_accel:
                intent = new Intent(getApplication(), MainActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_light:
                intent = new Intent(getApplication(), LightActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_proximity:
                intent = new Intent(getApplication(), ProximityActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_magne:
                intent = new Intent(getApplication(), MagneticActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_Orientation:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void checkExternalStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            try {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
                } else {
                    openExternalStorage();
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    private void openExternalStorage() {
        String path = Environment.getExternalStorageDirectory().getPath() + "/" + getString(R.string.orientation_name_label) + ".txt";
        try {
            out = new FileOutputStream(path, false);
            writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openExternalStorage();
            }
        }
    }
}
