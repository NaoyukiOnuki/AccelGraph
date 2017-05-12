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

/**
 * Created by onuki on 2017/05/12.
 */

public class ProximityActivity extends Activity implements SensorEventListener {

    private final static String TAG = "ProximityActivity";

    private TextView rateView, accuracyView;
    private GraphView graphView;

    private SensorManager sensorMgr;
    private Sensor sensor;

    private final static long GRAPH_REFRESH_WAIT_MS = 20;

    private GraphRefreshThread th = null;
    private Handler handler;

    private float v;
    private float rate;
    private int accuracy;
    private long prevts;

    private final static float alpha = 0F;

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
        setContentView(R.layout.activity_proximity);

        rateView = (TextView) findViewById(R.id.rate_view);
        accuracyView = (TextView) findViewById(R.id.accuracy_view);
        graphView = (GraphView) findViewById(R.id.light_view);

        sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (sensor == null) {
            Toast.makeText(this, getString(R.string.toast_no_light_error),
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
        sensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
        th = new GraphRefreshThread();
        th.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
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
        th = null;
        sensorMgr.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        v = alpha * v + (1 - alpha) * event.values[0];
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
                            graphView.addData(v, true);
                        }
                    });
                    if (writing && writer != null) {
                        Long now = System.currentTimeMillis() - startTime;
                        String str = now/1000+"."+now%1000 + ": " + v;
                        writer.println(str);
                        writer.flush();
                    }
                    Thread.sleep(GRAPH_REFRESH_WAIT_MS);
                }
            } catch (InterruptedException e) {
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
                return true;
            case R.id.menu_magne:
                intent = new Intent(getApplication(), MagneticActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_Orientation:
                intent = new Intent(getApplication(), OrientationActivity.class);
                startActivity(intent);
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
        String path = Environment.getExternalStorageDirectory().getPath() + "/" + getString(R.string.proxi_name_label) + ".txt";
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
