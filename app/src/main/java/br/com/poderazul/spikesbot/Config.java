package br.com.poderazul.spikesbot;

import android.content.Context;
import android.content.SharedPreferences;

final class Config {
    static final String PACKAGE = "com.ketchapp.donttouchthespikes";
    static final String PREFS = "spikesbot_config_v1";
    static final String RUN = "run";
    static final String CANDY = "candy";
    static final String COLOR = "color";
    static final String RATE = "rate";
    static final String SAFETY = "safety";
    static final String STATUS = "status";
    static final String STATS = "stats";
    private Config() { }
    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static void status(Context context, String status) {
        get(context).edit().putString(STATUS, status).apply();
    }
}
