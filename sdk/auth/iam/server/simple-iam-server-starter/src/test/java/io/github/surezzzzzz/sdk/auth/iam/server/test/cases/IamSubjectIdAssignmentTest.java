package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap.IamSubjectIdBackfillService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 主体 ID 分配与回填（真实 MySQL 集成形态：创建即分配 / 回填幂等 / 占位不复用 / 劣质生成器重试耗尽）。
 *
 * <p>依赖真实测试库，与联调库分离后纳入全量验收；当前按需手跑。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamSubjectIdAssignmentTest {

    @Autowired
    private IamUserService userService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamSubjectIdBackfillService backfillService;

    @Test
    @DisplayName("用户创建即分配主体 ID（16 位数字，写后不改）")
    @Transactional
    void shouldAssignSubjectIdOnCreate() {
        IamUserEntity user = userService.createUser(buildRequest("subject-assign-user"));
        log.info("创建用户: username={}, subjectId={}", user.getUsername(), user.getSubjectId());
        assertNotNull(user.getSubjectId(), "创建即分配主体 ID");
        assertTrue(user.getSubjectId().matches("[0-9]{16}"), "默认生成器形态为 16 位数字");
    }

    @Test
    @DisplayName("存量回填幂等：NULL 行补齐，二次启动零变更")
    @Transactional
    void shouldBackfillSubjectIdIdempotently() {
        IamUserEntity user = userService.createUser(buildRequest("subject-backfill-user"));
        Optional<IamUserEntity> managedUser = userRepository.findById(user.getId());
        assertTrue(managedUser.isPresent(), "创建后的用户必须可查询");
        IamUserEntity managed = managedUser.get();
        managed.setSubjectId(null);
        userRepository.saveAndFlush(managed);

        backfillService.run(null);
        Optional<IamUserEntity> refilled = userRepository.findById(user.getId());
        assertTrue(refilled.isPresent());
        assertNotNull(refilled.get().getSubjectId(), "回填后主体 ID 非空");
        String first = refilled.get().getSubjectId();

        backfillService.run(null);
        assertEquals(first, userRepository.findById(user.getId()).map(IamUserEntity::getSubjectId).orElse(null),
                "二次回填零变更");
    }

    @Test
    @DisplayName("软删行占位不复用：不同用户主体 ID 必然不同（唯一索引兜底 + 冲突重试换新）")
    @Transactional
    void shouldNotReuseSubjectIdOfArchivedRow() {
        IamUserEntity first = userService.createUser(buildRequest("subject-first-user"));
        IamUserEntity second = userService.createUser(buildRequest("subject-second-user"));
        log.info("firstSubjectId={}, secondSubjectId={}", first.getSubjectId(), second.getSubjectId());
        assertNotEquals(first.getSubjectId(), second.getSubjectId(), "不同用户主体 ID 必然不同");
    }

    private CreateUserRequest buildRequest(String username) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Backfill#2026");
        request.setDisplayName("主体ID测试用户");
        return request;
    }
}
