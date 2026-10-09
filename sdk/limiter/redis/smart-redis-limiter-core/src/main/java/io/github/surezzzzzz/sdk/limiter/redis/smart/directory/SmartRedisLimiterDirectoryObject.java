package io.github.surezzzzzz.sdk.limiter.redis.smart.directory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * v2 对象目录条目：稳定 ID + 展示名称
 *
 * <p>名称仅作辅助显示，可空；不可获取时如实回显稳定 ID，不编造归属。
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmartRedisLimiterDirectoryObject {

    /**
     * 计数维度编码
     */
    private String dimension;

    /**
     * CUSTOM 维度的自定义类型编码，其他维度为 null
     */
    private String customType;

    /**
     * 稳定对象标识（规则使用该值，最多 256 码点）
     */
    private String id;

    /**
     * 展示名称（可空）
     */
    private String name;
}
