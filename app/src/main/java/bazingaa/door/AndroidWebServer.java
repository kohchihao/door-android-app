package bazingaa.door;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import fi.iki.elonen.NanoHTTPD;

import static android.content.Context.WIFI_SERVICE;

/**
 * Created by marcuskoh on 24/7/18.
 */

public class AndroidWebServer extends NanoHTTPD {

    Context ctx;

    public AndroidWebServer(int port, Context ctx) {
        super(port);
        this.ctx = ctx;
    }

    public AndroidWebServer(String hostname, int port, Context ctx) {

        super(hostname, port);
        this.ctx = ctx;
    }


    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> headers = session.getHeaders();
        Map<String, String> params = session.getParms();
        Method method = session.getMethod();
        String uri = session.getUri();
        Log.e("URL", uri);
        Map<String, String> files = new HashMap<>();
        if( Method.OPTIONS.equals(method)) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("status", "ok");
                return outputInJSON(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (Method.POST.equals(method) || Method.PUT.equals(method)) {
            try {
                Log.e("Method", method.name());
                session.parseBody(files);
            } catch (IOException | ResponseException ioe) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("status", "error");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", jsonObject.toString());
            }
        }

        //need to do checking

        if ("/uploadfile".equalsIgnoreCase(uri)) {
            Log.e("Upload ","file");
            String filename = params.get("filename");
            Log.e("Filename", filename);
            String tmpFilePath = files.get("filename");
            Log.e("Filename",tmpFilePath);
            if (null == filename || null == tmpFilePath) {
                // Response for invalid parameters
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("status", "no file");
                    return outputInJSON(jsonObject);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            File dst = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + params.get("filename"));
            File src = new File(files.get("filename"));
            Log.e("Source", String.valueOf(src));
            try {
                Utils.copy(src, dst);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("status", "done");

                return outputInJSON(jsonObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if ("/check".equalsIgnoreCase(uri)) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("type", "android");
                jsonObject.put("wifi_ssid", getSSID());
                jsonObject.put("check", "success");
                return outputInJSON(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if ("/files".equalsIgnoreCase(uri)) {
            File rootDir = new File( Environment.getExternalStorageDirectory() +  File.separator  + uri);
            File[] files2 = rootDir.listFiles();
            String answer = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><title>sdcard0 - TECNO P5 - WiFi File Transfer Pro</title>";
            for (File detailsOfFiles : files2) {
                if(detailsOfFiles.isFile()){
                    answer += detailsOfFiles.getAbsolutePath() + "<br>";
                }else{
                    answer += "<a href=\"" + detailsOfFiles.getAbsolutePath()
                            + "\" alt = \"\">" + detailsOfFiles.getAbsolutePath()
                            + "</a><br>";
                }
            }
            answer += "</head></html>";
            return NanoHTTPD.newFixedLengthResponse(answer);

        }


        if (uri.contains("/download/")) {
            InputStream mbuffer = null;
            uri = uri.substring(9);
            Log.e("after",uri);
            File request = new File(uri);
            try {
                mbuffer = new FileInputStream(request);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            FileNameMap fileNameMap = URLConnection.getFileNameMap();
            String mimeType = fileNameMap.getContentTypeFor(uri);

            Response streamResponse = NanoHTTPD.newChunkedResponse(Response.Status.OK, mimeType, mbuffer);
            Random rnd = new Random();
            String etag = Integer.toHexString( rnd.nextInt() );
            streamResponse.addHeader("Access-Control-Allow-Methods", "DELETE, GET, POST, PUT");
            streamResponse.addHeader("Access-Control-Allow-Origin", "*");
            streamResponse.addHeader( "ETag", etag);
            streamResponse.addHeader( "Connection", "Keep-alive");
            return streamResponse;
        }

        Response response;
        response = NanoHTTPD.newFixedLengthResponse(getWebsite());
        response.addHeader("Access-Control-Allow-Methods", "*");
        response.addHeader("Access-Control-Allow-Origin", "*");


        return response;
    }



    private String getWebsite() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><h1>Hello server</h1>\n");
        sb.append(" </body></html>\n");
        return sb.toString();
    }

    private String getSSID() {
        WifiManager wifiManager = (WifiManager) ctx.getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        String ssid = info.getSSID();
        ssid = ssid.substring(1, ssid.length() - 1);
        Log.e("SSID", ssid);
        return ssid;
    }

    private Response outputInJSON(JSONObject obj) {
        String MIME_JSON = "application/json";
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_JSON, obj.toString());
        //response.addHeader("Access-Control-Allow-Methods", "DELETE, GET, POST, PUT, OPTIONS");
        response.addHeader("Access-Control-Allow-Origin", "*");


        response.addHeader("Access-Control-Max-Age", "3628800");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "X-Requested-With");
        response.addHeader("Access-Control-Allow-Headers", "Authorization");

        return response;
    }
}
