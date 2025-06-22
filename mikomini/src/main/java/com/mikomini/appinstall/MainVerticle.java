package com.mikomini.appinstall;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mikomini.appinstall.verticles.AnalyticsVerticle;
import com.mikomini.appinstall.verticles.AppInstallationVerticle;
import com.mikomini.appinstall.verticles.DatabaseVerticle;
import com.mikomini.appinstall.verticles.NotificationVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);
    private HttpServer httpServer;

    @Override
    public void start(Promise<Void> startPromise) {
        LOGGER.info("MainVerticle starting...");
        
     // --- NEW: Configure Jackson for Java 8 Date/Time support ---
        // This must be done before any JSON serialization/deserialization involving LocalDateTime
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
        DatabindCodec.mapper()
             .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
             .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        LOGGER.info("Jackson JavaTimeModule registered and date serialization configured.");
        // --- END NEW CONFIGURATION ---

        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
            .setPort(3306)
            .setHost("localhost")
            .setDatabase("mikomini_app_db")
            .setUser("root")
            .setPassword("Abhinav@91"); 

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        Pool dbClient = MySQLPool.pool(vertx, connectOptions, poolOptions);
        
        MailConfig mailConfig = new MailConfig()
                .setHostname("smtp.gmail.com")
                .setPort(587)
                .setAuthMethods("PLAIN") 
                .setTrustAll(true) 
                .setStarttls(StartTLSOptions.REQUIRED) 
                .setUsername("zaidmohd2356@gmail.com") 
                .setPassword("ywrtrllegbxvlhnl");

        vertx.deployVerticle(new DatabaseVerticle(dbClient))
            .onSuccess(dbId -> {
                LOGGER.info("DatabaseVerticle deployed with ID: {}", dbId);
                vertx.deployVerticle(new AppInstallationVerticle())
                    .onSuccess(appInstallId -> {
                        LOGGER.info("AppInstallationVerticle deployed with ID: {}", appInstallId);
                        vertx.deployVerticle(new NotificationVerticle(mailConfig))
                        .onSuccess(notificationId -> {
                            LOGGER.info("NotificationVerticle deployed with ID: {}", notificationId);
                            vertx.deployVerticle(new AnalyticsVerticle()) // Deploy AnalyticsVerticle
                            .onSuccess(analyticsId -> {
                                LOGGER.info("AnalyticsVerticle deployed with ID: {}", analyticsId);
                                LOGGER.info("All core Verticles deployed successfully!");

                                // --- Setup HTTP API for Analytics and Admin Operations ---
                                Router router = Router.router(vertx);

                                // Enable BodyHandler for parsing request bodies (for POST requests)
                                router.route().handler(BodyHandler.create());

                                // API to get installation logs for a specific app ID
                                // Example: GET /api/analytics/apps/1/logs
                                router.get("/api/analytics/apps/:appId/logs").handler(this::handleGetAppLogsApi);

                                // API to clear all data (admin operation)
                                // Example: POST /api/admin/clear-data
                                router.post("/api/admin/clear-data").handler(this::handleClearDataApi);

                                // New: API to insert data in the 'apps' table
                                // Example: POST /api/apps
                                router.post("/api/apps").handler(this::handleInsertAppApi);

                                // New: API to get all data from 'installation_logs' table
                                // Example: GET /api/installation-logs
                                router.get("/api/installation-logs").handler(this::handleGetAllInstallationLogsApi);


                                // Create the HTTP server
                                httpServer = vertx.createHttpServer();
                                httpServer.requestHandler(router)
                                    .listen(8080)
                                    .onSuccess(server -> {
                                        LOGGER.info("HTTP server started on port {}", server.actualPort());
                                        startPromise.complete();
                                    })
                                    .onFailure(err -> {
                                        LOGGER.error("Failed to start HTTP server: {}", err.getMessage());
                                        startPromise.fail(err);
                                    });

                            })
                            .onFailure(startPromise::fail);
                    })
                    .onFailure(startPromise::fail);
            })
            .onFailure(startPromise::fail);
    })
    .onFailure(startPromise::fail);
}

