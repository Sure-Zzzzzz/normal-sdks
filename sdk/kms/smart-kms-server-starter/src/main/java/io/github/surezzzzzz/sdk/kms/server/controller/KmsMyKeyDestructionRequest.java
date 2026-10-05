package io.github.surezzzzzz.sdk.kms.server.controller;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 本人密钥销毁排程请求。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class KmsMyKeyDestructionRequest {

    /**
     * 已规范化为 UTC 毫秒的销毁时间。
     */
    private final String dueAt;
    /**
     * 最近回读的资源版本。
     */
    private final long expectedRowVersion;
}
