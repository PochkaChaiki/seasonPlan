package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.adapter.fake.FakePollenTreeClient;
import com.seasonplan.lifecycle.adapter.pollentree.RestPollenTreeClient;
import com.seasonplan.lifecycle.application.port.PollenTreeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class PollenTreeIntegrationConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PollenTreeIntegrationConfiguration.class)
            .withBean(RestClient.Builder.class, RestClient::builder);
    private static final String PREFIX = "integrations.pollen-tree.";

    @Test
    void defaultRuntimeFailsFastWithoutCredentials() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void restUsesOneExternalClientAndNeverFallsBack() {
        runner.withPropertyValues(PREFIX + "base-url=https://tree.example/api", PREFIX + "api-key=secret")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PollenTreeClient.class).hasSingleBean(RestPollenTreeClient.class);
                    assertThat(context).doesNotHaveBean(FakePollenTreeClient.class);
                });
    }

    @Test
    void configuredRestBeanUsesConfiguredEndpointCredentialsAndReadTimeout() {
        RestClient.Builder builder = org.mockito.Mockito.spy(RestClient.builder());
        new ApplicationContextRunner().withUserConfiguration(PollenTreeIntegrationConfiguration.class)
                .withBean(RestClient.Builder.class, () -> builder)
                .withPropertyValues(PREFIX + "base-url=https://configured.example/tree", PREFIX + "api-key=configured-secret",
                        PREFIX + "read-timeout=700ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    org.mockito.Mockito.verify(builder).baseUrl("https://configured.example/tree");
                    org.mockito.Mockito.verify(builder).defaultHeader("X-API-Key", "configured-secret");
                    var factory = org.mockito.ArgumentCaptor.forClass(org.springframework.http.client.ClientHttpRequestFactory.class);
                    org.mockito.Mockito.verify(builder).requestFactory(factory.capture());
                    assertThat(org.springframework.test.util.ReflectionTestUtils.getField(factory.getValue(), "readTimeout"))
                            .isEqualTo(Duration.ofMillis(700));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://tree.example", "ftp://tree.example", "/relative", "https:tree.example", "https://user:pass@tree.example", "https://tree.example?key=x", "https://tree.example#fragment", "https:///missing"})
    void rejectsUnsafeBaseUrls(String uri) {
        runner.withPropertyValues(PREFIX + "base-url=" + uri, PREFIX + "api-key=secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void rejectsBlankApiKey(String key) {
        runner.withPropertyValues(PREFIX + "base-url=https://tree.example", PREFIX + "api-key=" + key)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"connect-timeout=0ms", "read-timeout=-1s", "connect-timeout=31s", "read-timeout=31s", "mode=unknown"})
    void rejectsInvalidSettings(String setting) {
        runner.withPropertyValues(PREFIX + "base-url=https://tree.example", PREFIX + "api-key=secret", PREFIX + setting)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void clientDisablesRedirectsAndOnlyEnablesTls12And13() {
        var settings = new PollenTreeIntegrationConfiguration.Settings("rest", "https://tree.example/api", "secret",
                Duration.ofMillis(400), Duration.ofMillis(700), new java.math.BigDecimal("2300"));
        HttpClient client = PollenTreeIntegrationConfiguration.httpClient(settings);
        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(client.connectTimeout()).contains(Duration.ofMillis(400));
        assertThat(client.sslParameters().getProtocols()).containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(settings.toString()).doesNotContain("secret");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void explicitFakeInDevOrTestIsSharedAndConfigurable(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile, PREFIX + "mode=fake", PREFIX + "fake-capacity-grams=2300")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PollenTreeClient.class).hasSingleBean(FakePollenTreeClient.class);
                    assertThat(context.getBean(PollenTreeClient.class).capacity().availableGrams()).isEqualByComparingTo("2300");
                    assertThat(context).doesNotHaveBean(RestPollenTreeClient.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "prod", "staging", "prod,dev", "test,prod"})
    void fakeIsForbiddenOutsideDevTestAndAlwaysInProd(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles, PREFIX + "mode=fake")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void devProfileAloneDoesNotMakeMissingRestSettingsFallBack() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void profileResourcesExplicitlySelectFakeForExistingWorkflows(String profile) {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(FakePollenTreeClient.class));
    }

    @Test
    void prodCannotAccidentallyUseDevResourceFake() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod,dev")
                .run(context -> assertThat(context).hasFailed());
    }
}
