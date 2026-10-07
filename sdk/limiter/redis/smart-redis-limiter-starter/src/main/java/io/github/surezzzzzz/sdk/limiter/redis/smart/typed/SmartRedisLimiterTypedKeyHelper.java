package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;

/**
 * 类型化计数桶 Redis Key Helper
 * <p>同一桶的多个窗口使用相同 Hash Tag 包裹的摘要，保证 Cluster 下单桶多窗口原子性。
 * javax 与 jakarta 两条运行端按本模板逐字节一致地组 Key，共享同一 Redis 时不互认错桶。</p>
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterTypedKeyHelper {

    private SmartRedisLimiterTypedKeyHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构造类型化计数桶 Key
     *
     * @param serviceCode  服务编码
     * @param resourceCode 资源编码
     * @param bucketDigest 桶身份摘要
     * @param useHashTag   是否以 Hash Tag 包裹摘要
     * @return 计数桶 Key
     */
    public static String buildBucketKey(String serviceCode, String resourceCode,
                                        String bucketDigest, boolean useHashTag) {
        String bucketPart = useHashTag
                ? "{" + bucketDigest + "}"
                : bucketDigest;
        return String.format(SmartRedisLimiterStarterConstant.TEMPLATE_TYPED_BUCKET_KEY,
                SmartRedisLimiterConstant.TYPED_REDIS_BUSINESS_TYPE, serviceCode, resourceCode, bucketPart);
    }
}
