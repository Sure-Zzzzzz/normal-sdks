package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Write Options
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
public class WriteOptions {

    /**
     * 是否立即刷新。
     */
    private Boolean refresh;
    /**
     * 路由值。
     */
    private String routing;
    /**
     * 超时时间（毫秒）。
     */
    private Long timeoutMs;
    /**
     * ES refresh policy：true / false / wait_for。
     */
    private String refreshPolicy;
}
