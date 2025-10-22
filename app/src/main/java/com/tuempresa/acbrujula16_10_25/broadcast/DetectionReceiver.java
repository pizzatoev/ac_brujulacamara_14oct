package com.tuempresa.acbrujula16_10_25.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DetectionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("DetectionReceiver", "Broadcast recibido");
    }
}
