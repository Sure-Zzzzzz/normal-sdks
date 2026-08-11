package io.github.surezzzzzz.sdk.ops.middleware.redis;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Redis 精确 key 类型化读取安全响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class RedisKeyReadResponse {

    private final String dataType;
    private final String stringValue;
    private final List<FieldValue> hashEntries;
    private final List<IndexValue> listEntries;
    private final List<String> setMembers;
    private final List<ScoreMember> zSetEntries;
    private final List<StreamEntry> streamEntries;

    /**
     * Hash field/value 条目。
     */
    @Getter
    @Builder
    public static class FieldValue {
        private final String field;
        private final String value;
    }

    /**
     * List index/value 条目。
     */
    @Getter
    @Builder
    public static class IndexValue {
        private final long index;
        private final String value;
    }

    /**
     * ZSet score/member 条目。
     */
    @Getter
    @Builder
    public static class ScoreMember {
        private final String member;
        private final Double score;
    }

    /**
     * Stream ID/field/value 条目。
     */
    @Getter
    @Builder
    public static class StreamEntry {
        private final String id;
        private final Map<String, String> fields;
    }
}
