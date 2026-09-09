package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response.AdminDashboardResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response.AdminRecentLoginResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 管理台仪表盘聚合服务
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamAdminDashboardService {

    private final IamUserRepository userRepository;
    private final IamDepartmentRepository departmentRepository;
    private final IamUserGroupRepository userGroupRepository;
    private final IamRoleRepository roleRepository;
    private final IamPermissionRepository permissionRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamSessionRepository sessionRepository;

    /**
     * 聚合仪表盘数据：治理规模计数 + 运行状态统计。
     *
     * <p>纯读操作不发审计事件；今日零点按部署时区计算（Instant 为 UTC，直接回退 24 小时会有时区偏差）。
     */
    public AdminDashboardResponse getDashboard() {
        Instant now = Instant.now();
        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

        AdminDashboardResponse.GovernanceCounts counts = new AdminDashboardResponse.GovernanceCounts(
                userRepository.count(),
                departmentRepository.count(),
                userGroupRepository.count(),
                roleRepository.count(),
                permissionRepository.count(),
                trustedApplicationRepository.count()
        );

        AdminDashboardResponse.RuntimeStats stats = new AdminDashboardResponse.RuntimeStats(
                sessionRepository.countByStatusAndExpiresAtAfter(SimpleIamServerConstant.STATUS_ACTIVE, now),
                userRepository.countByLastLoginAtAfter(todayStart),
                userRepository.countByLockedUntilAfter(now),
                userRepository.countByStatus(SimpleIamServerConstant.STATUS_INACTIVE),
                userRepository.countByDepartmentIdIsNull()
        );

        return new AdminDashboardResponse(counts, stats);
    }

    /**
     * 最近登录用户分页（仪表盘最近登录列表，按最后登录时间倒序）
     */
    public Page<AdminRecentLoginResponse> listRecentLogins(int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "lastLoginAt"));
        return userRepository.findByLastLoginAtIsNotNull(pageable)
                .map(user -> AdminRecentLoginResponse.from(user, resolveDepartmentName(user.getDepartmentId())));
    }

    private String resolveDepartmentName(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .map(IamDepartmentEntity::getName)
                .orElse(null);
    }
}
