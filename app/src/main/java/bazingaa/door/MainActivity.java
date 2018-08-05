package bazingaa.door;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Properties;

import fi.iki.elonen.NanoHTTPD;

import static java.net.HttpURLConnection.HTTP_OK;

public class MainActivity extends AppCompatActivity {
    private static final int DEFAULT_PORT = 8080;
    private static final String TAG = "MainActivity";

    // INSTANCE OF ANDROID WEB SERVER
    private AndroidWebServer androidWebServer;
    private BroadcastReceiver broadcastReceiverNetworkState;
    private static boolean isStarted = false;

    // VIEW
    private CoordinatorLayout coordinatorLayout;
    private Button btnScan;
    private TextView textViewIpAccess;
    private Toolbar toolbar;
    private LinearLayout connectedView;
    private LinearLayout disconnectedView;
    private Button btnEnableWifi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        // INIT VIEW
        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        textViewIpAccess = findViewById(R.id.textViewIpAccess);
        connectedView = findViewById(R.id.connectedView);
        disconnectedView = findViewById(R.id.disconnectedView);
        btnEnableWifi = findViewById(R.id.btnEnableWifi);
        btnScan = findViewById(R.id.btnScan);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(MainActivity.this,QrCodeScannerActivity.class);
                startActivity(i);
            }
        });
        btnEnableWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                wifiManager.setWifiEnabled(true);
            }
        });

        setIpAccess();
        if (isConnectedInWifi()) {
            getSupportActionBar().setTitle("Connected to ");
            if (!isStarted && startAndroidWebServer()) {
                isStarted = true;
            } else if (stopAndroidWebServer()) {
                isStarted = false;
            }
        } else {
            getSupportActionBar().setTitle("WiFi Disabled");
            Snackbar.make(coordinatorLayout, getString(R.string.wifi_message), Snackbar.LENGTH_LONG).show();
        }

        // INIT BROADCAST RECEIVER TO LISTEN NETWORK STATE CHANGED
        initBroadcastReceiverNetworkStateChanged();
    }

    //region Start And Stop AndroidWebServer
    private boolean startAndroidWebServer() {
        if (!isStarted) {
            int port = DEFAULT_PORT;
            try {
                if (port == 0) {
                    throw new Exception();
                }
                androidWebServer = new AndroidWebServer(port,this);
                androidWebServer.start();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                Snackbar.make(coordinatorLayout, "The PORT " + port + " doesn't work, please change it between 1000 and 9999.", Snackbar.LENGTH_LONG).show();
            }
        }
        return false;
    }

    private boolean stopAndroidWebServer() {
        if (isStarted && androidWebServer != null) {
            androidWebServer.stop();
            return true;
        }
        return false;
    }


    //region Private utils Method
    private void setIpAccess() {
        String ip = getIpAccess();
        if (ip.equals("0.0.0.0")) {
            getSupportActionBar().setTitle("WiFi Disabled");
            disconnectedView.setVisibility(View.VISIBLE);
            connectedView.setVisibility(View.GONE);
        } else {
            disconnectedView.setVisibility(View.GONE);
            connectedView.setVisibility(View.VISIBLE);
            getSupportActionBar().setTitle("Connected to " + getSSID());
        }
        textViewIpAccess.setText(getIpAccess());
    }

    private String getSSID() {
        WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        String ssid = info.getSSID();
        ssid = ssid.substring(1, ssid.length() - 1);
        Log.e("SSID", ssid);
        return ssid;
    }

    private void initBroadcastReceiverNetworkStateChanged() {
        final IntentFilter filters = new IntentFilter();
        filters.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filters.addAction("android.net.wifi.STATE_CHANGE");
        broadcastReceiverNetworkState = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setIpAccess();
            }
        };
        super.registerReceiver(broadcastReceiverNetworkState, filters);
    }

    private String getIpAccess() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        final String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        return formatedIpAddress;
    }


    public boolean isConnectedInWifi() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        NetworkInfo networkInfo = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()
                && wifiManager.isWifiEnabled() && networkInfo.getTypeName().equals("WIFI");
    }
    //endregion


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAndroidWebServer();

        isStarted = false;
        if (broadcastReceiverNetworkState != null) {
            unregisterReceiver(broadcastReceiverNetworkState);
        }
    }
}
