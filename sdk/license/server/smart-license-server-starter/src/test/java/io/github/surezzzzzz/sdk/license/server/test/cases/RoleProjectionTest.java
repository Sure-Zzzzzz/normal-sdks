package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.server.integration.RoleProjectionDiffCalculator;
import io.github.surezzzzzz.sdk.license.server.integration.RoleProjectionDiffCalculator.DepartmentFact;
import io.github.surezzzzzz.sdk.license.server.integration.RoleProjectionDiffCalculator.Diff;
import io.github.surezzzzzz.sdk.license.server.integration.RoleProjectionPlan;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * 角色联动差异与清单断言：子树展开/环防护/迁入迁出差异/清单确认与失效语义。
 *
 * @author surezzzzzz
 */
class RoleProjectionTest {

    @Test
    void subtreeExpandedByParentIdWithNewChildDetection() {
        // 根 1 → 子 2 → 孙 4；新子部门 5 未挂载
        Diff diff = RoleProjectionDiffCalculator.calculate(
                Arrays.asList(new DepartmentFact(1, null, true), new DepartmentFact(2, 1L, true),
                        new DepartmentFact(3, 1L, false), new DepartmentFact(4, 2L, true),
                        new DepartmentFact(5, 2L, true)),
                1L, Arrays.asList(1L, 2L));
        assertThat(diff.getActiveSubtree()).containsExactly(1L, 2L, 4L, 5L); // 停用 3 排除
        assertThat(diff.getToMount()).containsExactly(4L, 5L);
        assertThat(diff.getToUnmount()).isEmpty();
        assertThat(diff.isSubtreeChanged()).isTrue();
    }

    @Test
    void migratedOutDepartmentUnmounts() {
        // 已迁出部门不会出现在当前根的完整目录，但旧挂载仍需摘除。
        Diff diff = RoleProjectionDiffCalculator.calculate(
                Arrays.asList(new DepartmentFact(1, null, true), new DepartmentFact(2, 1L, true)),
                1L, Arrays.asList(1L, 2L, 4L));
        assertThat(diff.getActiveSubtree()).containsExactly(1L, 2L);
        assertThat(diff.getToUnmount()).containsExactly(4L);
    }

    @Test
    void unrelatedCycleInvalidatesAClaimedCompleteSubtree() {
        // 官方完整子树不能混入根外环，整份目录拒绝，不用部分目录产生授予。
        assertThatThrownBy(() -> RoleProjectionDiffCalculator.calculate(
                Arrays.asList(new DepartmentFact(1, null, true), new DepartmentFact(2, 3L, true),
                        new DepartmentFact(3, 2L, true)),
                1L, Arrays.asList(1L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reachableCycleRejectsThePermissionPlan() {
        // 1→2→1 可达环：整份目录拒绝，不能只标变更后继续授权。
        assertThatThrownBy(() -> RoleProjectionDiffCalculator.calculate(
                Arrays.asList(new DepartmentFact(1, 2L, true), new DepartmentFact(2, 1L, true)),
                1L, Collections.<Long>emptyList())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inactiveAncestorSuppressesActiveDescendant() {
        Diff diff = RoleProjectionDiffCalculator.calculate(Arrays.asList(new DepartmentFact(1, null, true),
                new DepartmentFact(2, 1L, false), new DepartmentFact(3, 2L, true)), 1L, Arrays.asList(1L, 3L));
        assertThat(diff.getActiveSubtree()).containsExactly(1L);
        assertThat(diff.getToUnmount()).containsExactly(3L);
    }

    @Test
    void duplicateAndMissingRootCannotCreatePermissionPlan() {
        assertThatThrownBy(() -> RoleProjectionDiffCalculator.calculate(Arrays.asList(
                new DepartmentFact(1, null, true), new DepartmentFact(1, null, true)), 1L, Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoleProjectionDiffCalculator.calculate(Collections.singletonList(
                new DepartmentFact(2, null, true)), 1L, Collections.emptyList())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualPlanConfirmAndSupersedeSemantics() {
        RoleProjectionPlan plan = RoleProjectionPlan.builder("plan-1", 3L)
                .environment("env-inst-1", 2)
                .target("cu-1", "p-1", 9L, "lic_proj_uuid_1")
                .desiredRules(Collections.<String, Object>singletonMap("pagePermissions",
                        Arrays.asList("product:page")), 5L)
                .departments(Arrays.asList(1L, 2L), Arrays.asList(2L), Collections.<Long>emptyList())
                .validUntil(2000L)
                .build();
        assertThat(plan.getState()).isEqualTo(RoleProjectionPlan.State.PENDING_EXECUTION);
        // 过期清单拒绝确认
        assertThat(plan.confirmManually(2000L)).isFalse();
        assertThat(plan.confirmManually(1999L)).isTrue();
        assertThat(plan.getState()).isEqualTo(RoleProjectionPlan.State.MANUALLY_CONFIRMED);
        // 已确认清单不可重复确认，也不可被 supersede 回退
        assertThat(plan.confirmManually(1998L)).isFalse();
        plan.supersede();
        assertThat(plan.getState()).isEqualTo(RoleProjectionPlan.State.MANUALLY_CONFIRMED);
    }

    @Test
    void pendingPlanSupersededByNewExpectation() {
        RoleProjectionPlan plan = RoleProjectionPlan.builder("plan-2", 1L)
                .validUntil(9999L).build();
        plan.supersede();
        assertThat(plan.getState()).isEqualTo(RoleProjectionPlan.State.SUPERSEDED);
        assertThat(plan.confirmManually(1L)).isFalse();
    }
}
