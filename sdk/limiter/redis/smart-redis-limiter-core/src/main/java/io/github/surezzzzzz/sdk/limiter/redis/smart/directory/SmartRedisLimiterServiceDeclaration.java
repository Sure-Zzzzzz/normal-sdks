package io.github.surezzzzzz.sdk.limiter.redis.smart.directory;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2 服务目录声明
 *
 * <p>controlMode 与 smart-redis-limiter-core 的
 * {@code SmartRedisLimiterServiceControlMode} 编码一致（LEGACY_V1 / TYPED_V2）；
 * 一个服务只能属于一种协议模式，LEGACY_V1 服务不出现在类型化目录中。
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterServiceDeclaration {

    /**
     * 服务编码（最多 128 字符稳定 ASCII，区分大小写）
     */
    private String serviceCode;

    /**
     * 协议模式编码：LEGACY_V1 / TYPED_V2
     */
    private String controlMode;

    /**
     * 展示名称（可空；为空时目录接口回显服务编码）
     */
    private String displayName;
    /**
     * 策略代次（可空=默认 1）：协议切换时由宿主显式递增，运行端用于隔离旧快照
     */
    private Long policyEpoch;

    /**
     * 资源声明：资源编码 -> 该资源可用的计数维度编码列表
     */
    private Map<String, List<String>> resources = new LinkedHashMap<>();

    /**
     * 维度 -> 宿主声明的计数命名空间（RESOURCE/IP 固定空间可不填；
     * 未声明的维度使用空命名空间）
     */
    private Map<String, String> namespaces = new LinkedHashMap<>();

    /**
     * CUSTOM 维度允许的自定义类型编码列表
     */
    private List<String> customTypes = new ArrayList<>();

    /**
     * 判断资源是否声明了某维度
     */
    public boolean declares(String resourceCode, String dimension) {
        List<String> dimensions = resources.get(resourceCode);
        return dimensions != null && dimensions.contains(dimension);
    }
}
