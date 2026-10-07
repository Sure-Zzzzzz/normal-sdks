package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 类型化不可变执行计划
 * <p>由声明、事实与单份已接受类型化快照在任何扣减前构建完成：
 * 适用门禁、每门禁选中的完整限额与同桶唯一性均已校验；
 * 执行期间不再取值或替换规则。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterTypedExecutionPlan {

    /**
     * 适用门禁（固定顺序：IP、RESOURCE、CUSTOMER、USER/SERVICE、CREDENTIAL、CUSTOM）
     */
    private final List<SmartRedisLimiterTypedGateRule> gates;

    /**
     * 扣减前拒绝（非空时计划不执行）
     */
    private final SmartRedisLimiterTypedRejection rejection;

    /**
     * 构造执行计划
     *
     * @param gates     适用门禁
     * @param rejection 扣减前拒绝；可执行计划为 null
     */
    public SmartRedisLimiterTypedExecutionPlan(List<SmartRedisLimiterTypedGateRule> gates,
                                               SmartRedisLimiterTypedRejection rejection) {
        this.gates = Collections.unmodifiableList(gates == null
                ? Collections.<SmartRedisLimiterTypedGateRule>emptyList()
                : gates);
        this.rejection = rejection;
    }

    /**
     * 判断计划是否可执行
     * <p>全部门禁不适用时计划为空但无拒绝，直接放行（零门禁零扣减）；
     * 只有存在扣减前拒绝时计划才不可执行。</p>
     *
     * @return 无扣减前拒绝时返回 true
     */
    public boolean isExecutable() {
        return rejection == null;
    }
}
