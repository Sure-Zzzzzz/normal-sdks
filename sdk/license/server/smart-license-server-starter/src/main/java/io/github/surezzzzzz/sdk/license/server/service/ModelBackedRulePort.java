package io.github.surezzzzzz.sdk.license.server.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.RuleModelWire;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.ConditionEvaluator;
import io.github.surezzzzzz.sdk.license.core.model.FactValues;
import io.github.surezzzzzz.sdk.license.core.model.RuleModel;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.LicenseValidationConstant;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.HashMap;
import java.util.Map;

/**
 * 在线判定规则端口生产实现：已保存模型进入求值。
 *
 * <p>授权关联已发布模型时，动作判定=模型动作树对（授权权益∪请求事实）求值——
 * 条件不满足即拒绝；未关联真实模型按依赖缺失处理，不用产品动作白名单补造规则。
 * 求值期缺事实按确定拒绝，不猜默认值。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
public class ModelBackedRulePort implements OnlineDecisionService.RuleEvaluationPort {

    private final GrantRepository grants;
    private final ModelVersionRepository models;
    public ModelBackedRulePort(GrantRepository grants, ModelVersionRepository models) {
        this.grants = grants;
        this.models = models;
    }

    @Override
    public boolean evaluate(String grantId, String action, Map<String, Object> facts) {
        return evaluateResult(grantId, action, facts).isAllowed();
    }

    @Override
    public boolean allowsCommercialGrace(String grantId, String action) {
        GrantRevisionEntity grant = grants.findById(grantId).orElse(null);
        ModelVersionEntity model = grant == null || grant.getModelVersionId() == null ? null
                : models.findById(grant.getModelVersionId()).orElse(null);
        if (model == null || !grant.getProductId().equals(model.getProductId())
                || !("PUBLISHED".equals(model.getStatus()) || "RETIRED".equals(model.getStatus()))) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        com.fasterxml.jackson.databind.JsonNode actions;
        try {
            actions = io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire
                    .parse(model.getSnapshot()).path("validityPolicy").path("graceActions");
        } catch (RuntimeException invalidStoredModel) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        for (com.fasterxml.jackson.databind.JsonNode value : actions) {
            if (action.equals(value.textValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public OnlineDecisionService.RuleEvaluation evaluateResult(String grantId, String action, Map<String, Object> facts) {
        GrantRevisionEntity grant = grants.findById(grantId).orElse(null);
        if (grant == null) {
            return new OnlineDecisionService.RuleEvaluation(false, 0L);
        }
        if (grant.getModelVersionId() == null) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        ModelVersionEntity stored = models.findById(grant.getModelVersionId()).orElse(null);
        if (stored == null || !grant.getProductId().equals(stored.getProductId()) || stored.getVersion() < 1L
                || !("PUBLISHED".equals(stored.getStatus()) || "RETIRED".equals(stored.getStatus()))) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        RuleModel model = parse(stored.getSnapshot());
        if (!model.getActions().containsKey(action)) {
            return new OnlineDecisionService.RuleEvaluation(false, stored.getVersion());
        }
        Map<String, Object> rights = rightsOf(grant);
        try {
            return new OnlineDecisionService.RuleEvaluation(ConditionEvaluator.evaluate(model, action,
                    FactValues.of(model, rights, facts, false)), stored.getVersion());
        } catch (LicenseCoreException | IllegalArgumentException | FactValues.MissingFactException missingOrInvalid) {
            // 缺事实/类型不符=确定拒绝，不猜默认值
            return new OnlineDecisionService.RuleEvaluation(false, stored.getVersion());
        }
    }

    private static RuleModel parse(String snapshot) {
        try {
            return RuleModelWire.fromSnapshot(StrictJson.parse(snapshot, ObjectNode.class));
        } catch (RuntimeException failure) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
    }

    private static Map<String, Object> rightsOf(GrantRevisionEntity grant) {
        try {
            Map<String, Object> rights = new HashMap<String, Object>();
            StrictJson.mapper().readValue(grant.getRightsJson() == null ? "{}" : grant.getRightsJson(),
                    Map.class).forEach((key, value) -> rights.put(String.valueOf(key), value));
            return rights;
        } catch (Exception failure) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
    }

}
