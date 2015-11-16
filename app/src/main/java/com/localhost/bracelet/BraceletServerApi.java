package com.localhost.bracelet;

/**
 * Created by mattc on 14/11/2015.
 */

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.loopj.android.http.*;

import org.json.JSONObject;

import cz.msebera.android.httpclient.Header;

public class BraceletServerApi {

    private static final String BASE_URL = "https://mcj33.user.srcf.net/bracelet/bracelet_api_v1.php";

    private static SyncHttpClient client = new SyncHttpClient();

    private static void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        client.get(getAbsoluteUrl(url), params, responseHandler);
    }

    private static void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        client.post(getAbsoluteUrl(url), params, responseHandler);
    }

    private static String getAbsoluteUrl(String relativeUrl) {
        return BASE_URL + relativeUrl;
    }



    private static String getDeviceUUID(Context c) {
        return Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ANDROID_ID);
    }

    public static void setDeviceGcmId(Context c, String gcmId){
        RequestParams params = new RequestParams();
        params.put("action", "update_device_id");
        params.put("device_id", getDeviceUUID(c));
        params.put("gcm_registered_id", gcmId);

        get("", params, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                Log.d("setgcmid", response.toString());
            }
        });
    }
    public static void sendMessage(Context c, String message){
        RequestParams params = new RequestParams();
        params.put("action", "send_message");
        params.put("device_id", getDeviceUUID(c));
        params.put("message", message);

        Log.d("sendmsg", getDeviceUUID(c));

        get("", params, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                Log.d("sendmsg", response.toString());
            }
        });
    }
}