/**
* Handles HTTP GET requests for /api/analytics/apps/:appId/logs
* Retrieves installation logs for a specific app ID via the event bus and returns them as JSON.
* @param routingContext The routing context for the HTTP request.
*/
private void handleGetAppLogsApi(RoutingContext routingContext) {
String appIdStr = routingContext.request().getParam("appId");
if (appIdStr == null) {
    routingContext.response()
        .setStatusCode(400)
        .putHeader("content-type", "application/json")
        .end(new JsonObject().put("error", "App ID is required.").encodePrettily());
    return;
}

try {
    int appId = Integer.parseInt(appIdStr);

    // Send a request to the AnalyticsVerticle via the event bus
    vertx.eventBus().request("analytics.get.app.logs", new JsonObject().put("appId", appId))
        .onSuccess(message -> {
            // AnalyticsVerticle returns a JsonArray of logs
            JsonArray logs = (JsonArray) message.body();
            routingContext.response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(logs.encodePrettily());
        })
        .onFailure(err -> {
            LOGGER.error("API Error: Failed to retrieve analytics logs for App ID {}: {}", appId, err.getMessage());
            routingContext.response()
                .setStatusCode(500)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("error", "Internal server error retrieving logs.").encodePrettily());
        });
} catch (NumberFormatException e) {
    routingContext.response()
        .setStatusCode(400)
        .putHeader("content-type", "application/json")
        .end(new JsonObject().put("error", "Invalid App ID format.").encodePrettily());
}
}

/**
* Handles HTTP POST requests for /api/admin/clear-data
* Triggers the database truncation by sending an event bus message to the DatabaseVerticle.
* This endpoint should be protected in a production environment.
* @param routingContext The routing context for the HTTP request.
*/
private void handleClearDataApi(RoutingContext routingContext) {
LOGGER.info("Received request to clear all database data.");
vertx.eventBus().request("db.clear.data", null) // Send a message to the DatabaseVerticle
    .onSuccess(reply -> {
        LOGGER.info("Database clear initiated successfully: {}", reply.body());
        routingContext.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("message", "Database cleared successfully.").encodePrettily());
    })
    .onFailure(err -> {
        LOGGER.error("API Error: Failed to clear database: {}", err.getMessage());
        routingContext.response()
            .setStatusCode(500)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("error", "Failed to clear database: " + err.getMessage()).encodePrettily());
    });
}

/**
* Handles HTTP POST requests for /api/apps to insert a new app.
* Expects a JSON body with 'name', 'version', and 'package_url'.
* @param routingContext The routing context for the HTTP request.
*/
private void handleInsertAppApi(RoutingContext routingContext) {
JsonObject appData = routingContext.body().asJsonObject();
if (appData == null || !appData.containsKey("name") || !appData.containsKey("version") || !appData.containsKey("package_url")) {
    routingContext.response()
        .setStatusCode(400)
        .putHeader("content-type", "application/json")
        .end(new JsonObject().put("error", "Missing required fields: name, version, package_url.").encodePrettily());
    return;
}

LOGGER.info("Received request to insert new app: {}", appData.getString("name"));
vertx.eventBus().request("db.insert.app", appData)
    .onSuccess(reply -> {
        LOGGER.info("App inserted successfully: {}", reply.body());
        routingContext.response()
            .setStatusCode(201) // Created
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("message", "App created successfully.").put("appId", reply.body()).encodePrettily());
    })
    .onFailure(err -> {
        LOGGER.error("API Error: Failed to insert app: {}", err.getMessage());
        routingContext.response()
            .setStatusCode(500)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("error", "Failed to insert app: " + err.getMessage()).encodePrettily());
    });
}

/**
* Handles HTTP GET requests for /api/installation-logs to get all logs.
* @param routingContext The routing context for the HTTP request.
*/
private void handleGetAllInstallationLogsApi(RoutingContext routingContext) {
LOGGER.info("Received request to get all installation logs.");
vertx.eventBus().request("db.get.all.installation.logs", null)
    .onSuccess(reply -> {
        JsonArray logs = (JsonArray) reply.body();
        LOGGER.info("Returning {} all installation logs.", logs.size());
        routingContext.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(logs.encodePrettily());
    })
    .onFailure(err -> {
        LOGGER.error("API Error: Failed to get all installation logs: {}", err.getMessage());
        routingContext.response()
            .setStatusCode(500)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("error", "Failed to get all installation logs: " + err.getMessage()).encodePrettily());
    });
}

    @Override
    public void stop(Promise<Void> stopPromise) {
        LOGGER.info("MainVerticle stopping...");
        stopPromise.complete();
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle());
    }
}