package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 管理端 v2 规则 wire 模型（仅客户端反序列化用；未知字段严格拒绝由 mapper 全局承担）
 *
 * <p>字段形态对齐管理端规则响应：id/rowVersion/enabled/身份七字段/limits（count/window/unit）。
 * 客户端不消费 createdAt/updatedAt 等扩展字段，按严格模式显式列出已知字段。</p>
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public final class TypedRuleWire {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("rowVersion")
    private Long rowVersion;

    @JsonProperty("serviceCode")
    private String serviceCode;

    @JsonProperty("resourceCode")
    private String resourceCode;

    @JsonProperty("dimension")
    private String dimension;

    @JsonProperty("selector")
    private String selector;

    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("customType")
    private String customType;

    @JsonProperty("objectId")
    private String objectId;

    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @JsonProperty("limits")
    private List<LimitWire> limits;

    public Long getId() {
        return id;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public String getDimension() {
        return dimension;
    }

    public String getSelector() {
        return selector;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getCustomType() {
        return customType;
    }

    public String getObjectId() {
        return objectId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public List<LimitWire> getLimits() {
        return limits;
    }

    /**
     * 窗口 wire（count/window/unit）
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class LimitWire {

        @JsonProperty("count")
        private Long count;

        @JsonProperty("window")
        private Long window;

        @JsonProperty("unit")
        private String unit;

        public Long getCount() {
            return count;
        }

        public Long getWindow() {
            return window;
        }

        public String getUnit() {
            return unit;
        }
    }

    /**
     * 变更响应 wire：{ rule: {...}|null, revision: long }
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class MutationWire {

        @JsonProperty("rule")
        private TypedRuleWire rule;

        @JsonProperty("revision")
        private Long revision;

        public TypedRuleWire getRule() {
            return rule;
        }

        public Long getRevision() {
            return revision;
        }
    }

    /**
     * 列表响应 wire：{ items: [...], page, size, total }
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class PageWire {

        @JsonProperty("items")
        private List<TypedRuleWire> items;

        @JsonProperty("page")
        private Long page;

        @JsonProperty("size")
        private Long size;

        @JsonProperty("total")
        private Long total;

        public List<TypedRuleWire> getItems() {
            return items;
        }

        public Long getPage() {
            return page;
        }

        public Long getSize() {
            return size;
        }

        public Long getTotal() {
            return total;
        }
    }
}
