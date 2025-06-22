package com.mikomini.appinstall.models;

import java.time.LocalDateTime;

public class App {
    private int id;
    private String name;
    private String version;
    private String packageUrl;
    private State currentState;
    private LocalDateTime lastUpdatedAt;
    private LocalDateTime createdAt;

    public enum State {
        SCHEDULED,
        PICKEDUP,
        ERROR,
        COMPLETED
    }
    
    public App() {
    }

    public App(int id, String name, String version, String packageUrl, State currentState) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.packageUrl = packageUrl;
        this.currentState = currentState;
    }

    public App(String name, String version, String packageUrl) {
        this.name = name;
        this.version = version;
        this.packageUrl = packageUrl;
        this.currentState = State.SCHEDULED;
        this.createdAt = LocalDateTime.now();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackageUrl() {
        return packageUrl;
    }

    public void setPackageUrl(String packageUrl) {
        this.packageUrl = packageUrl;
    }

    public State getCurrentState() {
        return currentState;
    }

    public void setCurrentState(State currentState) {
        this.currentState = currentState;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "App{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", version='" + version + '\'' +
               ", packageUrl='" + packageUrl + '\'' +
               ", currentState=" + currentState +
               ", lastUpdatedAt=" + lastUpdatedAt +
               ", createdAt=" + createdAt +
               '}';
    }
}