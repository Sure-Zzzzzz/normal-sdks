package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.constant.SimpleIamPortalGatewayAdaptorConstant;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.ResolvedTarget;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.spi.DnsResolver;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation.SimpleIamPortalGatewayAdaptorComponent;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 使用 JDK 解析目标并固定 IP，Envoy 不在运行期重新按域名解析。
 */
@SimpleIamPortalGatewayAdaptorComponent
public class DefaultDnsResolver implements DnsResolver {

    @Override
    public List<ResolvedTarget> resolve(String scheme, String host, int port) {
        if (!SimpleIamPortalGatewayAdaptorConstant.SCHEME_HTTP.equalsIgnoreCase(scheme)) {
            return Collections.emptyList();
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            List<ResolvedTarget> result = new ArrayList<>(addresses.length);
            for (InetAddress address : addresses) {
                String ip = address.getHostAddress();
                result.add(ResolvedTarget.builder()
                        .host(host)
                        .ip(ip)
                        .scheme(scheme)
                        .port(port)
                        .build());
            }
            return result;
        } catch (UnknownHostException ex) {
            return Collections.emptyList();
        }
    }
}
