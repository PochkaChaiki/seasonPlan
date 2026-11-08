package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.adapter.fake.FakePollenTreeClient;
import com.seasonplan.lifecycle.adapter.pollentree.RestPollenTreeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLParameters;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PollenTreeIntegrationConfiguration.Settings.class)
public class PollenTreeIntegrationConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "integrations.pollen-tree", name = "mode", havingValue = "rest", matchIfMissing = true)
    RestPollenTreeClient restPollenTreeClient(Settings settings, RestClient.Builder builder) {
        var factory = new JdkClientHttpRequestFactory(httpClient(settings));
        factory.setReadTimeout(settings.readTimeout());
        RestClient client = builder.requestFactory(factory).baseUrl(settings.baseUrl())
                .defaultHeader("X-API-Key", settings.apiKey()).build();
        return new RestPollenTreeClient(client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "integrations.pollen-tree", name = "mode", havingValue = "fake")
    FakePollenTreeClient fakePollenTreeClient(Settings settings, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))
                || !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("Pollen Tree fake requires dev/test and is forbidden in prod");
        }
        return new FakePollenTreeClient(settings.fakeCapacityGrams());
    }

    static HttpClient httpClient(Settings settings) {
        SSLParameters tls = new SSLParameters();
        tls.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        return HttpClient.newBuilder().sslParameters(tls).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(settings.connectTimeout()).build();
    }

    @ConfigurationProperties(prefix = "integrations.pollen-tree")
    public record Settings(@DefaultValue("rest") String mode, String baseUrl, String apiKey,
                           @DefaultValue("2s") Duration connectTimeout, @DefaultValue("3s") Duration readTimeout,
                           @DefaultValue("1000") BigDecimal fakeCapacityGrams) {
        public Settings {
            if (!"rest".equals(mode) && !"fake".equals(mode)) {
                throw new IllegalArgumentException("Pollen Tree mode must be rest or fake");
            }
            bounded(connectTimeout);
            bounded(readTimeout);
            if (fakeCapacityGrams == null || fakeCapacityGrams.signum() < 0) {
                throw new IllegalArgumentException("Pollen Tree fake capacity must not be negative");
            }
            if ("rest".equals(mode)) {
                validateBaseUrl(baseUrl);
                if (apiKey == null || apiKey.isBlank() || apiKey.chars().anyMatch(c -> c < 32 || c == 127)) {
                    throw new IllegalArgumentException("Pollen Tree REST requires a nonblank valid API key");
                }
            }
        }

        private static void bounded(Duration timeout) {
            if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException("Pollen Tree timeouts must be between 1ms and 30s");
            }
        }

        private static void validateBaseUrl(String value) {
            try {
                URI uri = URI.create(value == null ? "" : value);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Pollen Tree REST requires an absolute HTTPS base URL without userinfo, query or fragment");
            }
        }

        @Override
        public String toString() {
            return "PollenTreeSettings[mode=" + mode + ", credentials=REDACTED]";
        }
    }
}
