package io.github.surezzzzzz.sdk.iam.feign.client.test.cases;

import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.RequestInterceptor;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import feign.Retryer;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.iam.feign.client.configuration.IamAkskFeignConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IamProfileTokenSelfHealTest {

    private static final String PROFILE = "iam";
    private HttpServer server;

    interface IamPingContract {
        @GetMapping("/iam/api/self-heal")
        String ping();
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void revokedProfileTokenShouldClearCacheAndReplayOnce() {
        CountingProfileTokenManager tokenManager = new CountingProfileTokenManager("old-token", true);
        IamAkskFeignConfiguration configuration = configuration(tokenManager);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/iam/api/self-heal", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            int status = requests.incrementAndGet() == 1 ? 401 : 200;
            byte[] body = ("{status:" + status + ",authorization:" + authorization + "}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        IamPingContract client = builder(configuration, tokenManager)
                .target(IamPingContract.class, "http://127.0.0.1:" + server.getAddress().getPort());

        assertTrue(client.ping().startsWith("{status:200"));
        assertEquals(1, tokenManager.clearCount, "401 后应清理 Profile 缓存");
        assertEquals(2, requests.get(), "401 后只应重放一次");
    }

    @Test
    void persistentUnauthorizedShouldNotLoop() {
        CountingProfileTokenManager tokenManager = new CountingProfileTokenManager("revoked-token", false);
        IamAkskFeignConfiguration configuration = configuration(tokenManager);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/iam/api/self-heal", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        IamPingContract client = builder(configuration, tokenManager)
                .target(IamPingContract.class, "http://127.0.0.1:" + server.getAddress().getPort());

        FeignException exception = assertThrows(FeignException.Unauthorized.class, client::ping);
        assertEquals(401, exception.status());
        assertEquals(2, requests.get(), "一次原始请求加一次重放");
        assertEquals(2, tokenManager.clearCount, "每次 401 都应清缓存");
    }

    @Test
    void nonUnauthorizedRetryableExceptionShouldNotReplay() {
        Retryer retryer = configuration(new CountingProfileTokenManager("token", true))
                .iamProfileTokenRetryer();
        Request request = Request.create(Request.HttpMethod.GET, "/iam/api/self-heal",
                java.util.Collections.emptyMap(), null, StandardCharsets.UTF_8);
        RetryableException downstreamException = new RetryableException(
                503, "downstream unavailable", Request.HttpMethod.GET, null, new Date(), request);

        assertThrows(RetryableException.class, () -> retryer.continueOrPropagate(downstreamException));
    }

    private IamAkskFeignConfiguration configuration(ProfileTokenManager tokenManager) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("io.github.surezzzzzz.sdk.iam.client.aksk-profile", PROFILE);
        IamAkskFeignConfiguration configuration = new IamAkskFeignConfiguration(environment);
        ReflectionTestUtils.setField(configuration, "defaultTokenManagerProvider", provider(defaultTokenManager()));
        ReflectionTestUtils.setField(configuration, "profileTokenManagerProvider", provider(tokenManager));
        return configuration;
    }

    private Feign.Builder builder(IamAkskFeignConfiguration configuration, ProfileTokenManager tokenManager) {
        RequestInterceptor interceptor = configuration.akskRequestInterceptor();
        ErrorDecoder errorDecoder = configuration.iamProfileTokenErrorDecoder();
        Retryer retryer = configuration.iamProfileTokenRetryer();
        return Feign.builder()
                .contract(new SpringMvcContract())
                .requestInterceptor(interceptor)
                .errorDecoder(errorDecoder)
                .retryer(retryer);
    }

    private static TokenManager defaultTokenManager() {
        return new TokenManager() {
            @Override
            public String getToken() {
                return "default-token";
            }

            @Override
            public void clearToken() {
            }
        };
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getObject(Object... args) {
                return value;
            }
        };
    }

    private static final class CountingProfileTokenManager implements ProfileTokenManager {
        private final boolean refreshOnClear;
        private String token;
        private int clearCount;

        CountingProfileTokenManager(String initialToken, boolean refreshOnClear) {
            this.token = initialToken;
            this.refreshOnClear = refreshOnClear;
        }

        @Override
        public String getToken() {
            return token;
        }

        @Override
        public void clearToken() {
            clearToken(PROFILE);
        }

        @Override
        public String getToken(String profile) {
            return token;
        }

        @Override
        public void clearToken(String profile) {
            clearCount++;
            if (refreshOnClear) {
                token = "refreshed-token-" + clearCount;
            }
        }

        @Override
        public boolean containsProfile(String profile) {
            return PROFILE.equals(profile);
        }
    }
}
