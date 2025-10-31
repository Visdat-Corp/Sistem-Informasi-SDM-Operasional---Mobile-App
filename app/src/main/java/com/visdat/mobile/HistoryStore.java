package com.visdat.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HistoryStore {
    private static final String PREF_NAME = "history_store";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_LAST_CHECKIN_TIMESTAMP = "last_checkin_timestamp"; // Baru: Simpan timestamp Cek In terakhir

    public static void addEntry(Context context, String statusText, long timestamp, String jamHadir, String terlambat, String jamPulang, String cepatPulang) {
        addEntry(context, statusText, timestamp, jamHadir, terlambat, jamPulang, cepatPulang, false);
    }

    public static void addEntry(Context context, String statusText, long timestamp, String jamHadir, String terlambat, String jamPulang, String cepatPulang, boolean isFailed) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_ENTRIES, "[]");
            JSONArray array = new JSONArray(json);

            JSONObject obj = new JSONObject();
            obj.put("statusText", statusText);
            obj.put("timestamp", timestamp);
            obj.put("jamHadir", jamHadir);
            obj.put("terlambat", terlambat);
            obj.put("jamPulang", jamPulang);
            obj.put("cepatPulang", cepatPulang);
            obj.put("isFailed", isFailed);

            array.put(0, obj); // prepend newest

            prefs.edit().putString(KEY_ENTRIES, array.toString()).apply();
        } catch (JSONException ignored) { }
    }

    // Baru: Simpan timestamp Cek In terakhir
    public static void saveLastCheckInTimestamp(Context context, long timestamp) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_CHECKIN_TIMESTAMP, timestamp).apply();
    }

    // Baru: Ambil timestamp Cek In terakhir
    public static long getLastCheckInTimestamp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_CHECKIN_TIMESTAMP, 0L); // 0 jika belum ada
    }

    public static List<HistoryItem> getEntries(Context context) {
        List<HistoryItem> items = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ENTRIES, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                HistoryItem item = new HistoryItem(
                        o.optString("statusText", "Berhasil mengambil absen"),
                        o.optLong("timestamp", System.currentTimeMillis()),
                        o.optString("jamHadir", "-"),
                        o.optString("terlambat", "00:00:00"),
                        o.optString("jamPulang", "-"),
                        o.optString("cepatPulang", "00:00:00"),
                        o.optBoolean("isFailed", false)
                );
                items.add(item);
            }
        } catch (JSONException ignored) { }
        return items;
    }

    // Baru: Fungsi untuk membersihkan semua data history lokal
    public static void clearAllEntries(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    public static class HistoryItem {
        public final int id;
        public final String statusText;
        public final long timestamp;
        public final String jamHadir;
        public final String terlambat;
        public final String jamPulang;
        public final String cepatPulang;
        public final boolean isFailed;
        public final String overrideStatus;

        public HistoryItem(int id, String statusText, long timestamp, String jamHadir, String terlambat, String jamPulang, String cepatPulang, boolean isFailed, String overrideStatus) {
            this.id = id;
            this.statusText = statusText;
            this.timestamp = timestamp;
            this.jamHadir = jamHadir;
            this.terlambat = terlambat;
            this.jamPulang = jamPulang;
            this.cepatPulang = cepatPulang;
            this.isFailed = isFailed;
            this.overrideStatus = overrideStatus;
        }

        // Backward compatibility
        public HistoryItem(String statusText, long timestamp, String jamHadir, String terlambat, String jamPulang, String cepatPulang, boolean isFailed) {
            this(0, statusText, timestamp, jamHadir, terlambat, jamPulang, cepatPulang, isFailed, null);
        }

        // Backward compatibility
        public HistoryItem(String statusText, long timestamp, String jamHadir, String terlambat, String jamPulang, String cepatPulang) {
            this(0, statusText, timestamp, jamHadir, terlambat, jamPulang, cepatPulang, false, null);
        }
    }
}
