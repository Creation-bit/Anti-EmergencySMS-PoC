package dev.goyo.alertcontrol;

import android.app.Application;

public final class GoyoApp extends Application {
    ControlModel model;
    @Override public void onCreate() {
        super.onCreate();
        model = new ControlModel(this);
    }
}
