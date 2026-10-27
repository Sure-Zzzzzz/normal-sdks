package io.github.surezzzzzz.sdk.ticket.server.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketServiceException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 机器开单请求摘要；只保存规范化请求哈希，模板后续变更不影响幂等判定。
 *
 * @author surezzzzzz
 */
public final class OpenApiRequestFingerprint {

    private static final ObjectMapper MAPPER = TicketJson.mapper().copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private OpenApiRequestFingerprint() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成 SHA-256 摘要；字段顺序固定，答案 Map 各层按键排序。
     *
     * @param typeCode 类型编码
     * @param subject 标题
     * @param description 描述
     * @param priority 规范化优先级
     * @param ownerSubjectId 提单人标识
     * @param answers 原始答案
     * @return 十六进制摘要
     */
    public static String of(String typeCode, String subject, String description, TicketPriority priority,
                            String ownerSubjectId, Map<String, Object> answers) {
        try {
            byte[] canonical = MAPPER.writeValueAsBytes(Arrays.asList(typeCode, subject, description,
                    priority.name(), ownerSubjectId, answers));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            char[] encoded = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int value = digest[i] & 0xff;
                encoded[i * 2] = HEX[value >>> 4];
                encoded[i * 2 + 1] = HEX[value & 0x0f];
            }
            return new String(encoded);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new TicketServiceException(e);
        }
    }

    /**
     * 生成 Portal 提单摘要；Portal 的关联单和附件也是幂等请求体的一部分。
     *
     * @param typeCode 类型编码
     * @param subject 标题
     * @param description 描述
     * @param priority 规范化优先级
     * @param ownerSubjectId 提单人标识
     * @param answers 原始答案
     * @param relatedTicketNo 关联单号
     * @param attachments 附件元数据
     * @return 十六进制摘要
     */
    public static String portal(String typeCode, String subject, String description, TicketPriority priority,
                                String ownerSubjectId, Map<String, Object> answers, String relatedTicketNo,
                                List<Map<String, Object>> attachments) {
        try {
            byte[] canonical = MAPPER.writeValueAsBytes(Arrays.asList(typeCode, subject, description,
                    priority.name(), ownerSubjectId, answers, relatedTicketNo, attachments));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            char[] encoded = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int value = digest[i] & 0xff;
                encoded[i * 2] = HEX[value >>> 4];
                encoded[i * 2 + 1] = HEX[value & 0x0f];
            }
            return new String(encoded);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new TicketServiceException(e);
        }
    }
}
