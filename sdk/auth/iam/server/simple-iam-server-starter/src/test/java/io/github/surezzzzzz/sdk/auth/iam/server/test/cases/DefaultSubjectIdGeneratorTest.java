package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.SubjectIdGenerator;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.support.DefaultSubjectIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 默认主体 ID 生成器：形态与随机性（纯逻辑单测，不起 Spring 上下文）。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultSubjectIdGeneratorTest {

    private final SubjectIdGenerator generator = new DefaultSubjectIdGenerator();

    @Test
    @DisplayName("默认生成器形态：16 位纯数字")
    void shouldGenerateSixteenDigitSubjectId() {
        String subjectId = generator.generate();
        log.info("生成的主体ID: {}", subjectId);
        assertNotNull(subjectId, "主体ID不应为空");
        assertEquals(SimpleIamServerConstant.DEFAULT_SUBJECT_ID_LENGTH, subjectId.length(), "主体ID长度应为默认长度");
        assertTrue(subjectId.matches("[0-9]+"), "主体ID应为纯数字（首位可为 0，纯字符串无数值语义）");
    }

    @Test
    @DisplayName("多次生成不重复（随机性冒烟，SPI 契约：非空）")
    void shouldGenerateDistinctValues() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String subjectId = generator.generate();
            assertNotNull(subjectId, "主体ID不应为空");
            assertTrue(subjectId.length() <= 64, "主体ID长度不应超过 SPI 契约上限 64");
            seen.add(subjectId);
        }
        log.info("1000 次生成去重后数量: {}", seen.size());
        assertEquals(1000, seen.size(), "1000 次生成应全部不同（16 位随机空间 10^16）");
    }
}
