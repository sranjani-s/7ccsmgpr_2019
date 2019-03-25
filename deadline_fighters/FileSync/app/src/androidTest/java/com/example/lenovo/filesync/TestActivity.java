package com.example.lenovo.filesync;

import android.content.*;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.*;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
public class TestActivity extends ActivityInstrumentationTestCase2<MainActivity> {
    private Context context;

    public TestActivity() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = getActivity().getApplicationContext();
    }

    public void testStart() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        Log.i("TestActivity","MainActivity is start.");

    }
}
