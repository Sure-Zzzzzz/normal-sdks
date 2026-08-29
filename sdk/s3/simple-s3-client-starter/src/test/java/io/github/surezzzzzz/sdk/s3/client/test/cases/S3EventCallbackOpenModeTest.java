package io.github.surezzzzzz.sdk.s3.client.test.cases;

import io.github.surezzzzzz.sdk.s3.client.test.SimpleS3ClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S3 事件回调端点开放模式测试：token 未配置时不校验认证（网络层防护责任在
 * 部署侧），无监听器时事件确认 204 不失败。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleS3ClientTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.s3.client.event-callback.enable=true")
@AutoConfigureMockMvc
class S3EventCallbackOpenModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedEventAcceptedWhenTokenUnset() throws Exception {
        mockMvc.perform(post("/api/s3-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Records\":[{\"eventName\":\"ObjectRemoved:Delete\"}]}"))
                .andExpect(status().isNoContent());

        log.info("token 未配置时无凭据请求直接受理（部署侧网络层防护模式）");
    }

    @Test
    void noListenerStillAcknowledgesEvent() throws Exception {
        mockMvc.perform(post("/api/s3-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Records\":[{\"eventName\":\"ObjectCreated:Copy\"}]}"))
                .andExpect(status().isNoContent());

        log.info("无监听器时事件确认 204, 不向存储侧报失败");
    }
}
