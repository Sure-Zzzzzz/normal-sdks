package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 已声明的规则模型：rights/facts 字段与动作规则树（发布快照的骨架部分）。
 *
 * <p>字段名 ASCII 规则、每命名空间字段上限、动作数上限构造期校验；
 * 发布级引用/类型/单位/预算校验由 {@link RuleModelValidator} 执行。</p>
 *
 * @author surezzzzzz
 */
public final class RuleModel {

    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern ACTION_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final Map<String, FieldDefinition> rights;
    private final Map<String, FieldDefinition> facts;
    private final Map<String, ConditionNode> actions;

    private RuleModel(Builder builder) {
        this.rights = Collections.unmodifiableMap(new LinkedHashMap<String, FieldDefinition>(builder.rights));
        this.facts = Collections.unmodifiableMap(new LinkedHashMap<String, FieldDefinition>(builder.facts));
        this.actions = Collections.unmodifiableMap(new LinkedHashMap<String, ConditionNode>(builder.actions));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, FieldDefinition> getRights() {
        return rights;
    }

    public Map<String, FieldDefinition> getFacts() {
        return facts;
    }

    public Map<String, ConditionNode> getActions() {
        return actions;
    }

    /** 是否存在声明动作。 */
    public boolean hasAction(String action) {
        return actions.containsKey(action);
    }

    public static final class Builder {

        private final Map<String, FieldDefinition> rights = new LinkedHashMap<String, FieldDefinition>();
        private final Map<String, FieldDefinition> facts = new LinkedHashMap<String, FieldDefinition>();
        private final Map<String, ConditionNode> actions = new LinkedHashMap<String, ConditionNode>();

        public Builder right(FieldDefinition definition) {
            putField(rights, definition);
            return this;
        }

        public Builder fact(FieldDefinition definition) {
            putField(facts, definition);
            return this;
        }

        public Builder action(String name, ConditionNode condition) {
            if (name == null || !ACTION_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
            }
            if (condition == null) {
                throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
            }
            if (actions.size() >= SmartLicenseCoreConstant.ACTIONS_PER_MODEL_MAX) {
                throw new IllegalArgumentException(ErrorMessage.RULE_BUDGET_EXCEEDED);
            }
            actions.put(name, condition);
            return this;
        }

        private static void putField(Map<String, FieldDefinition> target, FieldDefinition definition) {
            if (definition == null || !FIELD_NAME.matcher(definition.getName()).matches()) {
                throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
            }
            if (target.size() >= SmartLicenseCoreConstant.FIELDS_PER_NAMESPACE_MAX) {
                throw new IllegalArgumentException(ErrorMessage.RULE_BUDGET_EXCEEDED);
            }
            target.put(definition.getName(), definition);
        }

        public RuleModel build() {
            return new RuleModel(this);
        }
    }
}
