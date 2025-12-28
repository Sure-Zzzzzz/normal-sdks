package io.github.surezzzzzz.sdk.sensitive.keyword.strategy;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.configuration.SmartKeywordSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorCode;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.keyword.exception.MaskException;
import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.MaskReasonHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Hash Mask Strategy Implementation
 * 哈希脱敏策略实现
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class HashMaskStrategy implements MaskStrategy {

    private static final ThreadLocal<MessageDigest> MD5_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance(SmartKeywordSensitiveConstant.HASH_ALGORITHM);
        } catch (Exception e) {
            throw new MaskException(ErrorCode.MASK_HASH_FAILED,
                    String.format(ErrorMessage.MASK_HASH_FAILED, e.getMessage()), e);
        }
    });

    @Override
    public String mask(String keyword, MetaInfo meta, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        if (!StringUtils.hasText(keyword)) {
            return keyword;
        }

        if (config == null || !config.isComplete()) {
            throw new MaskException(ErrorCode.CONFIG_STRATEGY_MISSING,
                    String.format(ErrorMessage.CONFIG_STRATEGY_MISSING, keyword));
        }

        return maskWithHash(keyword);
    }

    /**
     * 哈希掩码
     */
    private String maskWithHash(String keyword) {
        try {
            MessageDigest md = MD5_THREAD_LOCAL.get();
            md.reset();
            byte[] hash = md.digest(keyword.getBytes(StandardCharsets.UTF_8));

            // 转为16进制
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02X", b));
            }

            // 截取指定长度
            String fullHash = sb.toString();
            return fullHash.substring(0, Math.min(SmartKeywordSensitiveConstant.HASH_LENGTH, fullHash.length()));

        } catch (Exception e) {
            throw new MaskException(ErrorCode.MASK_HASH_FAILED,
                    String.format(ErrorMessage.MASK_HASH_FAILED, e.getMessage()), e);
        }
    }

    /**
     * Fallback 脱敏
     * 当没有匹配到任何关键词或组织机构时使用
     * 对于Hash策略，fallback就是直接hash整个文本
     *
     * @param text   原始文本
     * @param config 策略配置
     * @return 脱敏后的文本
     */
    @Override
    public String maskWithFallback(String text, SmartKeywordSensitiveProperties.RuntimeStrategy config) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        return maskWithHash(text);
    }

    /**
     * Hash策略不需要元信息提取
     */
    @Override
    public boolean requiresFullMetaExtraction() {
        return false;
    }

    /**
     * Hash策略不需要保留率计算
     */
    @Override
    public boolean requiresRetentionCalculation() {
        return false;
    }

    /**
     * 构建Hash脱敏的原因说明（统一通过MaskReasonHelper生成）
     */
    @Override
    public String buildMaskReason(String keyword, String source, MetaInfo meta,
                                  SmartKeywordSensitiveProperties.RuntimeStrategy originalStrategy,
                                  SmartKeywordSensitiveProperties.RuntimeStrategy actualStrategy,
                                  Object adjustment, String masked) {
        // Hash策略统一通过MaskReasonHelper生成reason
        if (adjustment instanceof MaskReasonHelper.RetentionAdjustment) {
            MaskReasonHelper.RetentionAdjustment reasonAdjustment =
                    (MaskReasonHelper.RetentionAdjustment) adjustment;
            return MaskReasonHelper.buildSingleTaskReason(
                    keyword, source, meta, originalStrategy, actualStrategy, reasonAdjustment, masked
            );
        }
        // adjustment为null时也通过MaskReasonHelper生成简化格式
        return MaskReasonHelper.buildSingleTaskReason(
                keyword, source, meta, originalStrategy, actualStrategy, null, masked
        );
    }
}
