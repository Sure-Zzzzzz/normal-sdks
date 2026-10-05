package io.github.surezzzzzz.sdk.kms.server.controller;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 本人密钥启停请求。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class KmsMyKeyStateRequest {

    /**
     * 目标状态，仅 ACTIVE 或 DISABLED。
     */
    private final String state;
    /**
     * 最近回读的资源版本。
     */
    private final long expectedRowVersion;
}
