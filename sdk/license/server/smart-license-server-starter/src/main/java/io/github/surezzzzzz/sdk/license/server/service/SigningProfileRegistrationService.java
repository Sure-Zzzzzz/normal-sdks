package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.core.spi.KeyMaterialPort;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 签名档案登记服务：登记期钥材料严格校验 + 不可变历史的原子轮换。
 *
 * <p>档案是历史记录：轮换=同一事务内旧档案 RETIRED（expectedRowVersion CAS）+新档案 ACTIVE，
 * RETIRED 行永久保留供旧凭据验签与审计；表级 active_marker 生成列唯一键兜底并发首建双 ACTIVE。
 * 钥材料读取在行锁持有期内完成（防锁等待期间钥被销毁的窗口），失败按签名服务不可用回滚。</p>
 *
 * @author surezzzzzz
 */
@lombok.extern.slf4j.Slf4j
@SimpleLicenseServerComponent
public class SigningProfileRegistrationService {

    /** ES256/P-256 的 SPKI 固定字节长（X.509 头 26 字节 + 未压缩点 65 字节）。 */
    private static final int P256_SPKI_BYTES = 91;

    private final SigningProfileRepository profiles;
    private final ProductRepository products;
    private final KeyMaterialPort keyMaterial;

    public SigningProfileRegistrationService(SigningProfileRepository profiles,
                                             ProductRepository products, KeyMaterialPort keyMaterial) {
        this.profiles = profiles;
        this.products = products;
        this.keyMaterial = keyMaterial;
    }

    /** 登记结果：新档案 + 被轮换退役的旧档案编号（首建为 null）。 */
    public static final class Registration {

        public final SigningProfileEntity profile;
        public final String rotatedFrom;

        Registration(SigningProfileEntity profile, String rotatedFrom) {
            this.profile = profile;
            this.rotatedFrom = rotatedFrom;
        }
    }

    /**
     * 登记或轮换：产品×用途至多一个 ACTIVE；已有在用档案时必须携带期望档案
     * （expectedProfileId+expectedRowVersion 双匹配：版本号在不同档案间会撞初始值，
     * 身份+版本一起比对才无歧义）。并发轮换一个成功其余 409；材料校验任何一项
     * 不符即 422 拒绝，不留半登记状态。
     */
    @Transactional
    public Registration register(String productId, String purpose, String kid, String keyRef,
                                 int keyVersion, String expectedProfileId, Long expectedRowVersion) {
        products.findById(productId).orElseThrow(LicenseHttpException::notFound);
        Optional<SigningProfileEntity> active;
        try {
            active = profiles.lockActive(productId, purpose);
        } catch (org.springframework.dao.CannotAcquireLockException lockTimeout) {
            // 悲观锁等待超时（对方登记事务长时间未提交）：归一并发冲突，调用方回读后重试
            log.warn("签名档案登记锁等待超时：productId={}, purpose={}", productId, purpose);
            throw LicenseHttpException.conflict("并发登记冲突，请回读后重试");
        }
        if (active.isPresent() && expectedRowVersion == null) {
            throw LicenseHttpException.conflict("已存在在用档案，轮换必须携带 expectedProfileId/expectedRowVersion");
        }
        if (!active.isPresent() && expectedProfileId != null) {
            // 期望的在用档案已不在用（并发轮换已发生或已退役）：拒绝，调用方回读后重试
            throw LicenseHttpException.conflict("期望的在用档案已不在用，请回读后重试");
        }
        // 严格校验+固化公钥快照（行锁持有期内读取，材料与切换原子）
        String publicKeyHex = validateAndSnapshot(keyRef, keyVersion);
        String rotatedFrom = null;
        if (active.isPresent()) {
            SigningProfileEntity previous = active.get();
            if (!previous.getProfileId().equals(expectedProfileId)
                    || expectedRowVersion.longValue() != previous.getRowVersion()) {
                throw LicenseHttpException.conflict("版本已变化，请回读后重试");
            }
            previous.retire();
            // 退役先落库（active_marker→NULL）再插新在用行：Hibernate 默认 INSERT 先于 UPDATE，
            // 不强制刷新会瞬态双 ACTIVE 撞兜底唯一键
            profiles.saveAndFlush(previous);
            rotatedFrom = previous.getProfileId();
        }
        SigningProfileEntity created;
        try {
            created = profiles.save(new SigningProfileEntity(
                    "sp-" + UUID.randomUUID(), productId, purpose, kid, keyRef, keyVersion, publicKeyHex));
            profiles.flush();
        } catch (DataIntegrityViolationException race) {
            // active_marker 生成列唯一键兜底（并发首建/轮换同键）：一个成功，其余 409
            log.warn("签名档案并发登记冲突：productId={}, purpose={}, kid已登记指纹={}",
                    productId, purpose, fingerprintOf(kid));
            throw LicenseHttpException.conflict("并发登记冲突，请回读后重试");
        }
        log.info("签名档案登记完成：profileId={}, productId={}, purpose={}, rotatedFrom={}, keyRef指纹={}, keyVersion={}",
                created.getProfileId(), productId, purpose, rotatedFrom, fingerprintOf(keyRef), keyVersion);
        return new Registration(created, rotatedFrom);
    }

    /**
     * 登记期严格校验：回显 keyRef/版本一致、算法 ES256、版本状态与逻辑钥状态均可签、
     * SPKI 可解析为 P-256 域参数且长度 91——任何一项不符拒绝登记（不退化为长度检查）。
     */
    private String validateAndSnapshot(String keyRef, int keyVersion) {
        KeyMaterialPort.KeyMaterial material;
        try {
            material = keyMaterial.readKeyMaterial(keyRef, keyVersion);
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            log.warn("签名服务读取钥材料失败：keyRef指纹={}, keyVersion={}, 异常类型={}",
                    fingerprintOf(keyRef), keyVersion, failure.getClass().getSimpleName());
            throw LicenseHttpException.dependencyUnavailable("签名服务暂不可用");
        }
        if (material == null || material.getKeyRef() == null || material.getVersion() != keyVersion
                || !keyRef.equals(material.getKeyRef())) {
            throw LicenseHttpException.unprocessable("签名服务返回的钥引用或版本与请求不符");
        }
        if (!"ES256".equals(material.getAlgorithm())) {
            throw LicenseHttpException.unprocessable("仅支持 ES256 钥（返回算法不符）");
        }
        if (!"ACTIVE".equals(material.getVersionState()) || !"ACTIVE".equals(material.getKeyState())) {
            throw LicenseHttpException.unprocessable("钥不可用于新签名（版本或逻辑钥状态非 ACTIVE）");
        }
        byte[] spki = material.getSpki();
        if (spki.length != P256_SPKI_BYTES) {
            throw LicenseHttpException.unprocessable("签名服务返回的公钥形态不符");
        }
        try {
            // 解析即强校验：仅认 P-256 域参数（secp256r1），其他 256 位曲线拒绝
            Es256Primitives.parseSpkiPublicKey(spki);
        } catch (RuntimeException failure) {
            throw LicenseHttpException.unprocessable("签名服务返回的公钥形态不符");
        }
        StringBuilder hex = new StringBuilder(spki.length * 2);
        for (byte b : spki) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** 日志脱敏：完整标识不出日志，只出 SHA-256 前 8 位指纹。 */
    public static String fingerprintOf(String value) {
        if (value == null || value.isEmpty()) {
            return "none";
        }
        try {
            byte[] hashed = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                text.append(String.format("%02x", hashed[i]));
            }
            return text + "…";
        } catch (Exception failure) {
            return "unknown";
        }
    }
}
