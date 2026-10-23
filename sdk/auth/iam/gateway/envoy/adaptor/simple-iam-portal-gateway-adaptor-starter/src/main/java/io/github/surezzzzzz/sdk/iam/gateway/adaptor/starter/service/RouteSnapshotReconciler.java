package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationRoute;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.constant.SimpleIamPortalGatewayAdaptorConstant;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.*;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.service.RouteSnapshotBuilder;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.spi.DnsResolver;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation.SimpleIamPortalGatewayAdaptorComponent;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration.SimpleIamPortalGatewayAdaptorProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 IAM Portal 投影映射为 core 输入，并委托 core 生成整份快照。
 */
@SimpleIamPortalGatewayAdaptorComponent
public class RouteSnapshotReconciler {

    private final DnsResolver dnsResolver;
    private final SimpleIamPortalGatewayAdaptorProperties properties;
    private final RouteSnapshotBuilder snapshotBuilder = new RouteSnapshotBuilder();

    public RouteSnapshotReconciler(DnsResolver dnsResolver,
                                   SimpleIamPortalGatewayAdaptorProperties properties) {
        this.dnsResolver = dnsResolver;
        this.properties = properties;
    }

    /**
     * 构建失败时只返回结构化拒绝，不把部分路由交给 xDS。
     *
     * @param input           IAM Portal 路由投影
     * @param catalogRevision IAM 目录版本
     * @return 全量快照或结构化拒绝结果
     */
    public RouteSnapshotBuildResult reconcile(List<IamPortalApplicationRoute> input,
                                              long catalogRevision) {
        if (input == null || catalogRevision < 0L) {
            return RouteSnapshotBuildResult.rejected(
                    RouteValidationResult.rejected(RouteValidationReason.DIRECTORY_INCOMPLETE));
        }
        List<PortalApplicationRoute> routes = new ArrayList<>();
        for (IamPortalApplicationRoute item : input) {
            routes.add(toCoreRoute(item));
        }
        return snapshotBuilder.build(catalogRevision, targetPolicy(), routes, dnsResolver);
    }

    private PortalApplicationRoute toCoreRoute(IamPortalApplicationRoute item) {
        PortalApplicationRoute.PortalApplicationRouteBuilder builder = PortalApplicationRoute.builder();
        if (item == null) {
            return builder.build();
        }
        builder.applicationCode(item.getApplicationCode())
                .applicationName(item.getApplicationName())
                .status(item.getStatus())
                .portalEnabled(item.getPortalEnabled())
                .routePrefix(item.getRoutePrefix())
                .entry(item.getEntry())
                .apiBase(item.getApiBase())
                .targetOrigin(item.getTargetOrigin())
                .configVersion(item.getConfigVersion())
                .forwardAuthorization(item.getForwardAuthorization());
        if (item.getForwardCookieNames() != null) {
            for (String cookieName : item.getForwardCookieNames()) {
                builder.forwardCookieName(cookieName);
            }
        }
        return builder.build();
    }

    private TargetUrlPolicy targetPolicy() {
        TargetUrlPolicy.TargetUrlPolicyBuilder builder = TargetUrlPolicy.builder()
                .allowLoopback(properties.isAllowLoopback())
                .allowPrivateNetwork(properties.isAllowPrivateNetwork())
                .allowMetadata(properties.isAllowMetadata())
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_ROOT)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_IAM)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_IAM_APP)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_APP)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_CLIENT_API)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_PERSONAL_CLIENT_API)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_TOKEN_API)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_APPLICATION_AUTHORIZATION_API)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_OAUTH)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_LOGIN)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_HEALTH)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_ACTUATOR)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_XDS)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_ADMIN);
        if (properties.getAllowedPorts() != null) {
            for (Integer port : properties.getAllowedPorts()) {
                if (port != null) {
                    builder.allowedPort(port);
                }
            }
        }
        return builder.build();
    }
}
