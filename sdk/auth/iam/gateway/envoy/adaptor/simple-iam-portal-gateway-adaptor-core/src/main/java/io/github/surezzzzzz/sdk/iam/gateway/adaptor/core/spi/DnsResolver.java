package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.spi;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.ResolvedTarget;

import java.util.List;

/**
 * DNS 解析端口。实现由 starter/runtime 提供，core 不直接访问网络。
 *
 * @author surezzzzzz
 */
public interface DnsResolver {

    /**
     * 解析并返回经过实现方规范化的固定端点。
     *
     * @param scheme 目标协议
     * @param host   登记目标主机
     * @param port   目标端口
     * @return 固定 IP 端点；空集合表示没有可用解析
     */
    List<ResolvedTarget> resolve(String scheme, String host, int port);
}
