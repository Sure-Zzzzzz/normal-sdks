package io.github.surezzzzzz.sdk.cache.pubsub;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Cache Invalidation Message
 * <p>
 * 缓存失效消息
 * </p>
 *
 * @author Sure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 缓存名称
     */
    private String cacheName;

    /**
     * 缓存 key
     */
    private String key;

    /**
     * 操作类型：evict（删除单个）、clear（清空所有）
     */
    private String operation;

    /**
     * 发送者实例标识
     */
    private String sender;
}
