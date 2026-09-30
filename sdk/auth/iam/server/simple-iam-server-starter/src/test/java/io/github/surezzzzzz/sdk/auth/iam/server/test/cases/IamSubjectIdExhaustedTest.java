package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.SubjectIdGenerator;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 劣质生成器（恒定输出）重试耗尽失败关闭——独立类承载固定生成器上下文，不污染其他用例。
 *
 * <p>依赖真实测试库，与联调库分离后纳入全量验收；用例提交不回滚，
 * 前置清理历史残留（固定 subjectId 的用户）保证跨全量重跑幂等。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {SimpleIamServerTestApplication.class, IamSubjectIdExhaustedTest.FixedGeneratorConfig.class})
class IamSubjectIdExhaustedTest {

    private static final String FIXED_SUBJECT_ID = "1234567890123456";

    @Autowired
    private IamUserService userService;

    @Autowired
    private IamUserRepository userRepository;

    @BeforeEach
    void cleanUpLeftovers() {
        userRepository.findByUsername("subject-exhausted-first").ifPresent(userRepository::delete);
        userRepository.findByUsername("subject-exhausted-second").ifPresent(userRepository::delete);
        // 上一次运行若在清理前中断，残留用户会占用固定 subjectId，使本轮首个建号即耗尽重试
        userRepository.findBySubjectId(FIXED_SUBJECT_ID).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("恒定输出生成器：第二个用户唯一冲突重试耗尽，抛异常不静默吞")
    void shouldThrowWhenGeneratorAlwaysCollides() {
        userService.createUser(buildRequest("subject-exhausted-first"));
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> userService.createUser(buildRequest("subject-exhausted-second")));
        log.info("劣质生成器重试耗尽: errorCode={}", exception.getErrorCode());
        assertEquals("SUBJECT_ID_002", exception.getErrorCode());
    }

    private CreateUserRequest buildRequest(String username) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Exhausted#2026");
        request.setDisplayName("劣质生成器测试用户");
        return request;
    }

    /**
     * 固定输出生成器：业务方劣质实现的最坏形态（@Import 显式引入，不得被组件扫描捡到泄漏进运行时）。
     */
    static class FixedGeneratorConfig {

        @Bean
        @Primary
        public SubjectIdGenerator fixedSubjectIdGenerator() {
            return () -> "1234567890123456";
        }
    }
}
