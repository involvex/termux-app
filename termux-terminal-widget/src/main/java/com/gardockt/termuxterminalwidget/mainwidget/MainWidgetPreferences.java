package com.gardockt.termuxterminalwidget.mainwidget;

import androidx.annotation.NonNull;

import com.gardockt.termuxterminalwidget.ColorScheme;

public class MainWidgetPreferences {

    @NonNull
    private String command = "";
    private ColorScheme colorScheme = null;
    private Integer textSizeSp = null;
    private int refreshIntervalSecs = 15 * 60;

    public void setCommand(@NonNull String command) {
        this.command = command;
    }

    @NonNull
    public String getCommand() {
        return command;
    }

    public ColorScheme getColorScheme() {
        return colorScheme;
    }

    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }

    public Integer getTextSizeSp() {
        return textSizeSp;
    }

    public void setTextSizeSp(Integer textSizeSp) {
        this.textSizeSp = textSizeSp;
    }

    public int getRefreshIntervalSecs() {
        return refreshIntervalSecs;
    }

    public void setRefreshIntervalSecs(int refreshIntervalSecs) {
        this.refreshIntervalSecs = refreshIntervalSecs;
    }
}
