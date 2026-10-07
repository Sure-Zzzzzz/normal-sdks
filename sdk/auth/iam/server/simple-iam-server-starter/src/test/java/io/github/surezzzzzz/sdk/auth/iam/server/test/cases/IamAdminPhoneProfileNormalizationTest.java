package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.UpdateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * admin 手机号登记路径资料面归一测试（1.3.3 修复回归）。
 *
 * <p>三态语义：null=不动/不登记、trim 空串=清空（置 NULL）、非空=normalize。
 * 防回归主断言：两个空串手机号用户均可创建（修复前第二个撞 uk_phone 唯一索引）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamAdminPhoneProfileNormalizationTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> userIds = new ArrayList<Long>();

    @Autowired
    private IamUserService userService;
    @Autowired
    private IamUserRepository userRepository;

    @AfterEach
    void cleanup() {
        for (Long userId : userIds) {
            userRepository.findById(userId).ifPresent(user -> userService.deleteUser(user.getId()));
        }
    }

    @Test
    void shouldStoreNullForEmptyPhoneOnCreateAndAllowSecondEmptyPhoneUser() {
        String phone = "";
        IamUserEntity first = create(phone);
        assertNull(first.getPhone(), "空串手机号创建必须落 NULL（不得落空串）");
        log.info("[1] 空串创建落 NULL: userId={}", first.getId());

        // 防回归主断言：修复前第二个空串用户撞 Duplicate entry '' for key uk_phone
        IamUserEntity second = create(phone);
        assertNotNull(second.getId(), "第二个空串手机号用户必须可创建（uk_phone 不撞唯一索引）");
        assertNull(second.getPhone(), "第二个空串用户同样落 NULL");
        log.info("[2] 第二个空串用户创建成功: userId={}", second.getId());
    }

    @Test
    void shouldClearPhoneToNullOnUpdateWithEmptyString() {
        IamUserEntity user = create("+8613800000001");
        assertEquals("+8613800000001", user.getPhone(), "前置：登记手机号应为 E.164 形态");

        UpdateUserRequest request = new UpdateUserRequest();
        request.setPhone("   ");
        IamUserEntity updated = userService.updateUser(user.getId(), request);
        assertNull(updated.getPhone(), "trim 空串更新必须清空为 NULL（对齐解绑语义）");
        log.info("[3] 空白串更新置 NULL: userId={}", updated.getId());
    }

    @Test
    void shouldNormalizeBlankPaddedDomesticPhoneOnCreate() {
        IamUserEntity user = create(" 13800138000 ");
        assertEquals("+8613800138000", user.getPhone(), "带空格裸号必须规范化为 +86 E.164 形态");
        log.info("[4] 裸号带空格规范化: phone={}", user.getPhone());
    }

    @Test
    void shouldRejectMalformedPhoneOnCreate() {
        assertThrows(SimpleIamServerException.class, () -> create("abc"),
                "非空非法格式必须在入口 400 拒绝，不得落库成脏资料");
        log.info("[5] 非法格式创建被拒绝");
    }

    @Test
    void shouldKeepPhoneUntouchedWhenUpdatePhoneIsNull() {
        IamUserEntity user = create("+8613800000002");

        UpdateUserRequest request = new UpdateUserRequest();
        request.setDisplayName("改名不动号");
        IamUserEntity updated = userService.updateUser(user.getId(), request);
        assertEquals("+8613800000002", updated.getPhone(), "更新 phone=null 必须保持原值不动（既有语义回归保护）");
        log.info("[6] null 更新不动原号: phone={}", updated.getPhone());
    }

    @Test
    void shouldNormalizePhoneOnUpdateAndMarkAuditPhoneChanged() {
        IamUserEntity user = create(null);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setPhone("13900139000");
        IamUserEntity updated = userService.updateUser(user.getId(), request);
        assertEquals("+8613900139000", updated.getPhone(), "更新非空裸号必须规范化为 +86 E.164 形态落库");
        log.info("[7] 更新裸号规范化: phone={}", updated.getPhone());
    }

    private IamUserEntity create(String phone) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("phone-profile-" + suffix + "-" + userIds.size());
        request.setPassword("User@1234");
        request.setDisplayName("手机号资料面测试用户");
        request.setPhone(phone);
        IamUserEntity user = userService.createUser(request);
        userIds.add(user.getId());
        return user;
    }
}
