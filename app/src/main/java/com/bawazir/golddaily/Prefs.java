package com.bawazir.golddaily;

import android.content.Context;
import android.content.SharedPreferences;

/** تخزين رابط النموذج على الجهاز (يُطلب عند أول فتح) */
public final class Prefs {

    private static final String FILE = "gold_daily";
    private static final String KEY_URL = "form_url";

    private Prefs() { }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String getUrl(Context c) {
        return sp(c).getString(KEY_URL, "");
    }

    public static void setUrl(Context c, String url) {
        sp(c).edit().putString(KEY_URL, url == null ? "" : url.trim()).apply();
    }

    /** يقبل روابط /exec من Google Apps Script فقط */
    public static boolean isValidUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        return u.startsWith("https://script.google.com/") && u.endsWith("/exec");
    }
}
