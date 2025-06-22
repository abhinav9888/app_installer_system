package com.mikomini.appinstall.verticles;

import com.mikomini.appinstall.models.App;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AppInstallationVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppInstallationVerticle.class);
    private static final int MAX_RETRIES = 3;
    private boolean isInstalling = false; 
    private ConcurrentHashMap<Integer, Integer> appRetryCounts = new ConcurrentHashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {
        LOGGER.info("AppInstallationVerticle starting...");

        vertx.setPeriodic(5000, id -> checkForScheduledApps());
        vertx.setPeriodic(30000, id -> checkForNewAppVersionsAndErrorApps());

        startPromise.complete();
    }

    private void checkForScheduledApps() {
        if (isInstalling) {
            LOGGER.debug("An app is currently being installed. Skipping check for new scheduled apps.");
            return;
        }

        LOGGER.info("Checking for scheduled apps...");
        vertx.eventBus().request("db.get.scheduled.apps", null, new DeliveryOptions().setSendTimeout(5000))
            .onSuccess(msg -> {
                JsonObject appJson = (JsonObject) msg.body();
                if (appJson != null && !appJson.isEmpty()) {
                    App app = appJson.mapTo(App.class);
                    LOGGER.info("Picked up scheduled app: {}", app.getName());
                    isInstalling = true; 
                    installApp(app);
                } else {
                    LOGGER.debug("No new apps to schedule.");
                }
            })
            .onFailure(err -> LOGGER.error("Failed to get scheduled apps from DB: {}", err.getMessage()));
    }

    private void installApp(App app) {
        JsonObject updateData = new JsonObject()
            .put("id", app.getId())
            .put("newState", App.State.PICKEDUP.name())
            .put("oldState", App.State.SCHEDULED.name()); 

        vertx.eventBus().request("db.update.app.state", updateData)
            .onSuccess(reply -> {
                if ("SUCCESS".equals(reply.body())) {
                    LOGGER.info("App {} state updated to PICKEDUP.", app.getName());
                    logInstallationEvent(app.getId(), App.State.PICKEDUP, null, appRetryCounts.getOrDefault(app.getId(), 0));

                 // Simulate installation process
                    int currentRetries = appRetryCounts.getOrDefault(app.getId(), 0);
                    int successRate;

                    // --- Scenario 2 Testing Logic ---
                    // Force first two attempts to fail, third to succeed for specific app name
                    if (app.getName().equals("MikoBuggy")) { // Use this for Scenario 2
                        if (currentRetries < 2) { // 0 and 1 retries (1st and 2nd attempt)
                            successRate = 0; // Force failure
                        } else { // 2 retries (3rd attempt)
                            successRate = 100; // Force success
                        }
                    }
                    // --- Scenario 3 Testing Logic ---
                    // Force all attempts to fail for specific app name (for email notification test)
                    else if (app.getName().equals("MikoFailed")) { // Use this for Scenario 3
                         successRate = 0; // Always force failure
                    }
                    // --- Default behavior ---
                    else {
                        successRate = 80; // Default 80% success rate for other apps
                    }

                    // Simulate installation process
                    vertx.setTimer(ThreadLocalRandom.current().nextLong(2000, 7000), timerId -> { 
                        if (ThreadLocalRandom.current().nextInt(100) < successRate) { // 80% success rate
                            handleInstallationSuccess(app);
                        } else {
                            handleInstallationFailure(app, "Simulated installation error.");
                        }
                    });
                }
//            	For Testing succsess 3rd attempt   
                
//                if ("SUCCESS".equals(reply.body())) {
//                    LOGGER.info("App {} state updated to PICKEDUP.", app.getName());
//                    logInstallationEvent(app.getId(), App.State.PICKEDUP, null, appRetryCounts.getOrDefault(app.getId(), 0));
//
//                    int currentRetries = appRetryCounts.getOrDefault(app.getId(), 0);
//                    int successRate;
//                    if (currentRetries < 2) { // For 0 and 1 retries (first and second attempts)
//                        successRate = 0; // Force failure
//                    } else { // For 2 retries (third attempt) and beyond
//                        successRate = 100; // Force success
//                    }
//
//                    vertx.setTimer(ThreadLocalRandom.current().nextLong(2000, 7000), timerId -> { // Simulate 2-7 seconds install time
//                        if (ThreadLocalRandom.current().nextInt(100) < successRate) {
//                            handleInstallationSuccess(app);
//                        } else {
//                            handleInstallationFailure(app, "Simulated installation error during " + (currentRetries + 1) + " attempt.");
//                        }
//                    });
//                }
            	
//            	For Testing Mail Notification after 3 attempt fails
                
//            	if ("SUCCESS".equals(reply.body())) {
//                    LOGGER.info("App {} state updated to PICKEDUP.", app.getName());
//                    logInstallationEvent(app.getId(), App.State.PICKEDUP, null, appRetryCounts.getOrDefault(app.getId(), 0));
//
//                    int currentRetries = appRetryCounts.getOrDefault(app.getId(), 0);
//                    int successRate = 0;
//
//                    vertx.setTimer(ThreadLocalRandom.current().nextLong(2000, 7000), timerId -> { // Simulate 2-7 seconds install time
//                        if (ThreadLocalRandom.current().nextInt(100) < successRate) {
//                            handleInstallationSuccess(app);
//                        } else {
//                            handleInstallationFailure(app, "Simulated installation error during " + (currentRetries + 1) + " attempt.");
//                        }
//                    });
//                }
            	
                
                else {
                    LOGGER.warn("Failed to set app {} to PICKEDUP. Re-queueing for next cycle.", app.getName());
                    isInstalling = false; 
                }
            })
            .onFailure(err -> {
                LOGGER.error("Failed to update app {} to PICKEDUP state in DB: {}", app.getName(), err.getMessage());
                isInstalling = false; 
            });
    }

    private void handleInstallationSuccess(App app) {
        LOGGER.info("App {} installation COMPLETED.", app.getName());
        JsonObject updateData = new JsonObject()
            .put("id", app.getId())
            .put("newState", App.State.COMPLETED.name())
            .put("oldState", App.State.PICKEDUP.name());

        vertx.eventBus().request("db.update.app.state", updateData)
            .onSuccess(reply -> {
                logInstallationEvent(app.getId(), App.State.COMPLETED, null, appRetryCounts.getOrDefault(app.getId(), 0));
                appRetryCounts.remove(app.getId()); 
                isInstalling = false; 
            })
            .onFailure(err -> {
                LOGGER.error("Failed to set app {} to COMPLETED state in DB: {}", app.getName(), err.getMessage());
                isInstalling = false; 
            });
    }

    private void handleInstallationFailure(App app, String errorMessage) {
        LOGGER.warn("App {} installation FAILED: {}", app.getName(), errorMessage);

        int currentRetries = appRetryCounts.compute(app.getId(), (k, v) -> (v == null) ? 1 : v + 1);

        logInstallationEvent(app.getId(), App.State.ERROR, errorMessage, currentRetries);

        if (currentRetries < MAX_RETRIES) {
            LOGGER.info("App {} will be retried (Attempt {}/{})", app.getName(), currentRetries, MAX_RETRIES);
            JsonObject updateData = new JsonObject()
                .put("id", app.getId())
                .put("newState", App.State.SCHEDULED.name())
                .put("oldState", App.State.PICKEDUP.name());

            vertx.eventBus().request("db.update.app.state", updateData)
                .onSuccess(reply -> {
                    LOGGER.info("App {} re-scheduled for retry.", app.getName());
                    isInstalling = false; 
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to re-schedule app {} for retry: {}", app.getName(), err.getMessage());
                    isInstalling = false; 
                });
        } else {
            LOGGER.error("App {} failed after {} retries. Sending notification.", app.getName(), MAX_RETRIES);
            JsonObject updateData = new JsonObject()
                .put("id", app.getId())
                .put("newState", App.State.ERROR.name()) 
                .put("oldState", App.State.PICKEDUP.name());

            vertx.eventBus().request("db.update.app.state", updateData)
                .onSuccess(reply -> {
                    // Send notification
                    JsonObject notificationData = new JsonObject()
                        .put("appName", app.getName())
                        .put("error", errorMessage)
                        .put("appId", app.getId())
                        .put("retryAttempts", currentRetries);
                    vertx.eventBus().send("notify.installation.failure", notificationData);
                    isInstalling = false; 
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to set app {} to ERROR state after max retries: {}", app.getName(), err.getMessage());
                    isInstalling = false; 
                });
        }
    }

    private void logInstallationEvent(int appId, App.State state, String errorMessage, int retryAttempt) {
        JsonObject logData = new JsonObject()
            .put("appId", appId)
            .put("state", state.name())
            .put("errorMessage", errorMessage)
            .put("retryAttempt", retryAttempt);

        vertx.eventBus().send("db.log.installation.event", logData);
    }

    private void checkForNewAppVersionsAndErrorApps() {
        vertx.eventBus().request("db.get.new.app.versions", null, new DeliveryOptions().setSendTimeout(5000))
            .onSuccess(msg -> {
                JsonArray appsWithNewVersions = (JsonArray) msg.body();
                if (!appsWithNewVersions.isEmpty()) {
                    LOGGER.info("Found {} apps with new versions. Scheduling for update.", appsWithNewVersions.size());
                    appsWithNewVersions.stream()
                        .map(obj -> ((JsonObject)obj).mapTo(App.class))
                        .forEach(app -> {
                            JsonObject updateData = new JsonObject()
                                .put("id", app.getId())
                                .put("newState", App.State.SCHEDULED.name())
                                .put("oldState", app.getCurrentState().name());
                            vertx.eventBus().send("db.update.app.state", updateData);
                            LOGGER.info("App {} (version {}) scheduled for update.", app.getName(), app.getVersion());
                        });
                }
            })
            .onFailure(err -> LOGGER.error("Failed to check for new app versions: {}", err.getMessage()));

        vertx.eventBus().request("db.get.apps.in.error.state", null, new DeliveryOptions().setSendTimeout(5000))
            .onSuccess(msg -> {
                JsonArray errorApps = (JsonArray) msg.body();
                if (!errorApps.isEmpty()) {
                    LOGGER.info("Found {} apps in ERROR state. Rescheduling some.", errorApps.size());
                    JsonObject appJson = errorApps.getJsonObject(0); 
                    App app = appJson.mapTo(App.class);

                    appRetryCounts.put(app.getId(), 0);

                    JsonObject updateData = new JsonObject()
                        .put("id", app.getId())
                        .put("newState", App.State.SCHEDULED.name())
                        .put("oldState", App.State.ERROR.name());

                    vertx.eventBus().send("db.update.app.state", updateData);
                    LOGGER.info("App {} (id: {}) in ERROR state re-scheduled for re-installation.", app.getName(), app.getId());
                }
            })
            .onFailure(err -> LOGGER.error("Failed to check for apps in ERROR state: {}", err.getMessage()));
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        LOGGER.info("AppInstallationVerticle stopping...");
        stopPromise.complete();
    }
}
