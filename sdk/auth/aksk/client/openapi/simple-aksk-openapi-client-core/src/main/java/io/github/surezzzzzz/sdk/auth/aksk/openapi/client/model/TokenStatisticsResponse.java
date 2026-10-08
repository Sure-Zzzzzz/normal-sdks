package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

/**
 * Token 统计响应
 *
 * @author surezzzzzz
 */
public class TokenStatisticsResponse {

    /**
     * 总数
     */
    private long totalCount;

    /**
     * 活跃数
     */
    private long activeCount;

    /**
     * 已撤销数
     */
    private long revokedCount;

    /**
     * 已过期数
     */
    private long expiredCount;

    /**
     * MySQL 侧计数
     */
    private long mysqlCount;

    /**
     * Redis 侧计数
     */
    private long redisCount;

    /**
     * 双侧计数
     */
    private long bothCount;


    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(long activeCount) {
        this.activeCount = activeCount;
    }

    public long getRevokedCount() {
        return revokedCount;
    }

    public void setRevokedCount(long revokedCount) {
        this.revokedCount = revokedCount;
    }

    public long getExpiredCount() {
        return expiredCount;
    }

    public void setExpiredCount(long expiredCount) {
        this.expiredCount = expiredCount;
    }

    public long getMysqlCount() {
        return mysqlCount;
    }

    public void setMysqlCount(long mysqlCount) {
        this.mysqlCount = mysqlCount;
    }

    public long getRedisCount() {
        return redisCount;
    }

    public void setRedisCount(long redisCount) {
        this.redisCount = redisCount;
    }

    public long getBothCount() {
        return bothCount;
    }

    public void setBothCount(long bothCount) {
        this.bothCount = bothCount;
    }
}
