package io.github.surezzzzzz.sdk.auth.captcha.storage;

import java.time.Duration;

/**
 * 挑战存储：验证码答案的暂存与一次性消费
 *
 * <p>唯一实现为 Redis 共享存储（强制 redis-route，无内存模式）：
 * 出题与验题可落在不同实例；宿主无 route bean 时启动快速失败。
 *
 * @author surezzzzzz
 */
public interface ChallengeStore {

    /**
     * 暂存挑战答案
     *
     * @param key    挑战 key（由调用方按 key 规范构建）
     * @param answer 挑战答案（统一小写）
     * @param ttl    有效期
     */
    void save(String key, String answer, Duration ttl);

    /**
     * 原子取出并删除挑战答案（一次性消费语义，防同一挑战并发双花）
     *
     * @param key 挑战 key
     * @return 不存在或已过期 / 已消费时返回 null
     */
    String consume(String key);
}
