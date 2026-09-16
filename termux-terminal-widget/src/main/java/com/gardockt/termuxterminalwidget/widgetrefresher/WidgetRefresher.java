package com.gardockt.termuxterminalwidget.widgetrefresher;

public interface WidgetRefresher {

    /**
     * Sets up widget to be refreshed with given interval. If the widget is already set up, no
     * action occurs.
     */
    void add(int widgetId, long intervalSecs);

    /**
     * Removes widget from refresher. If given widget is not controlled by that refresher, no action
     * occurs.
     */
    void remove(int widgetId);

    /**
     * @return True if widgets, once configured to be refreshed by given implementation, do not need
     * to be re-added across app launches.
     */
    boolean isPersistent();
}
