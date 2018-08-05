package bazingaa.door;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

/**
 * Created by marcuskoh on 24/7/18.
 */

public class ConnectedActivity extends AppCompatActivity {

    private static final String TAG = "ConnectedActivity";
    private TextView txtRoom;
    private Socket mSocket;
    private static final String URL = "https://door-server.glitch.me/";
    private static final int PERMISSIONS_REQUEST_STORAGE = 2;
    private Button btnUpload;
    private static final int PICKFILE_RESULT_CODE = 1;
    private String room;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_connected);

        txtRoom = findViewById(R.id.txtRoom);
        btnUpload = findViewById(R.id.btnUpload);

        Bundle bundle = getIntent().getExtras();
        room = bundle.getString("room", "empty?");
        Log.e("Room", room);
        txtRoom.setText(room);

        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkPermissions()) {
                    openFilePicker();
                } else {
                    getStoragePermission();
                }

            }
        });

        //SOCKET
        try {
            mSocket = IO.socket(URL);
        } catch (URISyntaxException e) {
            Log.v("AvisActivity", "error connecting to socket");
        }


        mSocket.connect();
        mSocket.emit("room", room);
        JSONObject androidData = new JSONObject();
        try {
            androidData.put("room", room);
            androidData.put("status", "phone_connected");
            androidData.put("message", "Android has joined");
            androidData.put("ip", getIpAccess() + "8080");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mSocket.emit("mobile", androidData);

        mSocket.on("message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                Log.e("Data in coming...", data.toString());
                try {
                    if (data.has("status")) {
                        String status = data.getString("status");
                        if (status.equals("disconnect")) {
                            Log.e(TAG, "disconnected");
                            ConnectedActivity.this.finish();
                        }
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICKFILE_RESULT_CODE);
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void getStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            //mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_STORAGE);
        }
    }

    private String getIpAccess() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        final String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        return "http://" + formatedIpAddress + ":";
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSocket.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSocket.disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PICKFILE_RESULT_CODE:
                if (resultCode == RESULT_OK) {

                    Uri uri = data.getData();

                    String real_path = RealPathUtil.getPathFromUri(this, uri);

                    if (real_path.contains("raw:")) {
                        real_path = real_path.substring(4);
                    }
                    Log.e("FilePath", real_path);
                    String[] fileTypeSplit = real_path.split("\\.");

                    String fileType = fileTypeSplit[fileTypeSplit.length - 1];

                    String[] fileNameSplit = real_path.split("/");

                    String fileName = fileNameSplit[fileNameSplit.length - 1];

                    JSONObject androidData = new JSONObject();
                    try {
                        androidData.put("room", room);
                        androidData.put("status", "incoming_files");
                        androidData.put("ip", getIpAccess() + "8080");
                        androidData.put("file_path", real_path);
                        androidData.put("file_name", fileName);
                        androidData.put("file_type", fileType);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.e("Android data", androidData.toString());

                    mSocket.emit("incoming_files", androidData);
                    Log.e("Path isssss ", real_path);
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    openFilePicker();


                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }
}
