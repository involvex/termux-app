package com.gardockt.termuxterminalwidget;

import android.app.Application;

import com.gardockt.termuxterminalwidget.mainwidget.MainWidget;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        MainWidget.setup(this);
        super.onCreate();
    }
}
