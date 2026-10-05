package io.github.surezzzzzz.sdk.kms.server.controller;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 本人密钥轮换或取消销毁的资源版本请求。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class KmsMyKeyVersionRequest {

    /**
     * 最近回读的资源版本。
     */
    private final long expectedRowVersion;
}
