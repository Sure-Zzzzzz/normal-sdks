package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 治理视角跨钥策略分页查询结果。
 *
 * @author surezzzzzz
 */
public final class KmsAdminPolicyPage {

    private final List<KmsAdminPolicyEntry> items;
    private final long total;

    /**
     * 创建稳定排序的跨钥策略分页投影。
     */
    public KmsAdminPolicyPage(List<KmsAdminPolicyEntry> items, long total) {
        this.items = Collections.unmodifiableList(new ArrayList<KmsAdminPolicyEntry>(items));
        this.total = total;
    }

    /**
     * 获取当前页策略条目。
     */
    public List<KmsAdminPolicyEntry> getItems() {
        return items;
    }

    /**
     * 获取筛选后策略总数。
     */
    public long getTotal() {
        return total;
    }

    /**
     * 单条策略的管理页投影：领域策略附加密钥别名与创建时间。
     */
    public static final class KmsAdminPolicyEntry {

        private final KmsKeyPolicy policy;
        private final String keyAlias;
        private final java.time.Instant createdAt;

        /**
         * 创建策略管理页条目。
         *
         * @param policy    已验证的策略领域对象
         * @param keyAlias  逻辑密钥别名
         * @param createdAt 策略创建时间
         */
        public KmsAdminPolicyEntry(KmsKeyPolicy policy, String keyAlias, java.time.Instant createdAt) {
            this.policy = policy;
            this.keyAlias = keyAlias;
            this.createdAt = createdAt;
        }

        public KmsKeyPolicy getPolicy() {
            return policy;
        }

        public String getKeyAlias() {
            return keyAlias;
        }

        public java.time.Instant getCreatedAt() {
            return createdAt;
        }
    }
}
