package com.visdat.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Singleton OkHttp client that shares session cookies across activities.
 * - Uses in-memory cookie store so login session is reused by SummaryPage.
 * - Disables redirects to detect unauthorized (302) responses from auth middleware.
 * - Adds Accept: application/json to all requests so API returns JSON (401) instead of HTML.
 * - Supports Bearer token authentication for API requests.
 * - Includes automatic token refresh mechanism.
 */
public class ApiClient {

    private static final String TAG = "ApiClient";

    // Base URL including /api prefix for convenience
    public static final String API_BASE_URL = "http://192.168.220.144:8000/api";

    private static OkHttpClient client;
    private static Context appContext;
    private static boolean isRefreshingToken = false;

    // Simple in-memory cookie store shared across app process
    private static final Map<String, List<Cookie>> cookieStore = new HashMap<>();

    public static synchronized OkHttpClient getClient(Context context) {
        if (client == null) {
            appContext = context.getApplicationContext();
            client = new OkHttpClient.Builder()
                    .cookieJar(new CookieJar() {
                        @Override
                        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                            cookieStore.put(url.host(), cookies);
                        }

                        @Override
                        public List<Cookie> loadForRequest(HttpUrl url) {
                            List<Cookie> cookies = cookieStore.get(url.host());
                            return cookies != null ? cookies : new ArrayList<>();
                        }
                    })
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request.Builder requestBuilder = original.newBuilder()
                                .header("Accept", "application/json");

                        // Add Bearer token if available for protected endpoints
                        String authToken = getStoredAuthToken();
                        if (authToken != null && !authToken.isEmpty()) {
                            // Only add Bearer token for protected API endpoints
                            String url = original.url().toString();
                            if (isProtectedEndpoint(url)) {
                                requestBuilder.header("Authorization", "Bearer " + authToken);
                                Log.d(TAG, "Interceptor added Authorization header for: " + url);
                            } else {
                                Log.d(TAG, "Interceptor skipped Authorization header for public endpoint: " + url);
                            }
                        } else {
                            Log.w(TAG, "Interceptor found no auth token for request: " + original.url().toString());
                        }

                        Request request = requestBuilder.build();
                        Response response = chain.proceed(request);

                        // Handle 401 Unauthorized responses with automatic token refresh
                        if (response.code() == 401 && isProtectedEndpoint(original.url().toString())
                            && !isRefreshingToken && !original.url().toString().contains("/refresh-token")) {

                            Log.d(TAG, "Received 401, attempting token refresh");
                            response.close(); // Close the original response

                            // Try to refresh token
                            if (refreshTokenSync()) {
                                // Retry the original request with new token
                                String newToken = getStoredAuthToken();
                                if (newToken != null && !newToken.isEmpty()) {
                                    Request newRequest = original.newBuilder()
                                            .header("Accept", "application/json")
                                            .header("Authorization", "Bearer " + newToken)
                                            .build();
                                    return chain.proceed(newRequest);
                                }
                            }
                        }

                        return response;
                    })
                    .build();
        }
        return client;
    }

    /**
     * Check if the endpoint requires authentication
     */
    private static boolean isProtectedEndpoint(String url) {
        // Login endpoints don't need Bearer token
        if (url.contains("/login") || url.contains("/register")) {
            return false;
        }

        // Public endpoints that don't need authentication
        if (url.contains("/locations") || url.contains("/departments") ||
            url.contains("/positions") || url.contains("/settings") ||
            url.contains("/server-time") || url.contains("/jam-kerja")) {
            return false;
        }

        // All other API endpoints are protected
        return url.contains("/api/v1/");
    }

    /**
     * Get stored auth token from SharedPreferences
     */
    private static String getStoredAuthToken() {
        if (appContext == null) return null;

        SharedPreferences prefs = appContext.getSharedPreferences(
            MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(MainActivity.KEY_AUTH_TOKEN, null);
    }

    /**
     * Get stored auth token from SharedPreferences (public method)
     */
    public static String getStoredAuthToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
            MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(MainActivity.KEY_AUTH_TOKEN, null);
    }

    /**
     * Get stored user ID from SharedPreferences
     */
    public static String getStoredUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
            MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(MainActivity.KEY_ID_KARYAWAN, null);
    }

    /**
     * Store auth token in SharedPreferences
     */
    public static void storeAuthToken(Context context, String token) {
        SharedPreferences prefs = context.getSharedPreferences(
            MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(MainActivity.KEY_AUTH_TOKEN, token).apply();
        Log.d(TAG, "Auth token stored");
    }

    /**
     * Clear stored auth token
     */
    public static void clearAuthToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
            MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(MainActivity.KEY_AUTH_TOKEN).apply();
        Log.d(TAG, "Auth token cleared");
    }

    /**
     * Refresh authentication token synchronously
     */
    private static boolean refreshTokenSync() {
        if (isRefreshingToken || appContext == null) {
            return false;
        }

        isRefreshingToken = true;
        try {
            String currentToken = getStoredAuthToken();
            if (currentToken == null || currentToken.isEmpty()) {
                Log.d(TAG, "No token to refresh");
                return false;
            }

            // Create a new client without the interceptor to avoid infinite loop
            OkHttpClient refreshClient = new OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();

            Request refreshRequest = new Request.Builder()
                    .url(API_BASE_URL + "/v1/employee/refresh-token")
                    .post(new FormBody.Builder().build())
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + currentToken)
                    .build();

            try (Response response = refreshClient.newCall(refreshRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);

                    if (jsonResponse.getBoolean("success")) {
                        JSONObject data = jsonResponse.getJSONObject("data");
                        String newToken = data.getString("token");
                        storeAuthToken(appContext, newToken);
                        Log.d(TAG, "Token refreshed successfully");
                        return true;
                    }
                }
                Log.d(TAG, "Token refresh failed: " + response.code());
                return false;
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Token refresh error: " + e.getMessage());
            return false;
        } finally {
            isRefreshingToken = false;
        }
    }

    /**
     * Validate current token
     */
    public static boolean validateToken() {
        if (appContext == null) return false;

        String token = getStoredAuthToken();
        if (token == null || token.isEmpty()) {
            return false;
        }

        try {
            Request request = new Request.Builder()
                    .url(API_BASE_URL + "/v1/employee/validate-token")
                    .post(new FormBody.Builder().build())
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .build();

            try (Response response = getClient(appContext).newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    return jsonResponse.getBoolean("success");
                }
                return false;
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Token validation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if user is authenticated (has valid token)
     */
    public static boolean isAuthenticated() {
        String token = getStoredAuthToken();
        return token != null && !token.isEmpty();
    }

    // Optional helper to clear cookies (e.g., on logout)
    public static synchronized void clearCookies() {
        cookieStore.clear();
        Log.d(TAG, "Cookies cleared");
    }

    // Clear all authentication data
    public static synchronized void clearAllAuth(Context context) {
        clearCookies();
        clearAuthToken(context);
        Log.d(TAG, "All auth data cleared");
    }

    /**
     * Get work hours settings from API
     */
    public static JSONObject getWorkHours(Context context) throws IOException, JSONException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/v1/settings")
                .get()
                .header("Accept", "application/json")
                .build();

        try (Response response = getClient(context).newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                if (jsonResponse.getBoolean("success")) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    JSONObject workHours = data.getJSONObject("work_hours");
                    return workHours;
                } else {
                    throw new IOException("API returned error: " + jsonResponse.optString("message"));
                }
            } else {
                throw new IOException("HTTP error: " + response.code());
            }
        }
    }

    /**
     * Get server time from API
     */
    public static JSONObject getServerTime(Context context) throws IOException, JSONException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/v1/server-time")
                .get()
                .header("Accept", "application/json")
                .build();

        try (Response response = getClient(context).newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                if (jsonResponse.getBoolean("success")) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    return data;
                } else {
                    throw new IOException("API returned error: " + jsonResponse.optString("message"));
                }
            } else {
                throw new IOException("HTTP error: " + response.code());
            }
        }
    }

    /**
     * Get settings from API
     */
    public static JSONObject getSettings(Context context) throws IOException, JSONException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/v1/settings")
                .get()
                .header("Accept", "application/json")
                .build();

        try (Response response = getClient(context).newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                if (jsonResponse.getBoolean("success")) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    return data;
                } else {
                    throw new IOException("API returned error: " + jsonResponse.optString("message"));
                }
            } else {
                throw new IOException("HTTP error: " + response.code());
            }
        }
    }

    /**
     * Get user profile from API
     */
    public static JSONObject getUserProfile(Context context) throws IOException, JSONException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/v1/employee/profile")
                .get()
                .header("Accept", "application/json")
                .build();

        try (Response response = getClient(context).newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                if (jsonResponse.getBoolean("success")) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    return data;
                } else {
                    throw new IOException("API returned error: " + jsonResponse.optString("message"));
                }
            } else {
                throw new IOException("HTTP error: " + response.code());
            }
        }
    }

    /**
     * Get jam kerja settings from API
     */
    public static JSONObject getJamKerja(Context context) throws IOException, JSONException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/v1/jam-kerja")
                .get()
                .header("Accept", "application/json")
                .build();

        try (Response response = getClient(context).newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                if (jsonResponse.getBoolean("success")) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    return data;
                } else {
                    throw new IOException("API returned error: " + jsonResponse.optString("message"));
                }
            } else {
                throw new IOException("HTTP error: " + response.code());
            }
        }
    }
}
