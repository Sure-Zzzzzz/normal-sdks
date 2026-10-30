package io.github.surezzzzzz.sdk.license.core.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 模型字段定义（rights 或 facts 命名空间之一）。
 *
 * <p>数值 minimum/maximum 必填且 maximum 不小于 minimum；枚举与长度显式声明；
 * facts 的 source 表示所需提供方类别（PRODUCT/DEVICE/ORGANIZATION），不证明输入可信。</p>
 *
 * @author surezzzzzz
 */
public final class FieldDefinition {

    private final String name;
    private final FieldType type;
    private final boolean required;
    private final Long minimum;
    private final Long maximum;
    private final String unit;
    private final Long maxLengthCodePoints;
    private final Long maxItems;
    private final Set<String> enumValues;
    private final FactSource source;

    private FieldDefinition(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.required = builder.required;
        this.minimum = builder.minimum;
        this.maximum = builder.maximum;
        this.unit = builder.unit;
        this.maxLengthCodePoints = builder.maxLengthCodePoints;
        this.maxItems = builder.maxItems;
        this.enumValues = builder.enumValues == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(builder.enumValues));
        this.source = builder.source;
    }

    public static Builder builder(String name, FieldType type) {
        return new Builder(name, type);
    }

    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public Long getMinimum() {
        return minimum;
    }

    public Long getMaximum() {
        return maximum;
    }

    public String getUnit() {
        return unit;
    }

    public Long getMaxLengthCodePoints() {
        return maxLengthCodePoints;
    }

    public Long getMaxItems() {
        return maxItems;
    }

    public Set<String> getEnumValues() {
        return enumValues;
    }

    public FactSource getSource() {
        return source;
    }

    /** facts 提供方类别。 */
    public enum FactSource {
        PRODUCT("PRODUCT"),
        DEVICE("DEVICE"),
        ORGANIZATION("ORGANIZATION");

        private final String code;

        FactSource(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /** 字段定义构造器：数值字段必须给 minimum/maximum，集合可给 maxItems，文本可给 maxLengthCodePoints 与枚举。 */
    public static final class Builder {

        private final String name;
        private final FieldType type;
        private boolean required;
        private Long minimum;
        private Long maximum;
        private String unit;
        private Long maxLengthCodePoints;
        private Long maxItems;
        private Set<String> enumValues;
        private FactSource source;

        private Builder(String name, FieldType type) {
            this.name = name;
            this.type = type;
        }

        public Builder required() {
            this.required = true;
            return this;
        }

        public Builder range(long minimum, long maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder maxLengthCodePoints(long max) {
            this.maxLengthCodePoints = max;
            return this;
        }

        public Builder maxItems(long max) {
            this.maxItems = max;
            return this;
        }

        public Builder enumValues(Set<String> values) {
            this.enumValues = values;
            return this;
        }

        public Builder source(FactSource source) {
            this.source = source;
            return this;
        }

        public FieldDefinition build() {
            if (type == FieldType.INTEGER && (minimum == null || maximum == null)) {
                throw new IllegalArgumentException("INTEGER 字段必须显式声明 minimum/maximum：" + name);
            }
            if (minimum != null && maximum != null && maximum < minimum) {
                throw new IllegalArgumentException("maximum 不得小于 minimum：" + name);
            }
            return new FieldDefinition(this);
        }
    }
}
