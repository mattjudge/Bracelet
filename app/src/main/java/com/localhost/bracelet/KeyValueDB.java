package com.localhost.bracelet;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by mattc on 15/11/2015.
 */
public class KeyValueDB {
    private SharedPreferences sharedPreferences;
    private static String PREF_NAME = "prefs";

    public KeyValueDB() {
        // Blank
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getLastConnectedMac(Context context) {
        return getPrefs(context).getString("LAST_CONNECTED_MAC", null);
    }

    public static void setLastConnectedMac(Context context, String input) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putString("LAST_CONNECTED_MAC", input);
        editor.commit();
    }

    public static boolean getHasSentTokenToServer(Context context) {
        return getPrefs(context).getBoolean("SENT_TOKEN_TO_SERVER", false);
    }

    public static void setHasSentTokenToServer(Context context, boolean value) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putBoolean("SENT_TOKEN_TO_SERVER", value);
        editor.commit();
    }

}