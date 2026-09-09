package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamMessageRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.MessageSseService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 站内信 SSE 跨实例广播端到端测试（DESIGN.message-sse-distributed-broadcast.md）。
 *
 * <p>真实 HTTP SSE 流 + 真实 Redis Pub/Sub：伪装远端实例直发广播 channel，
 * 断言本实例订阅回调投递到本地 emitter；本实例推送不因自身广播回环重复出帧。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class IamMessageSseBroadcastTest {

    private static final String REMOTE_INSTANCE_ID = "remote-fake-instance-";
    private static final long AWAIT_TIMEOUT_MS = 15_000;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "sse-broadcast-" + suffix;
    private final RestTemplate restTemplate = restTemplate();
    private final String remoteInstanceId = REMOTE_INSTANCE_ID + suffix;
    private final ConcurrentLinkedQueue<String> streamLines = new ConcurrentLinkedQueue<>();
    @LocalServerPort
    private int port;
    @Autowired
    private UserService userService;
    @Autowired
    private IamUserRepository userRepository;
    @Autowired
    private IamMessageRepository messageRepository;
    @Autowired
    private MessageSseService messageSseService;
    @Autowired
    private RedisRouteTemplate redisRouteTemplate;
    @Autowired
    private RedisKeyHelper redisKeyHelper;
    private IamUserEntity user;
    private String sessionCookie;
    private HttpURLConnection streamConnection;
    private volatile boolean streamClosed;

    @BeforeEach
    void prepare() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Admin@1234");
        request.setDisplayName(username);
        user = userService.createUser(request);
        sessionCookie = loginSessionCookie();
    }

    @AfterEach
    void cleanup() {
        closeStream();
        userRepository.findByUsername(username).ifPresent(item -> {
            messageRepository.findByRecipientUserIdOrderByCreatedAtDesc(item.getId())
                    .forEach(messageRepository::delete);
            userService.deleteUser(item.getId());
        });
    }

    @Test
    @DisplayName("远端实例广播 UNREAD_COUNT 后，本实例 SSE 流收到该未读数帧")
    void remoteUnreadCountBroadcastPushesToLocalSubscriber() throws Exception {
        openSseStream();
        assertTrue(awaitLine(line -> line.equals("event:" + MessageSseService.EVENT_READY)), "SSE 流必须先就绪");

        publishRemote(String.format(
                "{\"type\":\"%s\",\"userId\":%d,\"unreadCount\":3,\"instanceId\":\"%s\"}",
                MessageSseService.BROADCAST_TYPE_UNREAD_COUNT, user.getId(), remoteInstanceId));

        assertTrue(awaitLine(line -> line.equals("data:3")), "远端广播的未读数必须投递到本实例 emitter");
        log.info("远端 UNREAD_COUNT 广播投递断言完成：userId={}", user.getId());
    }

    @Test
    @DisplayName("远端实例广播 EVICT 后，本实例 SSE 流被关闭")
    void remoteEvictBroadcastClosesLocalSubscriber() throws Exception {
        openSseStream();
        assertTrue(awaitLine(line -> line.equals("event:" + MessageSseService.EVENT_READY)), "SSE 流必须先就绪");

        publishRemote(String.format(
                "{\"type\":\"%s\",\"userId\":%d,\"instanceId\":\"%s\"}",
                MessageSseService.BROADCAST_TYPE_EVICT, user.getId(), remoteInstanceId));

        assertTrue(awaitClosed(), "远端 EVICT 广播必须关闭本实例连接");
        log.info("远端 EVICT 广播关闭断言完成：userId={}", user.getId());
    }

    @Test
    @DisplayName("本实例推送只出一帧：广播回环跳过自身，不产生重复帧")
    void localPushBroadcastsExactlyOneFrameWithoutSelfEcho() throws Exception {
        openSseStream();
        assertTrue(awaitLine(line -> line.equals("event:" + MessageSseService.EVENT_READY)), "SSE 流必须先就绪");

        messageSseService.pushUnreadCount(user.getId(), 7L);

        assertTrue(awaitLine(line -> line.equals("data:7")), "本地直推帧必须到达");
        Thread.sleep(1500);
        assertEquals(1, countLine(line -> line.equals("data:7")),
                "自身广播必须被 instanceId 跳过，不得重复出帧");
        log.info("广播防回环断言完成：userId={}", user.getId());
    }

    /**
     * 伪装远端实例直发广播 channel（instanceId 非 本实例）。
     */
    private void publishRemote(String payloadJson) {
        String channel = redisKeyHelper.buildPubSubChannel(SimpleIamServerConstant.BUSINESS_MESSAGE_SSE);
        redisRouteTemplate.stringTemplateByKey(channel).convertAndSend(channel, payloadJson);
    }

    /**
     * 建立真实 SSE 流，后台线程逐行读入队列。
     */
    private void openSseStream() throws Exception {
        URL url = URI.create("http://localhost:" + port + "/iam/web/messages/events").toURL();
        streamConnection = (HttpURLConnection) url.openConnection();
        streamConnection.setRequestProperty("Cookie", sessionCookie);
        streamConnection.setRequestProperty("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
        streamConnection.setReadTimeout(20_000);
        assertEquals(HttpStatus.OK.value(), streamConnection.getResponseCode(), "SSE 端点必须可建立");

        Thread reader = new Thread(() -> {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                    streamConnection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    streamLines.add(line);
                }
            } catch (Exception exception) {
                log.debug("SSE 读流结束：{}", exception.getMessage());
            } finally {
                streamClosed = true;
            }
        }, "sse-stream-reader-" + suffix);
        reader.setDaemon(true);
        reader.start();
    }

    private void closeStream() {
        if (streamConnection != null) {
            streamConnection.disconnect();
            streamConnection = null;
        }
    }

    private boolean awaitLine(Predicate<String> matcher) throws InterruptedException {
        return awaitCondition(() -> streamLines.stream().anyMatch(matcher));
    }

    private boolean awaitClosed() throws InterruptedException {
        return awaitCondition(() -> streamClosed);
    }

    private boolean awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private long countLine(Predicate<String> matcher) {
        return streamLines.stream().filter(matcher).count();
    }

    private String loginSessionCookie() {
        ResponseEntity<Map> csrfResponse = restTemplate.exchange(
                URI.create("http://localhost:" + port + "/iam/web/auth/csrf"), HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        String cookie = sessionCookie(csrfResponse.getHeaders());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = new HttpHeaders();
        headersCookie(loginHeaders, cookie);
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                URI.create("http://localhost:" + port + "/iam/web/auth/login"), HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"Admin@1234\"}",
                        loginHeaders),
                Map.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        String loginCookie = sessionCookie(loginResponse.getHeaders());
        assertNotNull(loginCookie != null ? loginCookie : cookie, "登录必须成功并建立会话");
        return loginCookie != null ? loginCookie : cookie;
    }

    private void headersCookie(HttpHeaders headers, String cookie) {
        headers.set(HttpHeaders.COOKIE, cookie);
    }

    private String sessionCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith(SimpleIamServerConstant.SESSION_COOKIE_NAME + "=")) {
                    return cookie.substring(0, cookie.indexOf(';'));
                }
            }
        }
        return null;
    }

    private RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate(new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().disableRedirectHandling().disableCookieManagement().build()));
        template.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) {
            }
        });
        return template;
    }
}
