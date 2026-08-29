package io.github.surezzzzzz.sdk.s3.client.test.cases;

import io.github.surezzzzzz.sdk.s3.client.listener.S3EventListener;
import io.github.surezzzzzz.sdk.s3.client.model.S3Event;
import io.github.surezzzzzz.sdk.s3.client.test.SimpleS3ClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S3 事件回调端点测试（token 保护模式）：三通道认证（Bearer 头 / URL query /
 * 双通道均不中 401）、400 解析失败、500 监听器异常短路、204 成功、
 * 多监听器按 Order 顺序分发。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {SimpleS3ClientTestApplication.class, S3EventCallbackTest.ListenerConfiguration.class},
        properties = {"io.github.surezzzzzz.sdk.s3.client.event-callback.enable=true",
                "io.github.surezzzzzz.sdk.s3.client.event-callback.token=e2e-callback-token"})
@AutoConfigureMockMvc
class S3EventCallbackTest {

    private static final String PATH = "/api/s3-events";

    private static final String TOKEN = "e2e-callback-token";

    private static final List<String> dispatchOrder = new CopyOnWriteArrayList<>();

    private static volatile boolean listenerThrows;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetListenerState() {
        dispatchOrder.clear();
        listenerThrows = false;
    }

    @Test
    void bearerHeaderAuthenticatesAndDispatches() throws Exception {
        MvcResult result = mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(dispatchOrder).containsExactly("first", "second");
        log.info("Bearer 头通道认证通过且监听器按 Order 顺序分发");
    }

    @Test
    void queryTokenAuthenticates() throws Exception {
        mockMvc.perform(post(PATH + "?token=" + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isNoContent());

        assertThat(dispatchOrder).containsExactly("first", "second");
        log.info("URL query 通道（不支持自定义请求头的存储形态）认证通过");
    }

    @Test
    void missingCredentialsRejected() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isUnauthorized());

        assertThat(dispatchOrder).isEmpty();
        log.info("token 已配置且无任何凭据时 401 且不分发");
    }

    @Test
    void wrongBearerTokenRejected() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isUnauthorized());

        assertThat(dispatchOrder).isEmpty();
        log.info("Bearer 头 token 错误时 401");
    }

    @Test
    void nonBearerAuthorizationFallsBackToQueryOnly() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(PATH + "?token=" + TOKEN)
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isNoContent());

        assertThat(dispatchOrder).containsExactly("first", "second");
        log.info("非 Bearer 头不参与校验, query 通道独立可用");
    }

    @Test
    void malformedJsonRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());

        assertThat(dispatchOrder).isEmpty();
        log.info("非法 JSON 以 400 拒绝且不触发监听器");
    }

    @Test
    void listenerFailureShortCircuitsToServerError() throws Exception {
        listenerThrows = true;

        mockMvc.perform(post(PATH + "?token=" + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isInternalServerError());

        assertThat(dispatchOrder).contains("first").doesNotContain("second");
        log.info("首个监听器抛异常时 500 短路, 后续监听器不再执行（触发存储侧重投）");
    }

    private String validEventJson() {
        return "{\"Records\":[{\"eventVersion\":\"2.1\",\"eventSource\":\"minio:s3\","
                + "\"eventName\":\"ObjectCreated:Put\",\"s3\":{\"bucket\":{\"name\":\"e2e-bucket\"},"
                + "\"object\":{\"key\":\"e2e-key\",\"size\":4,\"sequencer\":\"0001\"}}}]}";
    }

    @TestConfiguration
    static class ListenerConfiguration {

        @Bean
        @Order(1)
        S3EventListener firstListener() {
            return event -> {
                dispatchOrder.add("first");
                assertEventShape(event);
                if (listenerThrows) {
                    throw new IllegalStateException("listener failure for e2e");
                }
            };
        }

        @Bean
        @Order(2)
        S3EventListener secondListener() {
            return event -> dispatchOrder.add("second");
        }

        private void assertEventShape(S3Event event) {
            assertThat(event).isNotNull();
            assertThat(event.getRecords()).isNotNull();
            assertThat(event.getRecords().get(0).getEventName()).isEqualTo("ObjectCreated:Put");
            assertThat(event.getRecords().get(0).getS3().getBucket().getName()).isEqualTo("e2e-bucket");
            assertThat(event.getRecords().get(0).getS3().getObject().getSequencer()).isEqualTo("0001");
        }
    }

}
