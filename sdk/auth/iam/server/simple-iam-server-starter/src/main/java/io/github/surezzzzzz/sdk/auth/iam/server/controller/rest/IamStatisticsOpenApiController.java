package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response.AdminDashboardResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.dashboard.IamAdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API 统计族（{@code /iam/api/statistics}，1.3.7）。
 *
 * <p>只读聚合：外部运维大屏/监控拉 IAM 运行统计（用户/应用/会话计数主口径与管理台同源）。
 * 纯码控无 DATA 面。登录明细（recent-logins）不开放——遍历面属管理台深度职能。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api/statistics")
@RequiredArgsConstructor
public class IamStatisticsOpenApiController {

    private final IamAdminDashboardService dashboardService;

    /**
     * 统计聚合（200）。
     */
    @GetMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_API)
    public ResponseEntity<AdminDashboardResponse> getStatistics() {
        AdminDashboardResponse statistics = dashboardService.getDashboard();
        log.debug("开放 API 统计聚合查询：activeSessions={}", statistics.getStats().getActiveSessions());
        return ResponseEntity.ok(statistics);
    }
}
