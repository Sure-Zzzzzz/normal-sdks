package io.github.surezzzzzz.sdk.retry.redis.smart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 重试记录扫描结果
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryScanResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 下一次扫描游标
     */
    private String nextCursor;
    /**
     * 是否已完成扫描
     */
    private boolean finished;
    /**
     * 扫描到的 Redis 记录 Key
     */
    private List<String> keys;
    /**
     * 按 Redis 记录 Key 索引的重试状态
     */
    private Map<String, RetryInfo> infos;
}
