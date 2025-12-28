package io.github.surezzzzzz.sdk.sensitive.keyword.test.cases;

import io.github.surezzzzzz.sdk.sensitive.keyword.support.KeywordSensitiveMaskHelper;
import io.github.surezzzzzz.sdk.sensitive.keyword.test.SmartKeywordSensitiveTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hash掩码类型测试
 * 使用 application-hash.yml 配置
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartKeywordSensitiveTestApplication.class)
@ActiveProfiles("hash")
class HashMaskTest {

    @Autowired
    private KeywordSensitiveMaskHelper helper;

    @Test
    @DisplayName("测试Hash掩码 - mask()")
    void testHashMask() {
        log.info("========================================");
        log.info("Hash掩码测试");
        log.info("========================================");

        String[] testCases = {
                "示例市示例科技有限公司",
                "示例县示例机构办公室",
                "示例省示例金融控股有限公司",
                "东大港外轮代理有限公司",
        };

        for (String text : testCases) {
            String masked = helper.mask(text);
            log.info("原文: {}", text);
            log.info("脱敏: {}", masked);
            log.info("---");

            assertNotNull(masked, "脱敏结果不应为空");
            assertTrue(masked.matches(".*[0-9a-fA-F]{32}.*"), "应包含32位哈希值");
        }

        log.info("========================================");
    }

    @Test
    @DisplayName("测试Hash掩码 - maskWithDetail()")
    void testHashMaskWithDetail() {
        log.info("========================================");
        log.info("Hash掩码详细测试");
        log.info("========================================");

        String text = "示例市示例科技有限公司";
        KeywordSensitiveMaskHelper.MaskResultDetail detail = helper.maskWithDetail(text);

        log.info("原文: {}", detail.getOriginalText());
        log.info("脱敏: {}", detail.getMaskedText());
        log.info("原因: {}", detail.getReason());

        assertNotNull(detail, "详细结果不应为空");
        assertNotNull(detail.getMaskedText(), "脱敏文本不应为空");
        assertTrue(detail.getMaskedText().matches(".*[0-9a-fA-F]{32}.*"), "应包含32位哈希值");

        log.info("========================================");
    }
}
