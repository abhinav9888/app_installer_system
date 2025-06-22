package com.mikomini.appinstall.verticles;

import com.mikomini.appinstall.models.App;
import com.mikomini.appinstall.models.InstallationLog;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.*;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DatabaseVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseVerticle.class);

    private Pool dbClient;

    public DatabaseVerticle(Pool dbClient) {
        this.dbClient = dbClient;
    }

    public DatabaseVerticle() {
    }

    @Override
    public void start(Promise<Void> startPromise) {
        LOGGER.info("DatabaseVerticle starting...");

        if (dbClient == null) {
        	LOGGER.warn("dbClient not injected. Trying to create from config...");
        	if (config().containsKey("dbClientConfig")) {
                JsonObject dbConfig = config().getJsonObject("dbClientConfig");
                MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                    .setHost(dbConfig.getString("host", "localhost"))
                    .setPort(dbConfig.getInteger("port", 3306))
                    .setDatabase(dbConfig.getString("mikomini_app_db"))
                    .setUser(dbConfig.getString("root"))
                    .setPassword(dbConfig.getString("Abhinav@91"));
                PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
                dbClient = Pool.pool(vertx, connectOptions, poolOptions);
            } else {
                LOGGER.error("MySQL config not found. Unable to create dbClient.");
                startPromise.fail("MySQLPool not initialized.");
                return;
            }
            }
        // Event Bus Consumers
        vertx.eventBus().consumer("db.get.scheduled.apps", this::handleGetScheduledApps);
        vertx.eventBus().consumer("db.update.app.state", this::handleUpdateAppState);
        vertx.eventBus().consumer("db.log.installation.event", this::handleLogInstallationEvent);
        vertx.eventBus().consumer("db.get.app.by.id", this::handleGetAppById);
        vertx.eventBus().consumer("db.get.installation.logs", this::handleGetInstallationLogs);
        vertx.eventBus().consumer("db.get.apps.in.error.state", this::handleGetAppsInErrorState);
        vertx.eventBus().consumer("db.get.new.app.versions", this::handleGetNewAppVersions);
        vertx.eventBus().consumer("db.clear.data", this::handleClearData);
        vertx.eventBus().consumer("db.insert.app", this::handleInsertApp); // New consumer for inserting app
        vertx.eventBus().consumer("db.get.all.installation.logs", this::handleGetAllInstallationLogs); // New consumer for getting all logs


        startPromise.complete();
    }

    private void handleGetScheduledApps(Message<Void> message) {
        dbClient.query("SELECT id, name, version, package_url, current_state FROM apps WHERE current_state = 'SCHEDULED' LIMIT 1")
            .execute()
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    App app = rowToApp(row);
                    LOGGER.debug("Found scheduled app: {}", app.getName());
                    message.reply(JsonObject.mapFrom(app));
                } else {
                    LOGGER.debug("No scheduled apps found.");
                    message.reply(new JsonObject()); 
                }
            })
            .onFailure(err -> {
                LOGGER.error("Failed to get scheduled apps: {}", err.getMessage());
                message.fail(500, "Failed to get scheduled apps: " + err.getMessage());
            });
    }

    private void handleUpdateAppState(Message<JsonObject> message) {
        JsonObject appData = message.body();
        int appId = appData.getInteger("id");
        String newState = appData.getString("newState");
        String oldState = appData.getString("oldState"); 

        dbClient.preparedQuery("UPDATE apps SET current_state = ?, last_updated_at = NOW() WHERE id = ? AND current_state = ?")
            .execute(Tuple.of(newState, appId, oldState))
            .onSuccess(updateResult -> {
                if (updateResult.rowCount() > 0) {
                    LOGGER.info("App {} state updated from {} to {}", appId, oldState, newState);
                    message.reply("SUCCESS");
                } else {
                    LOGGER.warn("App {} state not updated. Maybe state already changed or app not found.", appId);
                    message.fail(404, "App state not updated, possibly due to a conflict or not found.");
                }
            })
            .onFailure(err -> {
                LOGGER.error("Failed to update app {} state to {}: {}", appId, newState, err.getMessage());
                message.fail(500, "Failed to update app state: " + err.getMessage());
            });
    }

    private void handleLogInstallationEvent(Message<JsonObject> message) {
        JsonObject logData = message.body();
        int appId = logData.getInteger("appId");
        String state = logData.getString("state");
        String errorMessage = logData.getString("errorMessage");
        int retryAttempt = logData.getInteger("retryAttempt", 0); 

        dbClient.preparedQuery("INSERT INTO installation_logs (app_id, state, error_message, retry_attempt) VALUES (?, ?, ?, ?)")
            .execute(Tuple.of(appId, state, errorMessage, retryAttempt))
            .onSuccess(rows -> {
                LOGGER.info("Installation event logged for app {}: State={}, Retry={}", appId, state, retryAttempt);
                message.reply("LOGGED");
            })
            .onFailure(err -> {
                LOGGER.error("Failed to log installation event for app {}: {}", appId, err.getMessage());
                message.fail(500, "Failed to log event: " + err.getMessage());
            });
    }

    private void handleGetAppById(Message<Integer> message) {
        int appId = message.body();
        dbClient.preparedQuery("SELECT id, name, version, package_url, current_state FROM apps WHERE id = ?")
            .execute(Tuple.of(appId))
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    App app = rowToApp(rows.iterator().next());
                    message.reply(JsonObject.mapFrom(app));
                } else {
                    LOGGER.warn("App with ID {} not found.", appId);
                    message.fail(404, "App not found.");
                }
            })
            .onFailure(err -> {
                LOGGER.error("Failed to get app by ID {}: {}", appId, err.getMessage());
                message.fail(500, "Failed to get app: " + err.getMessage());
            });
    }

    private void handleGetInstallationLogs(Message<Integer> message) {
        int appId = message.body();
        dbClient.preparedQuery("SELECT id, app_id, state, timestamp, error_message, retry_attempt FROM installation_logs WHERE app_id = ? ORDER BY timestamp DESC")
            .execute(Tuple.of(appId))
            .onSuccess(rows -> {
                List<InstallationLog> logs = new ArrayList<>();
                rows.forEach(row -> logs.add(rowToInstallationLog(row)));
                message.reply(new JsonArray(logs.stream().map(JsonObject::mapFrom).collect(Collectors.toList())));
            })
            .onFailure(err -> {
                LOGGER.error("Failed to get installation logs for app {}: {}", appId, err.getMessage());
                message.fail(500, "Failed to get logs: " + err.getMessage());
            });
    }
    
 // New method to get ALL installation logs
    private void handleGetAllInstallationLogs(Message<Void> message) {
        dbClient.query("SELECT id, app_id, state, timestamp, error_message, retry_attempt FROM installation_logs ORDER BY timestamp DESC")
            .execute()
            .onSuccess(rows -> {
                List<InstallationLog> logs = new ArrayList<>();
                rows.forEach(row -> logs.add(rowToInstallationLog(row)));
                LOGGER.debug("Found {} total installation logs.", logs.size());
                message.reply(new JsonArray(logs.stream().map(JsonObject::mapFrom).collect(Collectors.toList())));
            })
            .onFailure(err -> {
                LOGGER.error("Failed to get all installation logs: {}", err.getMessage());
                message.fail(500, "Failed to get all logs: " + err.getMessage());
            });
    }

    private void handleGetAppsInErrorState(Message<Void> message) {
        dbClient.query("SELECT id, name, version, package_url, current_state FROM apps WHERE current_state = 'ERROR' LIMIT 10") // Limit to avoid fetching too many at once
            .execute()
            .onSuccess(rows -> {
                List<App> apps = new ArrayList<>();
                rows.forEach(row -> apps.add(rowToApp(row)));
                LOGGER.debug("Found {} apps in ERROR state.", apps.size());
                message.reply(new JsonArray(apps.stream().map(JsonObject::mapFrom).collect(Collectors.toList())));
            })
            .onFailure(err -> {
                LOGGER.error("Failed to get apps in error state: {}", err.getMessage());
                message.fail(500, "Failed to get apps in error state: " + err.getMessage());
            });
    }

    private void handleGetNewAppVersions(Message<Void> message) {
        dbClient.query("SELECT a.id, a.name, a.version, a.package_url, a.current_state FROM apps a WHERE a.current_state = 'COMPLETED' AND EXISTS (SELECT 1 FROM apps al WHERE al.name = a.name AND al.version > a.version)")
            .execute()
            .onSuccess(rows -> {
                List<App> newVersions = new ArrayList<>();
                rows.forEach(row -> newVersions.add(rowToApp(row)));
                LOGGER.debug("Found {} apps with new versions available.", newVersions.size());
                message.reply(new JsonArray(newVersions.stream().map(JsonObject::mapFrom).collect(Collectors.toList())));
            })
            .onFailure(err -> {
                LOGGER.error("Failed to get new app versions: {}", err.getMessage());
                message.fail(500, "Failed to get new app versions: " + err.getMessage());
            });
    }
    
    private void handleClearData(Message<Void> message) {
        LOGGER.info("Received request to clear database tables.");
        dbClient.getConnection()
            .compose(conn -> conn.query("TRUNCATE TABLE `installation_logs`").execute() // Truncate logs first
                .compose(res1 -> conn.query("TRUNCATE TABLE `analytics`").execute()) // Then analytics
                .compose(res2 -> conn.query("TRUNCATE TABLE `apps`").execute()) // Finally apps
                .eventually(v -> conn.close()) // Close connection in any case
            )
            .onSuccess(v -> {
                LOGGER.info("Successfully truncated all relevant tables.");
                message.reply("Database cleared.");
            })
            .onFailure(err -> {
                LOGGER.error("Failed to truncate tables: {}", err.getMessage(), err);
                message.fail(500, "Failed to clear database: " + err.getMessage());
            });
    }

    /**
     * Handles event bus messages to insert a new app into the 'apps' table.
     * @param message The event bus message containing app data (name, version, package_url).
     */
    private void handleInsertApp(Message<JsonObject> message) {
        JsonObject appData = message.body();
        String name = appData.getString("name");
        String version = appData.getString("version");
        String packageUrl = appData.getString("package_url");

        dbClient.preparedQuery("INSERT INTO apps (name, version, package_url, current_state) VALUES (?, ?, ?, 'SCHEDULED')")
            .execute(Tuple.of(name, version, packageUrl))
            .onSuccess(result -> {
                Long insertedId = result.property(MySQLClient.LAST_INSERTED_ID);
                LOGGER.info("App inserted successfully with ID: {}", insertedId);
                message.reply(insertedId); // Reply with the ID of the newly inserted app
            })
            .onFailure(err -> {
                LOGGER.error("Failed to insert app {}: {}", name, err.getMessage());
                message.fail(500, "Failed to insert app: " + err.getMessage());
            });
    }



    private App rowToApp(Row row) {
        return new App(
            row.getInteger("id"),
            row.getString("name"),
            row.getString("version"),
            row.getString("package_url"),
            App.State.valueOf(row.getString("current_state"))
        );
    }

    private InstallationLog rowToInstallationLog(Row row) {
        return new InstallationLog(
            row.getInteger("id"),
            row.getInteger("app_id"),
            App.State.valueOf(row.getString("state")),
            row.getLocalDateTime("timestamp"),
            row.getString("error_message"),
            row.getInteger("retry_attempt")
        );
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        LOGGER.info("DatabaseVerticle stopping...");
        if (dbClient != null) {
            dbClient.close();
            LOGGER.info("MySQLPool closed.");
        }
        stopPromise.complete();
    }
}