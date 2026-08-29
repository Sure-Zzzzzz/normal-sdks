package io.github.surezzzzzz.sdk.s3.client.controller;

import io.github.surezzzzzz.sdk.s3.client.annotation.SimpleS3ClientComponent;
import io.github.surezzzzzz.sdk.s3.client.configuration.SimpleS3ClientProperties;
import io.github.surezzzzzz.sdk.s3.client.constant.SimpleS3ClientConstant;
import io.github.surezzzzzz.sdk.s3.client.exception.EventParseFailedException;
import io.github.surezzzzzz.sdk.s3.client.listener.S3EventListener;
import io.github.surezzzzzz.sdk.s3.client.model.S3Event;
import io.github.surezzzzzz.sdk.s3.client.support.S3EventParseHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * S3 事件回调接收端点（默认关闭，event-callback.enable 开启后装配）。
 *
 * <p>接收存储侧事件通知 webhook 推送：认证 → 解析 → 分发给业务的
 * {@link S3EventListener} Bean。认证三通道：Authorization Bearer 头
 * （支持自定义请求头的存储）、URL query token（不支持自定义请求头的存储，
 * 如部分私有化存储的 webhook 只能配 URL）、token 未配置不校验
 * （仅限网络层已隔离的部署）。</p>
 *
 * <p>响应用 HTTP status 表达：204 成功、401 认证失败、400 解析失败、
 * 500 监听器异常（触发存储侧重投）。无统一包装体。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RestController
@SimpleS3ClientComponent
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
@ConditionalOnProperty(prefix = SimpleS3ClientConstant.CONFIG_PREFIX + "."
        + SimpleS3ClientConstant.CONFIG_PROPERTY_EVENT_CALLBACK,
        name = SimpleS3ClientConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = SimpleS3ClientConstant.BOOLEAN_TRUE)
public class S3EventCallbackController {

    private final SimpleS3ClientProperties properties;

    private final ObjectProvider<S3EventListener> listeners;

    /**
     * 创建事件回调接收端点。
     *
     * @param properties Client 配置
     * @param listeners  监听器提供器（按 Order 顺序分发，可为空）
     */
    public S3EventCallbackController(SimpleS3ClientProperties properties,
                                     ObjectProvider<S3EventListener> listeners) {
        this.properties = properties;
        this.listeners = listeners;
    }

    /**
     * 接收事件通知推送。
     *
     * @param body          事件 JSON 请求体
     * @param authorization Authorization 头（可空）
     * @param queryToken    URL query token 参数（可空，不支持自定义请求头的存储使用）
     * @return 204 成功；401 认证失败；400 解析失败；500 监听器异常
     */
    @PostMapping("${" + SimpleS3ClientConstant.CONFIG_PREFIX + ".event-callback.path:"
            + SimpleS3ClientConstant.DEFAULT_CALLBACK_PATH + "}")
    public ResponseEntity<Void> onEvent(@RequestBody String body,
                                        @RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestParam(value = SimpleS3ClientConstant.CALLBACK_TOKEN_PARAM, required = false) String queryToken) {
        if (!authenticated(authorization, queryToken)) {
            log.warn("S3 事件回调认证失败");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        S3Event event;
        try {
            event = S3EventParseHelper.parse(body);
        } catch (EventParseFailedException exception) {
            log.warn("S3 事件回调解析失败: {}", exception.getMessage());
            return ResponseEntity.badRequest().build();
        }
        try {
            dispatch(event);
        } catch (RuntimeException exception) {
            log.warn("S3 事件回调监听器执行失败, 异常类型: {}", exception.getClass().getName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * token 已配置时校验双通道：Authorization Bearer 头或 URL query token 任一命中即通过；
     * token 未配置时不校验（网络层防护责任在部署侧）。
     */
    private boolean authenticated(String authorization, String queryToken) {
        String expected = properties.getEventCallback().getToken();
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (authorization != null && authorization.startsWith(SimpleS3ClientConstant.BEARER_PREFIX)
                && tokenEquals(expected, authorization.substring(SimpleS3ClientConstant.BEARER_PREFIX.length()))) {
            return true;
        }
        return queryToken != null && tokenEquals(expected, queryToken);
    }

    /**
     * 常量时间比对，防时序侧信道。
     */
    private boolean tokenEquals(String expected, String presented) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private void dispatch(S3Event event) {
        List<S3EventListener> ordered = listeners.orderedStream()
                .collect(Collectors.toList());
        if (ordered.isEmpty()) {
            log.warn("S3 事件回调无监听器, 事件已确认但未处理, 记录数: {}",
                    event.getRecords() == null ? 0 : event.getRecords().size());
            return;
        }
        for (S3EventListener listener : ordered) {
            listener.onEvent(event);
        }
    }
}
