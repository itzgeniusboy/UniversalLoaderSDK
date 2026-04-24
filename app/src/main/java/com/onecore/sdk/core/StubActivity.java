package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import android.app.ActivityOptions;
import android.os.Build;

/**
 * Android 14 Sandbox StubActivity.
 * Acts as the entry point for guest applications within the virtual display.
 * Resolves the "Original game opens instead of clone" issue.
 */
public class StubActivity extends Activity {
    private static final String TAG = "OneCore-Stub";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The real work is now done by VAInstrumentation.newActivity 
        // which intercepts the creation of THIS activity instance 
        // and swaps the class to the target activity.
        
        // If we reach HERE, it means the class swiping failed or 
        // this is actually StubActivity itself.
        super.onCreate(savedInstanceState);
        
        Logger.w(TAG, "StubActivity created directly. Class swiping may have failed.");
        
        // Ensure UI is clean
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        finish();
    }

    @Override
    public android.content.res.Resources getResources() {
        android.content.res.Resources guestRes = VirtualContainer.getInstance().getGuestResources();
        return guestRes != null ? guestRes : super.getResources();
    }

    @Override
    public ClassLoader getClassLoader() {
        ClassLoader guestLoader = VirtualContainer.getInstance().getGuestClassLoader();
        return guestLoader != null ? guestLoader : super.getClassLoader();
    }
}
