package io.github.surezzzzzz.sdk.kms.server.configuration;

import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KMS Server 配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SmartKmsServerConstant.CONFIG_PREFIX)
public class SmartKmsServerProperties {

    /**
     * 是否启用 Server 默认链路。
     */
    private Boolean enable = SmartKmsServerConstant.DEFAULT_ENABLED;
    /**
     * 分页配置。
     */
    private Page page = new Page();
    /**
     * 幂等配置。
     */
    private Idempotency idempotency = new Idempotency();
    /**
     * 密码学 HTTP 输入输出上限。
     */
    private Crypto crypto = new Crypto();
    /**
     * 销毁 worker 配置。
     */
    private Worker worker = new Worker();

    /**
     * 分页配置。
     */
    @Data
    public static class Page {
        /**
         * 默认分页大小。
         */
        private Integer defaultSize = SmartKmsServerConstant.DEFAULT_PAGE_SIZE;
        /**
         * 最大分页大小。
         */
        private Integer maxSize = SmartKmsServerConstant.MAX_PAGE_SIZE;
    }

    /**
     * 幂等配置。
     */
    @Data
    public static class Idempotency {
        /**
         * 成功记录保留秒数。
         */
        private Long retentionSeconds = SmartKmsServerConstant.DEFAULT_IDEMPOTENCY_RETENTION_SECONDS;
    }

    /**
     * 密码学 HTTP 输入输出上限。
     */
    @Data
    public static class Crypto {
        /**
         * 签名或验签输入最大字节数。
         */
        private Integer maxSigningInputBytes = SmartKmsServerConstant.DEFAULT_MAX_SIGNING_INPUT_BYTES;
        /**
         * ES256 JOSE 签名最大字节数。
         */
        private Integer maxSignatureBytes = SmartKmsServerConstant.DEFAULT_MAX_SIGNATURE_BYTES;
        /**
         * 明文最大字节数。
         */
        private Integer maxPlaintextBytes = SmartKmsServerConstant.DEFAULT_MAX_PLAINTEXT_BYTES;
        /**
         * 外部 AAD 最大字节数。
         */
        private Integer maxAadBytes = SmartKmsServerConstant.DEFAULT_MAX_AAD_BYTES;
        /**
         * SKMS 封装最大字节数。
         */
        private Integer maxEnvelopeBytes = SmartKmsServerConstant.DEFAULT_MAX_ENVELOPE_BYTES;
    }

    /**
     * 销毁 worker 配置。
     */
    @Data
    public static class Worker {
        /**
         * 是否启用销毁 worker。
         */
        private Boolean enable = SmartKmsServerConstant.DEFAULT_WORKER_ENABLED;
        /**
         * KMS 服务实例标识；显式配置优先，未配置时当前进程自动生成 UUID，仅用于 worker 健康状态和排障归属。
         */
        private String instanceId;
        /**
         * 扫描间隔毫秒数。
         */
        private Long scanIntervalMillis = SmartKmsServerConstant.DEFAULT_WORKER_SCAN_INTERVAL_MILLIS;
        /**
         * 任务租约秒数。
         */
        private Long leaseSeconds = SmartKmsServerConstant.DEFAULT_WORKER_LEASE_SECONDS;
        /**
         * 连续失败阈值。
         */
        private Integer maxConsecutiveFailures = SmartKmsServerConstant.DEFAULT_WORKER_MAX_CONSECUTIVE_FAILURES;
    }
}
