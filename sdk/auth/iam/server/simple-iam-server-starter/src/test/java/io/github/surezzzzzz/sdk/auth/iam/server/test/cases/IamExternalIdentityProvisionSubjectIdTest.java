package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalIdentity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamExternalIdentityResolutionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 外部身份 JIT 开号的主体 ID 分配测试。
 *
 * <p>SSO / LDAP 首次登录自动开号必须当场分配 subject_id（与本地建号同一契约），
 * 不得留空等待下次启动的存量回填补号——回填前用户的 OIDC sub 只能回退数字主键，
 * 与 1.3.0 稳定主体语义冲突（2026-09-27 e2e-user 踩坑）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamExternalIdentityProvisionSubjectIdTest {

    private final String username = "ext-prov-" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private IamExternalIdentityResolutionService resolutionService;
    @Autowired
    private IamUserService userService;
    @Autowired
    private IamUserRepository userRepository;

    @AfterEach
    void cleanup() {
        userRepository.findByUsername(username)
                .ifPresent(user -> userService.deleteUser(user.getId()));
    }

    private ExternalIdentity identity(String providerCode, String externalId, String name) {
        return ExternalIdentity.builder()
                .providerCode(providerCode)
                .externalId(externalId)
                .usernameSuggestion(name)
                .displayName(name)
                .build();
    }

    @Test
    @DisplayName("JIT 开号当场分配 subjectId：非空、落库、身份源正确")
    void provisionAssignsSubjectIdImmediately() {
        IamUserEntity provisioned = resolutionService.resolve(
                identity("oidc", "sub-" + username, username));

        assertTrue(provisioned.getSubjectId() != null && !provisioned.getSubjectId().trim().isEmpty(),
                "JIT 开号必须当场分配主体ID");
        assertEquals("oidc", provisioned.getIdentitySource());

        IamUserEntity reloaded = userRepository.findById(provisioned.getId()).get();
        assertEquals(provisioned.getSubjectId(), reloaded.getSubjectId(), "主体ID必须已落库");
        log.info("✓ JIT 开号即有号：username={}, subjectId={}", username, provisioned.getSubjectId());
    }

    @Test
    @DisplayName("两次开号的 subjectId 互不相同（唯一性经 existsBy 校验路径）")
    void provisionedSubjectIdsAreUnique() {
        String username2 = username + "-b";
        try {
            IamUserEntity first = resolutionService.resolve(
                    identity("ldap", "uid=" + username + ",dc=test", username));
            IamUserEntity second = resolutionService.resolve(
                    identity("ldap", "uid=" + username2 + ",dc=test", username2));

            assertNotEquals(first.getSubjectId(), second.getSubjectId(), "不同用户的主体ID不得相同");
            log.info("✓ 主体ID唯一：{} vs {}", first.getSubjectId(), second.getSubjectId());
        } finally {
            userRepository.findByUsername(username2)
                    .ifPresent(user -> userService.deleteUser(user.getId()));
        }
    }

    @Test
    @DisplayName("已绑定身份再次登录复用原用户，subjectId 不变")
    void boundIdentityReusesSubjectId() {
        IamUserEntity first = resolutionService.resolve(
                identity("oidc", "sub-" + username, username));
        IamUserEntity again = resolutionService.resolve(
                identity("oidc", "sub-" + username, username));

        assertEquals(first.getId(), again.getId(), "同一外部身份必须复用同一本地用户");
        assertEquals(first.getSubjectId(), again.getSubjectId());
        log.info("✓ 绑定复用：subjectId 稳定 {}", again.getSubjectId());
    }
}
