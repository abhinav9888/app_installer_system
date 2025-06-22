package com.mikomini.appinstall.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class NotificationVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationVerticle.class);
    private MailClient mailClient; 
    private final MailConfig mailConfig;
    
    public NotificationVerticle(MailConfig mailConfig) {
    	this.mailConfig = mailConfig;
    }
    
    public NotificationVerticle() {
        this.mailConfig = null; 
    }

    @Override
    public void start(Promise<Void> startPromise) {
    	LOGGER.info("NotificationVerticle starting...");
        if (mailConfig == null || mailConfig.getHostname() == null) {
            LOGGER.error("Mail configuration is missing or incomplete. Email notifications will not work.");
            startPromise.fail("Mail configuration missing.");
            return;
        }
        mailClient = MailClient.createShared(vertx, mailConfig, "mikomini-mail-client"); 

        vertx.eventBus().consumer("notify.installation.failure", this::handleInstallationFailureNotification);
        startPromise.complete();
    }

    private void handleInstallationFailureNotification(Message<JsonObject> message) {
        JsonObject notificationData = message.body();
        String appName = notificationData.getString("appName");
        String error = notificationData.getString("error");
        int appId = notificationData.getInteger("appId");
        int retryAttempts = notificationData.getInteger("retryAttempts");

        String subject = String.format("Miko Mini App Installation Failed: %s", appName);
        String body = String.format(
        				"Hello,\n\n" +
        	            "An app installation for Miko Mini has failed after multiple retries.\n\n" +
        	            "App Name: %s\n" +
        	            "App ID: %d\n" +
        	            "Error Details: %s\n" +
        	            "Failed Attempts: %d\n" +
        	            "Timestamp: %s\n\n" +
        	            "Please investigate this issue immediately.\n\n" +
        	            "Regards,\n" +
        	            "Miko Mini App Installation System",
            appName, appId, error, retryAttempts, java.time.LocalDateTime.now().toString()
        );
        
        MailMessage emailMessage = new MailMessage()
                .setFrom("zaidmohd2356@gmail.com") 
                .setTo("abhinav22291@gmail.com") 
                .setSubject(subject)
                .setText(body);

            // Send the email
            mailClient.sendMail(emailMessage)
                .onSuccess(result -> {
                    LOGGER.info("Email notification sent successfully for app {}.", appName);
                    message.reply("Notification Sent: SUCCESS");
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to send email notification for app {}: {}", appName, err.getMessage(), err);
                    message.fail(500, "Notification Sent: FAILED - " + err.getMessage());
                });

        LOGGER.error("!!! SENDING EMAIL ALERT !!!");
        LOGGER.error("Subject: {}", subject);
        LOGGER.error("Body:\n{}", body);
        LOGGER.error("!!! EMAIL ALERT SENT !!!");

        message.reply("Notification Sent");
    }
    
    

    @Override
    public void stop(Promise<Void> stopPromise) {
        LOGGER.info("NotificationVerticle stopping...");
        stopPromise.complete();
    }
}
