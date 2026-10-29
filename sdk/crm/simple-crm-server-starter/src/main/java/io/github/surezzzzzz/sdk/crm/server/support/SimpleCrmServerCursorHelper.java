package io.github.surezzzzzz.sdk.crm.server.support;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * 游标编码：排序主键与查询指纹绑定，筛选、列表族或授权范围变化时拒绝旧游标。
 *
 * @author surezzzzzz
 */
public final class SimpleCrmServerCursorHelper {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = ":";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String VALIDATION_FIELD = "cursor";
    private static final int CONTEXT_PARTS = 3;

    private SimpleCrmServerCursorHelper() {
    }

    /**
     * 计算稳定的查询指纹；空值与空字符串保留各自语义，归属集合排序后参与摘要。
     *
     * @param family 列表族
     * @param actor 归属主体
     * @param criteria 筛选条件
     * @param owners DATA 范围，空表示全量或不适用
     * @return 不含原始条件的摘要
     */
    public static String fingerprint(String family, CrmOwner actor, CrmListCriteria criteria,
                                     List<String> owners) {
        List<String> sorted = owners == null ? null : new ArrayList<String>(owners);
        if (sorted != null) {
            Collections.sort(sorted);
        }
        StringBuilder value = new StringBuilder();
        append(value, family);
        append(value, actor.getOwner());
        append(value, criteria.getStateCode());
        append(value, criteria.getRelatedResourceId());
        append(value, criteria.getKeyword());
        append(value, criteria.getCreatedFrom());
        append(value, criteria.getCreatedTo());
        append(value, sorted == null ? null : sorted.size());
        if (sorted != null) {
            for (String owner : sorted) {
                append(value, owner);
            }
        }
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance(DIGEST_ALGORITHM).digest(
                            value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(DIGEST_ALGORITHM, ex);
        }
    }

    /**
     * 编码固定排序主键与查询指纹。
     *
     * @param primaryKey 行主键
     * @param fingerprint 查询指纹
     * @return 不透明游标
     */
    public static String encode(long primaryKey, String fingerprint) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (VERSION + SEPARATOR + primaryKey + SEPARATOR + fingerprint).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 解码并校验游标上下文。旧格式或条件变化须从首页重新查询。
     *
     * @param cursor 游标，空表示首页
     * @param fingerprint 当前查询指纹
     * @return 主键，首页为空
     */
    public static Long decode(String cursor, String fingerprint) {
        if (cursor == null || cursor.trim().isEmpty()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.US_ASCII).split(SEPARATOR, -1);
            if (parts.length != CONTEXT_PARTS || !VERSION.equals(parts[0]) || !fingerprint.equals(parts[2])) {
                throw CrmException.validation(VALIDATION_FIELD);
            }
            long primaryKey = Long.parseLong(parts[1]);
            if (primaryKey <= 0) {
                throw CrmException.validation(VALIDATION_FIELD);
            }
            return primaryKey;
        } catch (IllegalArgumentException ex) {
            throw CrmException.validation(VALIDATION_FIELD);
        }
    }

    private static void append(StringBuilder target, Object value) {
        String text = value == null ? null : value.toString();
        target.append(text == null ? -1 : text.length()).append(SEPARATOR);
        if (text != null) {
            target.append(text);
        }
    }
}
