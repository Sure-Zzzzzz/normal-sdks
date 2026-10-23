package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.constant.SimpleIamPortalGatewayAdaptorStarterConstant;
import lombok.Data;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Portal Gateway adaptor 的运行期配置。凭据由宿主环境注入，不进入该对象的公开字段。
 */
@Data
@ConfigurationProperties(prefix = SimpleIamPortalGatewayAdaptorStarterConstant.CONFIG_PREFIX)
public class SimpleIamPortalGatewayAdaptorProperties {

    /**
     * Portal 目录同步周期（毫秒）。
     */
    private long syncIntervalMillis = SimpleIamPortalGatewayAdaptorStarterConstant.DEFAULT_SYNC_INTERVAL_MILLIS;
    /**
     * 目录分页大小。
     */
    private int pageSize = SimpleIamPortalGatewayAdaptorStarterConstant.DEFAULT_PAGE_SIZE;
    /**
     * 旧快照允许保留的时间（毫秒）；0 表示依赖失败立即未就绪。
     */
    private long maxStaleMillis = 0L;
    /**
     * 目标端口白名单。
     */
    private List<Integer> allowedPorts = new ArrayList<>(Arrays.asList(80));
    /**
     * 是否允许本地回环目标，仅本地 runtime 可显式开启。
     */
    private boolean allowLoopback;
    /**
     * 是否允许 RFC1918 私网目标。
     */
    private boolean allowPrivateNetwork;
    /**
     * 是否允许云元数据地址。
     */
    private boolean allowMetadata;
    /**
     * xDS 监听地址由 runtime 绑定；starter 仅保留配置契约。
     */
    private String xdsBindAddress = "127.0.0.1";
    /**
     * xDS 监听端口由 runtime 绑定。
     */
    private int xdsPort = 18000;
    /**
     * Envoy 数据面监听地址，写入 LDS；由 localruntime 或宿主显式配置。
     */
    private String listenerBindAddress = "0.0.0.0";
    /**
     * Envoy 数据面监听端口，写入 LDS；由 localruntime 或宿主显式配置。
     */
    private int listenerPort = 8080;

    /**
     * 在配置绑定完成后拒绝无法运行的端口、周期和地址配置。
     */
    @PostConstruct
    public void validate() {
        if (syncIntervalMillis <= 0L) {
            throw invalid("sync-interval-millis 必须大于 0");
        }
        if (pageSize <= 0) {
            throw invalid("page-size 必须大于 0");
        }
        if (maxStaleMillis < 0L) {
            throw invalid("max-stale-millis 不能小于 0");
        }
        validatePort(xdsPort, "xds-port");
        validatePort(listenerPort, "listener-port");
        validateAddress(xdsBindAddress, "xds-bind-address");
        validateAddress(listenerBindAddress, "listener-bind-address");
        if (allowedPorts == null || allowedPorts.isEmpty()) {
            throw invalid("allowed-ports 不能为空");
        }
        for (Integer port : allowedPorts) {
            if (port == null) {
                throw invalid("allowed-ports 不能包含空值");
            }
            validatePort(port, "allowed-ports");
        }
    }

    private void validatePort(int port, String propertyName) {
        if (port < SimpleIamPortalGatewayAdaptorStarterConstant.MIN_PORT
                || port > SimpleIamPortalGatewayAdaptorStarterConstant.MAX_PORT) {
            throw invalid(propertyName + " 必须在 1 到 65535 之间");
        }
    }

    private void validateAddress(String address, String propertyName) {
        if (address == null || address.trim().isEmpty() || !address.equals(address.trim())
                || address.indexOf('\u0000') >= 0) {
            throw invalid(propertyName + " 不能为空或包含空白字符");
        }
    }

    private BeanCreationException invalid(String message) {
        return new BeanCreationException("IAM Portal Gateway adaptor 配置无效: " + message);
    }
}
