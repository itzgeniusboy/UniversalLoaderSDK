package com.onecore.sdk;

import com.onecore.sdk.utils.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Network Traffic Capture Engine for OneCore SDK Engine.
 * Intercepts and logs HTTP/HTTPS requests and responses from virtual apps.
 */
public class NetworkCapture {
    private static final String TAG = "NetworkCapture";
    private static NetworkCapture instance;
    private boolean isCapturing = false;
    private final List<NetworkLog> logs = new CopyOnWriteArrayList<>();

    public static class NetworkLog {
        public long timestamp;
        public String url;
        public String method;
        public String requestData;
        public String responseData;
        public int statusCode;

        public NetworkLog(String url, String method, String requestData) {
            this.timestamp = System.currentTimeMillis();
            this.url = url;
            this.method = method;
            this.requestData = requestData;
        }
    }

    private NetworkCapture() {}

    public static synchronized NetworkCapture getInstance() {
        if (instance == null) {
            instance = new NetworkCapture();
        }
        return instance;
    }

    public void startCapture() {
        isCapturing = true;
        Logger.i(TAG, "Network Capture started.");
    }

    public void stopCapture() {
        isCapturing = false;
        Logger.i(TAG, "Network Capture stopped.");
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public void logRequest(String url, String method, String data) {
        if (!isCapturing) return;
        logs.add(new NetworkLog(url, method, data));
        Logger.v(TAG, "Captured Request: " + method + " " + url);
    }

    public void logResponse(String url, int statusCode, String data) {
        if (!isCapturing) return;
        for (NetworkLog log : logs) {
            if (log.url.equals(url) && log.responseData == null) {
                log.statusCode = statusCode;
                log.responseData = data;
                break;
            }
        }
    }

    public List<NetworkLog> getRequestLog() {
        return new ArrayList<>(logs);
    }

    public String modifyRequest(String url, String newData) {
        Logger.d(TAG, "Modifying request for: " + url);
        // Logic: Search log or active stream and inject newData
        return newData;
    }

    public void clearLogs() {
        logs.clear();
    }
}
