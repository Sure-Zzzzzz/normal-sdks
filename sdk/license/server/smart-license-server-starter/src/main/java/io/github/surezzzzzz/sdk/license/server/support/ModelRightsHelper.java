package io.github.surezzzzzz.sdk.license.server.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.FactValues;
import io.github.surezzzzzz.sdk.license.core.model.RuleModel;
import io.github.surezzzzzz.sdk.license.server.constant.LicenseValidationConstant;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Collections;
import java.util.Map;

/**
 * 方案预填与商业授权共同复用核心模型的权益校验，避免管理面和签发面语义漂移。
 *
 * @author surezzzzzz
 */
public final class ModelRightsHelper {

    private ModelRightsHelper() {
    }

    /**
     * 校验权益对象；方案允许部分预填，商业授权必须满足模型中的全部必填权益。
     *
     * @param node 权益 JSON 对象，未提交表示空对象
     * @param model 已发布规则模型
     * @param required 是否校验完整必填权益
     * @return 保持四种权益类型的 JSON 文本
     */
    public static String validate(JsonNode node, RuleModel model, boolean required) {
        JsonNode rights = node == null || node.isNull() ? StrictJson.mapper().createObjectNode() : node;
        if (!rights.isObject()) {
            throw LicenseHttpException.unprocessable(LicenseValidationConstant.RIGHTS_INVALID);
        }
        try {
            Map<String, Object> values = StrictJson.mapper().convertValue(rights,
                    new TypeReference<Map<String, Object>>() { });
            FactValues.of(model, values, Collections.<String, Object>emptyMap(), required);
            return rights.toString();
        } catch (IllegalArgumentException invalid) {
            throw LicenseHttpException.unprocessable(LicenseValidationConstant.RIGHTS_INVALID);
        }
    }

    /** 从持久模型快照读取相同发布级规则，坏快照不能绕过管理面校验。 */
    public static RuleModel model(String snapshot) {
        try {
            return io.github.surezzzzzz.sdk.license.core.codec.RuleModelWire.fromSnapshot(
                    StrictJson.parse(snapshot, ObjectNode.class));
        } catch (IllegalArgumentException invalid) {
            throw LicenseHttpException.unprocessable(LicenseValidationConstant.RIGHTS_INVALID);
        }
    }
}
