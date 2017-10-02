package com.freeportmetrics.nearby.messages;

import android.databinding.BaseObservable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by skamycki on 25/09/2017.
 */

public class NearbyState extends BaseObservable {

    private boolean subscribed;

    private boolean publishing;

    private boolean supported;

    private final List<String> logs;

    private boolean earshotDistance;

    public NearbyState() {
        logs = new ArrayList<>();
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

    public boolean isPublishing() {
        return publishing;
    }

    public void setPublishing(boolean publishing) {
        this.publishing = publishing;
    }

    public void addLog(String log) {
        logs.add(log);
    }

    public boolean isSupported() {
        return supported;
    }

    public void setSupported(boolean supported) {
        this.supported = supported;
    }

    public String getLogText() {
        return TextUtils.join(System.lineSeparator(), logs);
    }

    public boolean isEarshotDistance() {
        return earshotDistance;
    }

    public void setEarshotDistance(boolean earshotDistance) {
        this.earshotDistance = earshotDistance;
    }
}
