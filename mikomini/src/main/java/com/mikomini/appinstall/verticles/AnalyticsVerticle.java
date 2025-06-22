package com.mikomini.appinstall.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalyticsVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        LOGGER.info("AnalyticsVerticle starting...");
        vertx.eventBus().consumer("analytics.get.app.logs", this::handleGetAppLogs);
        startPromise.complete();
    }

    private void handleGetAppLogs(Message<JsonObject> message) {
        int appId = message.body().getInteger("appId");
        LOGGER.info("Received request for analytics logs for App ID: {}", appId);

        vertx.eventBus().request("db.get.installation.logs", appId)
            .onSuccess(reply -> {
                JsonArray logs = (JsonArray) reply.body();
                LOGGER.info("Returning {} installation logs for App ID: {}", logs.size(), appId);
                message.reply(logs);
            })
            .onFailure(err -> {
                LOGGER.error("Failed to retrieve analytics logs for App ID {}: {}", appId, err.getMessage());
                message.fail(500, "Failed to retrieve analytics logs: " + err.getMessage());
            });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        LOGGER.info("AnalyticsVerticle stopping...");
        stopPromise.complete();
    }
}