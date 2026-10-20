package com.kantoi.app;

import android.app.Application;
import android.util.Log;

public class KantoiApp extends Application {
    private static final String TAG = "KantoiApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "=== Initializing KANTOI Anti-Scam Protection Engine v1.0 ===");
        KantoiThreatEngine.init(this);
        KantoiDownloadSentry.startWatching(this);
    }
}
