package io.github.surezzzzzz.sdk.license.server.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * 按父关系验证完整授权子树并计算真实挂载差异；停用祖先阻断全部后代。
 * 不完整、重复、环或越出根的目录不能产生授权计划。
 *
 * @author surezzzzzz
 */
public final class RoleProjectionDiffCalculator {
    private static final int MAX_DEPARTMENTS = 10000;

    @Value
    public static class DepartmentFact { long id; Long parentId; boolean active; }

    @Value
    public static class Diff {
        List<Long> activeSubtree;
        List<Long> toMount;
        List<Long> toUnmount;
        boolean subtreeChanged;
    }

    private RoleProjectionDiffCalculator() { }

    /** 完整目录中每个条目必须能沿父链到达授权根，且根不得反向指入子树。 */
    public static Diff calculate(List<DepartmentFact> facts, long rootDepartmentId, List<Long> currentlyMounted) {
        if (facts == null || facts.isEmpty() || facts.size() > MAX_DEPARTMENTS || currentlyMounted == null) {
            throw new IllegalArgumentException("组织目录不完整或超过预算");
        }
        Map<Long, DepartmentFact> byId = new HashMap<>();
        for (DepartmentFact fact : facts) {
            if (fact == null || fact.getId() <= 0 || byId.put(fact.getId(), fact) != null) {
                throw new IllegalArgumentException("组织目录存在重复或非法编号");
            }
        }
        DepartmentFact root = byId.get(rootDepartmentId);
        if (root == null || root.getParentId() != null && byId.containsKey(root.getParentId())) {
            throw new IllegalArgumentException("组织目录缺根或根父关系成环");
        }
        List<Long> active = new ArrayList<>();
        for (DepartmentFact fact : facts) {
            Set<Long> seen = new HashSet<>();
            DepartmentFact current = fact;
            boolean enabled = true;
            while (true) {
                if (current == null || !seen.add(current.getId())) {
                    throw new IllegalArgumentException("组织目录缺父、越界或成环");
                }
                enabled &= current.isActive();
                if (current.getId() == rootDepartmentId) { break; }
                current = byId.get(current.getParentId());
            }
            if (enabled) { active.add(fact.getId()); }
        }
        Collections.sort(active);
        Set<Long> mounted = new LinkedHashSet<>(currentlyMounted);
        if (mounted.size() != currentlyMounted.size() || mounted.contains(null)) {
            throw new IllegalArgumentException("角色挂载清单不完整");
        }
        List<Long> toMount = new ArrayList<>(active);
        toMount.removeAll(mounted);
        List<Long> toUnmount = new ArrayList<>(mounted);
        toUnmount.removeAll(active);
        Collections.sort(toUnmount);
        return new Diff(Collections.unmodifiableList(active), Collections.unmodifiableList(toMount),
                Collections.unmodifiableList(toUnmount), !toMount.isEmpty() || !toUnmount.isEmpty());
    }
}
