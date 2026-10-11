package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.JitUsernameConflictStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JIT 用户名冲突策略单元测试（1.3.7 B 件，纯逻辑）：解析/续号 max+1/拒绝/非法配置。
 *
 * @author surezzzzzz
 */
class JitUsernameConflictStrategyTest {

    private static Predicate<String> occupied(String... names) {
        Set<String> taken = new HashSet<String>(Arrays.asList(names));
        return taken::contains;
    }

    @Test
    @DisplayName("of()：默认 auto-suffix；两值解析；非法值 CONFIG_VALIDATION_FAILED")
    void ofParsesValuesAndFailsFast() {
        assertEquals(JitUsernameConflictStrategy.AUTO_SUFFIX, JitUsernameConflictStrategy.of(null));
        assertEquals(JitUsernameConflictStrategy.AUTO_SUFFIX, JitUsernameConflictStrategy.of("  "));
        assertEquals(JitUsernameConflictStrategy.AUTO_SUFFIX, JitUsernameConflictStrategy.of("auto-suffix"));
        assertEquals(JitUsernameConflictStrategy.REJECT, JitUsernameConflictStrategy.of("reject"));
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> JitUsernameConflictStrategy.of("foo"));
        assertEquals("CONFIG_001", exception.getErrorCode());
    }

    @Test
    @DisplayName("AUTO_SUFFIX：不撞原样；首冲突 -1；已有 -1..-5 续 -6（max+1 非从 1 找空位）")
    void autoSuffixResolvesByMaxPlusOne() {
        assertEquals("zhangsan", JitUsernameConflictStrategy.AUTO_SUFFIX
                .resolve("zhangsan", occupied("other"), Collections.emptyList()));
        assertEquals("zhangsan-1", JitUsernameConflictStrategy.AUTO_SUFFIX
                .resolve("zhangsan", occupied("zhangsan"), Collections.singletonList("zhangsan")));
        // 前缀集合含 -1..-5 与非数字干扰项 → max=5 续 -6（中间空洞 -3 即便被删也不回填）
        assertEquals("zhangsan-6", JitUsernameConflictStrategy.AUTO_SUFFIX
                .resolve("zhangsan", occupied("zhangsan", "zhangsan-1", "zhangsan-2", "zhangsan-4", "zhangsan-5",
                                "zhangsan-abc", "zhangsan-2b"),
                        Arrays.asList("zhangsan-1", "zhangsan-2", "zhangsan-abc", "zhangsan-2b", "zhangsan-4", "zhangsan-5")));
    }

    @Test
    @DisplayName("AUTO_SUFFIX：前缀集合含 -02 归一为 2；二次确认撞（并发窗口）拒绝走管理员")
    void autoSuffixNormalizesAndGuardsConcurrency() {
        // -02 解析归一 2 → max=2 → 续 -3
        assertEquals("zhangsan-3", JitUsernameConflictStrategy.AUTO_SUFFIX
                .resolve("zhangsan", occupied("zhangsan", "zhangsan-1", "zhangsan-02"),
                        Arrays.asList("zhangsan-1", "zhangsan-02")));
        // max+1 候选实际被占（并发插入窗口）→ NOT_BOUND 终态
        SimpleIamServerException conflict = assertThrows(SimpleIamServerException.class,
                () -> JitUsernameConflictStrategy.AUTO_SUFFIX.resolve("zhangsan",
                        occupied("zhangsan", "zhangsan-1"), Collections.emptyList()));
        assertEquals("BIZ_005", conflict.getErrorCode());
    }

    @Test
    @DisplayName("REJECT：不撞原样；撞名 NOT_BOUND（1.3.6 行为零变化）")
    void rejectKeepsLegacySemantics() {
        assertEquals("zhangsan", JitUsernameConflictStrategy.REJECT
                .resolve("zhangsan", occupied("other"), null));
        SimpleIamServerException reject = assertThrows(SimpleIamServerException.class,
                () -> JitUsernameConflictStrategy.REJECT
                        .resolve("zhangsan", occupied("zhangsan"), null));
        assertEquals("BIZ_005", reject.getErrorCode());
        assertEquals(ServerErrorMessage.EXTERNAL_IDENTITY_NOT_BOUND, reject.getMessage());
    }
}
