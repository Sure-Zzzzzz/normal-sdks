package io.github.surezzzzzz.sdk.sensitive.keyword.test.cases;

import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
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
 * Placeholder掩码类型测试
 * 使用 application-placeholder.yml 配置
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartKeywordSensitiveTestApplication.class)
@ActiveProfiles("placeholder")
class PlaceholderMaskTest {

    @Autowired
    private KeywordSensitiveMaskHelper helper;

    @Test
    @DisplayName("测试Placeholder掩码 - mask()")
    void testPlaceholderMask() {
        log.info("========================================");
        log.info("Placeholder掩码测试");
        log.info("========================================");

        String[] testCases = {
                "示例市示例科技有限公司",        // 公司
                "示例县示例机构办公室",          // 单位
                "示例省示例金融控股有限公司",     // 公司+金融
        };

        for (String text : testCases) {
            String masked = helper.mask(text);
            log.info("原文: {}", text);
            log.info("脱敏: {}", masked);
            log.info("---");

            assertNotNull(masked, "脱敏结果不应为空");
            // Placeholder是动态生成的，应该包含[前缀_xxx]格式
            assertTrue(masked.startsWith("[") && masked.endsWith("]"), "应该是方括号格式");
            assertTrue(masked.contains("_"), "应该包含下划线分隔符");
        }

        log.info("========================================");
    }

    @Test
    @DisplayName("测试Placeholder掩码 - maskWithDetail()")
    void testPlaceholderMaskWithDetail() {
        log.info("========================================");
        log.info("Placeholder掩码详细测试");
        log.info("========================================");

        String text = "示例市示例科技有限公司";
        KeywordSensitiveMaskHelper.MaskResultDetail detail = helper.maskWithDetail(text);

        log.info("原文: {}", detail.getOriginalText());
        log.info("脱敏: {}", detail.getMaskedText());
        log.info("原因: {}", detail.getReason());

        assertNotNull(detail, "详细结果不应为空");
        assertNotNull(detail.getMaskedText(), "脱敏文本不应为空");
        // Placeholder是动态生成的，应该是[企业_有限公司]格式（从常量读取前缀）
        String expectedPrefix = "[" + SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_COMPANY + "_";
        assertTrue(detail.getMaskedText().startsWith(expectedPrefix) && detail.getMaskedText().endsWith("]"),
                "应该是[" + SmartKeywordSensitiveConstant.ORG_TYPE_PREFIX_COMPANY + "_xxx]格式");
        assertTrue(detail.getMaskedText().contains("有限公司"), "应包含组织类型'有限公司'");

        log.info("========================================");
    }
}
