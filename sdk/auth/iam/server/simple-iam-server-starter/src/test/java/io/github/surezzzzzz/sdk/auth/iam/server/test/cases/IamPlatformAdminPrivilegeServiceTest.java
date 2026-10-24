package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamEffectiveRoleResolver;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamPlatformAdminPrivilegeService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.TrustedApplicationBuiltInResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 平台管理员特权主体完整性测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamPlatformAdminPrivilegeServiceTest {

    @Test
    @DisplayName("用户缺失或 subjectId 为空白时不解析角色")
    void rejectsMissingUserOrSubjectBeforeResolvingRoles() {
        IamUserRepository userRepository = mock(IamUserRepository.class);
        IamEffectiveRoleResolver effectiveRoleResolver = mock(IamEffectiveRoleResolver.class);
        IamRoleRepository roleRepository = mock(IamRoleRepository.class);
        IamPlatformAdminPrivilegeService service = service(userRepository, effectiveRoleResolver, roleRepository,
                mock(IamTrustedApplicationRepository.class), mock(IamApplicationPermissionManifestRepository.class),
                mock(TrustedApplicationBuiltInResolver.class));
        IamUserEntity nullSubjectUser = user(null);
        IamUserEntity blankSubjectUser = user(" \t ");
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(nullSubjectUser));
        when(userRepository.findById(3L)).thenReturn(Optional.of(blankSubjectUser));

        log.info("验证缺失用户及 null/blank subjectId 均在角色解析前失败关闭");
        assertFalse(service.isPlatformAdmin(1L), "用户行不存在时不得判为平台管理员");
        assertFalse(service.isPlatformAdmin(2L), "subjectId 为 null 时不得判为平台管理员");
        assertFalse(service.isPlatformAdmin(3L), "subjectId 为空白时不得判为平台管理员");
        verify(effectiveRoleResolver, never()).resolveRoleIds(anyLong());
        verifyNoInteractions(roleRepository);
    }

    @Test
    @DisplayName("直接构造特权上下文时用户缺失或 subjectId 为空白必须失败关闭")
    void rejectsPrivilegedContextWithoutUsableSubject() {
        IamUserRepository userRepository = mock(IamUserRepository.class);
        IamTrustedApplicationRepository applicationRepository = mock(IamTrustedApplicationRepository.class);
        IamApplicationPermissionManifestRepository manifestRepository =
                mock(IamApplicationPermissionManifestRepository.class);
        TrustedApplicationBuiltInResolver builtInResolver = mock(TrustedApplicationBuiltInResolver.class);
        IamTrustedApplicationEntity application = new IamTrustedApplicationEntity();
        application.setApplicationCode("iam");
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(application));
        when(builtInResolver.isBuiltIn(application)).thenReturn(true);
        when(manifestRepository.findByApplicationId(10L)).thenReturn(Optional.empty());
        when(userRepository.findById(11L)).thenReturn(Optional.empty());
        when(userRepository.findById(12L)).thenReturn(Optional.of(user(null)));
        when(userRepository.findById(13L)).thenReturn(Optional.of(user("  ")));
        IamPlatformAdminPrivilegeService service = service(userRepository, mock(IamEffectiveRoleResolver.class),
                mock(IamRoleRepository.class), applicationRepository, manifestRepository, builtInResolver);

        log.info("验证直接构造的特权上下文不接受缺失用户或空白 subjectId");
        SimpleIamServerException missingUser = assertThrows(SimpleIamServerException.class,
                () -> service.buildPrivilegedContext(11L, 10L, Instant.EPOCH, Instant.EPOCH.plusSeconds(60)));
        SimpleIamServerException missingSubject = assertThrows(SimpleIamServerException.class,
                () -> service.buildPrivilegedContext(12L, 10L, Instant.EPOCH, Instant.EPOCH.plusSeconds(60)));
        SimpleIamServerException blankSubject = assertThrows(SimpleIamServerException.class,
                () -> service.buildPrivilegedContext(13L, 10L, Instant.EPOCH, Instant.EPOCH.plusSeconds(60)));
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED, missingUser.getErrorCode());
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED, missingSubject.getErrorCode());
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED, blankSubject.getErrorCode());
    }

    @Test
    @DisplayName("有效 subjectId 的平台管理员仍按有效角色判定")
    void resolvesRolesForUserWithSubject() {
        IamUserRepository userRepository = mock(IamUserRepository.class);
        IamEffectiveRoleResolver effectiveRoleResolver = mock(IamEffectiveRoleResolver.class);
        IamRoleRepository roleRepository = mock(IamRoleRepository.class);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user("subject-20")));
        when(effectiveRoleResolver.resolveRoleIds(20L)).thenReturn(Collections.singleton(30L));
        IamRoleEntity adminRole = new IamRoleEntity();
        adminRole.setCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        when(roleRepository.findAllById(Collections.singleton(30L))).thenReturn(Collections.singletonList(adminRole));
        IamPlatformAdminPrivilegeService service = service(userRepository, effectiveRoleResolver, roleRepository,
                mock(IamTrustedApplicationRepository.class), mock(IamApplicationPermissionManifestRepository.class),
                mock(TrustedApplicationBuiltInResolver.class));

        log.info("验证有效 subjectId 用户仍使用角色解析结果判定平台管理员");
        assertTrue(service.isPlatformAdmin(20L));
        verify(effectiveRoleResolver).resolveRoleIds(20L);
    }

    private IamPlatformAdminPrivilegeService service(IamUserRepository userRepository,
                                                     IamEffectiveRoleResolver effectiveRoleResolver,
                                                     IamRoleRepository roleRepository,
                                                     IamTrustedApplicationRepository applicationRepository,
                                                     IamApplicationPermissionManifestRepository manifestRepository,
                                                     TrustedApplicationBuiltInResolver builtInResolver) {
        return new IamPlatformAdminPrivilegeService(userRepository, effectiveRoleResolver, roleRepository,
                applicationRepository, manifestRepository, builtInResolver);
    }

    private IamUserEntity user(String subjectId) {
        IamUserEntity user = new IamUserEntity();
        user.setSubjectId(subjectId);
        return user;
    }
}
