package com.onecore.loader;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;

public class StubActivity_P1 extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("OneCore-Stub", "StubActivity_P1 created");
    }
}
