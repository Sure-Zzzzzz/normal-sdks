package io.github.surezzzzzz.sdk.sensitive.keyword.test.cases;

import io.github.surezzzzzz.sdk.sensitive.keyword.registry.KeywordRegistry;
import io.github.surezzzzzz.sdk.sensitive.keyword.support.KeywordSensitiveMaskHelper;
import io.github.surezzzzzz.sdk.sensitive.keyword.test.SmartKeywordSensitiveTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * KeywordSensitiveMaskHelper 端到端测试
 * 专注于单个组织名脱敏的核心场景
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartKeywordSensitiveTestApplication.class)
class KeywordSensitiveMaskHelperTest {

    @Autowired
    private KeywordSensitiveMaskHelper helper;

    @Autowired
    private KeywordRegistry keywordRegistry;

    @Test
    void test() {
        String text = "东方大国北方电网有限责任公司超低压输电公司";
        KeywordSensitiveMaskHelper.MaskResultDetail result = helper.maskWithDetail(text);
        log.info("======================================");
        log.info("原文: {}", text);
        log.info("脱敏结果: {}", result.getMaskedText());
        log.info("======================================");
        log.info("完整Reason:\n{}", result.getReason());
        log.info("======================================");
    }

}
