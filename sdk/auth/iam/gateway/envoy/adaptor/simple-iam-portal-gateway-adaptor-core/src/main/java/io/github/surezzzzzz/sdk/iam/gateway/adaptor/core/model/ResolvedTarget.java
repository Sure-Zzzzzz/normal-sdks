package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * DNS 解析后的固定 HTTP 上游端点。IP 用于 EDS，host 用于 Host/authority。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class ResolvedTarget {

    /**
     * 登记目标主机名。
     */
    String host;
    /**
     * 解析后的 IP 地址。
     */
    String ip;
    /**
     * 上游协议。
     */
    String scheme;
    /**
     * 上游端口。
     */
    int port;
}
