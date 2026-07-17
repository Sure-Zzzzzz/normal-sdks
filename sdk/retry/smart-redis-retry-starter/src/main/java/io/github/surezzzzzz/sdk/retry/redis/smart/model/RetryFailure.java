package io.github.surezzzzzz.sdk.retry.redis.smart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 重试失败记录请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryFailure implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 重试类型
     */
    private String retryType;
    /**
     * 重试标识
     */
    private String retryKey;
    /**
     * 调用方指定的重试策略
     */
    private RetryPolicy policy;
    /**
     * 错误编码
     */
    private String errorCode;
    /**
     * 错误消息
     */
    private String errorMessage;
    /**
     * 重试上下文
     */
    private Map<String, Object> context;
}
