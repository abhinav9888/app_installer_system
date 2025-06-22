package com.mikomini.appinstall.models;

import java.io.Serializable;
import java.time.LocalDateTime;

public class InstallationLog implements Serializable{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int id;
    private int appId;
    private App.State state;
    private LocalDateTime timestamp;
    private String errorMessage;
    private int retryAttempt;

    public InstallationLog(int id, int appId, App.State state, LocalDateTime timestamp, String errorMessage, int retryAttempt) {
        this.id = id;
        this.appId = appId;
        this.state = state;
        this.timestamp = timestamp;
        this.errorMessage = errorMessage;
        this.retryAttempt = retryAttempt;
    }

    public int getId() {
        return id;
    }

    public int getAppId() {
        return appId;
    }

    public App.State getState() {
        return state;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRetryAttempt() {
        return retryAttempt;
    }

    @Override
    public String toString() {
        return "InstallationLog{" +
               "id=" + id +
               ", appId=" + appId +
               ", state=" + state +
               ", timestamp=" + timestamp +
               ", errorMessage='" + errorMessage + '\'' +
               ", retryAttempt=" + retryAttempt +
               '}';
    }
}
