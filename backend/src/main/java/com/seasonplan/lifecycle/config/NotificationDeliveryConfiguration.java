package com.seasonplan.lifecycle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.lifecycle.application.port.NotificationDeliveryGateway;
import com.seasonplan.lifecycle.application.port.NotificationRecipientResolver;
import com.seasonplan.lifecycle.persistence.ConfiguredNotificationRecipientResolver;
import com.seasonplan.lifecycle.persistence.JdbcNotificationDeliveryGateway;
import com.seasonplan.lifecycle.persistence.JdbcNotificationDeliveryWorker;
import com.seasonplan.lifecycle.persistence.SmtpNotificationEmailGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.Map;
import java.util.Properties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationDeliveryConfiguration.SmtpSettings.class)
public class NotificationDeliveryConfiguration {
    @Bean
    @ConditionalOnMissingBean(NotificationRecipientResolver.class)
    NotificationRecipientResolver notificationRecipientResolver(SmtpSettings settings) {
        return new ConfiguredNotificationRecipientResolver(settings.getRecipients());
    }

    @Bean
    @ConditionalOnMissingBean(NotificationDeliveryGateway.class)
    NotificationDeliveryGateway notificationDeliveryGateway(JdbcTemplate jdbc, SmtpSettings settings,
            NotificationRecipientResolver recipients) {
        var email = new SmtpNotificationEmailGateway(() -> settings.configured() ? createSmtpSender(settings) : null,
                recipients, settings.getFrom());
        return new JdbcNotificationDeliveryGateway(jdbc, email);
    }

    @Bean
    JdbcNotificationDeliveryWorker notificationDeliveryWorker(JdbcTemplate jdbc, ObjectMapper mapper,
            NotificationDeliveryGateway gateway, PlatformTransactionManager manager, Clock clock) {
        return new JdbcNotificationDeliveryWorker(jdbc, mapper, gateway, manager, clock);
    }

    public static JavaMailSenderImpl createSmtpSender(SmtpSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getHost());
        sender.setPort(settings.getPort());
        sender.setUsername(settings.getUsername());
        sender.setPassword(settings.getPassword());
        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "3000");
        properties.setProperty("mail.smtp.timeout", "3000");
        properties.setProperty("mail.smtp.writetimeout", "3000");
        return sender;
    }

    @ConfigurationProperties(prefix = "notifications.smtp")
    public static class SmtpSettings {
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private String from;
        private Map<String, String> recipients = Map.of();
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public Map<String, String> getRecipients() { return recipients; }
        public void setRecipients(Map<String, String> recipients) { this.recipients = recipients; }
        boolean configured() {
            return host != null && !host.isBlank() && port > 0 && port <= 65535
                    && username != null && !username.isBlank() && password != null && !password.isBlank()
                    && from != null && !from.isBlank();
        }
    }
}
