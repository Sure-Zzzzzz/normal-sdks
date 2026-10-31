package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.support;

import java.io.InputStream;

/**
 * 管理面变更客户端 JSON 编解码契约（宿主可替换实现；族形态与快照 codec 一致：契约接口 + Jackson 实现分离）
 *
 * @author surezzzzzz
 */
public interface PolicyMutationJsonCodec {

    /**
     * 解析变更响应（rule + revision）
     */
    TypedRuleWire.MutationWire decodeMutation(InputStream inputStream);

    /**
     * 解析列表响应（items + total）
     */
    TypedRuleWire.PageWire decodePage(InputStream inputStream);

    /**
     * 解析单规则对象（GET /v2/policy/rule/{id} 响应）
     */
    TypedRuleWire decodeRule(InputStream inputStream);

    /**
     * 序列化请求体
     */
    String encode(Object value);
}
