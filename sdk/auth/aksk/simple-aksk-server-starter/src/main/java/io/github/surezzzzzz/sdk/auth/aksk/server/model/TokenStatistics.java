package io.github.surezzzzzz.sdk.auth.aksk.server.model;

import lombok.Data;

/**
 * Token统计信息领域模型
 * <p>
 * 用于表示Token的统计数据，包括总数、状态分布（有效/过期）、数据源分布（MySQL/Redis/Both）等。
 * 支持从MySQL和Redis双重数据源进行统计聚合。
 *
 * @author surezzzzzz
 */
@Data
public class TokenStatistics {

    /**
     * 总Token数（MySQL + Redis去重后的总数）
     */
    private long totalCount;

    /**
     * 有效Token数（未过期）
     */
    private long activeCount;

    /**
     * 过期Token数
     */
    private long expiredCount;

    /**
     * MySQL中的Token数（包括仅在MySQL和同时在MySQL+Redis中的）
     */
    private long mysqlCount;

    /**
     * Redis中的Token数（包括仅在Redis和同时在MySQL+Redis中的）
     */
    private long redisCount;

    /**
     * 同时存在于MySQL和Redis的Token数
     */
    private long bothCount;
}
