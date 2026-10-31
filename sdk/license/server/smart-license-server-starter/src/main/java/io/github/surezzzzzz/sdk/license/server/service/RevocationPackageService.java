package io.github.surezzzzzz.sdk.license.server.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.CompactJws;
import io.github.surezzzzzz.sdk.license.core.codec.ProtocolHeader;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.model.SigningInput;
import io.github.surezzzzzz.sdk.license.core.spi.LicenseSigner;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationConstant;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackageEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackagePartEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationScopeEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.event.RevocationPackageCompleted;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationPackagePartRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationPackageRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationScopeRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 产品级吊销包生成服务：冻结完整产品范围、按字节预算分片、逐片签名验签后再签验 manifest。
 *
 * <p>服务不接收客户或 DATA 子集；导出来源由真实对象父关系归属产品。
 * 每次受理先分配不可复用序号，签名失败留下失败包和序号空洞，不回收序号。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@Slf4j
public class RevocationPackageService {

    private static final String PURPOSE = "REVOCATION";
    private final ProductRepository products;
    private final RevocationRepository revocations;
    private final RevocationScopeRepository scopes;
    private final RevocationPackageRepository packages;
    private final RevocationPackagePartRepository parts;
    private final SigningProfileRepository profiles;
    private final LicenseSigner signer;
    private final ApplicationEventPublisher events;

    /** 隔离领域测试构造；生产由完整构造注入正式事件发布器。 */
    public RevocationPackageService(ProductRepository products, RevocationRepository revocations,
                                    RevocationScopeRepository scopes, RevocationPackageRepository packages,
                                    RevocationPackagePartRepository parts, SigningProfileRepository profiles,
                                    LicenseSigner signer) {
        this(products, revocations, scopes, packages, parts, profiles, signer, event -> { });
    }

    /** 生产完整构造：签名终态只有在数据库事务成功提交后才交给监听器。 */
    @Autowired
    public RevocationPackageService(ProductRepository products, RevocationRepository revocations,
                                    RevocationScopeRepository scopes, RevocationPackageRepository packages,
                                    RevocationPackagePartRepository parts, SigningProfileRepository profiles,
                                    LicenseSigner signer, ApplicationEventPublisher events) {
        this.products = products;
        this.revocations = revocations;
        this.scopes = scopes;
        this.packages = packages;
        this.parts = parts;
        this.profiles = profiles;
        this.signer = signer;
        this.events = events;
    }

    /** 生成并发布 READY 包；重复幂等摘要直接返回既有包。 */
    @Transactional(noRollbackFor = RuntimeException.class)
    public RevocationPackageEntity create(String productId, String idempotencyDigest) {
        // 产品始终先存在；先锁它再查建范围，使首次并发与后续导出采用相同的数据库屏障。
        ProductEntity product = products.lockByProductId(productId).orElseThrow(LicenseHttpException::notFound);
        log.debug("吊销包创建已取得产品锁：productId={}", productId);
        SigningProfileEntity profile = profiles.findByProductIdAndPurposeAndStatus(productId, PURPOSE, "ACTIVE")
                .orElseThrow(() -> LicenseHttpException.conflict("产品尚未登记 REVOCATION 签名档案"));
        RevocationScopeEntity scope = scopes.lockByIssuerIdAndProductId(product.getIssuerId(), productId).orElse(null);
        if (scope == null) {
            // 同一产品的其他创建者仍阻塞在产品锁，唯一键竞争不会污染当前事务。
            scope = scopes.saveAndFlush(new RevocationScopeEntity(
                    "rs-" + UUID.randomUUID(), product.getIssuerId(), productId));
            log.debug("首次吊销范围已初始化：productId={}", productId);
        }
        // 在产品范围锁内回读幂等包，使同一产品的并发重放在分配序号前收敛到首包。
        RevocationPackageEntity existing = packages.findByIdempotencyDigest(idempotencyDigest).orElse(null);
        if (existing != null) {
            if (!productId.equals(existing.getProductId())) {
                throw LicenseHttpException.conflict("幂等键已绑定其他产品");
            }
            log.debug("吊销包幂等回读：packageId={}，state={}", existing.getPackageId(), existing.getState());
            return existing;
        }
        long sequence = scope.allocateSequence();
        scope.allocateTrustVersion();
        scope = scopes.saveAndFlush(scope);
        String operationId = "rop-" + UUID.randomUUID();
        String packageId = "rp-" + UUID.randomUUID();
        String snapshotId = "rsnap-" + UUID.randomUUID();
        long issuedAt = Instant.now().getEpochSecond();
        RevocationPackageEntity entity = new RevocationPackageEntity(packageId, operationId,
                product.getIssuerId(), productId, sequence, snapshotId, profile.getProfileId(),
                profile.getKid(), profile.getKeyVersion(), idempotencyDigest);
        // 分配式 ID 可能使 JPA 走 merge；后续状态变更必须继续使用返回的受管实例和行版本。
        entity = packages.saveAndFlush(entity);
        try {
            entity.markSigning();
            entity = packages.saveAndFlush(entity);
            List<RevocationEntity> source = revocations.findForProduct(productId);
            List<String> signedParts = signParts(product.getIssuerId(), productId, sequence, issuedAt, snapshotId,
                    profile, operationId, source);
            List<String> digests = new ArrayList<String>();
            for (int i = 0; i < signedParts.size(); i++) {
                String compact = signedParts.get(i);
                String digest = sha256(compact.getBytes(StandardCharsets.UTF_8));
                digests.add(digest);
                parts.save(new RevocationPackagePartEntity(packageId, i, compact, digest,
                        countEntries(compact)));
            }
            parts.flush();
            String manifest = signManifest(product.getIssuerId(), productId, sequence, issuedAt, snapshotId,
                    profile, operationId, signedParts.size(), digests);
            entity.markReady(manifest, signedParts.size(), sha256(manifest.getBytes(StandardCharsets.UTF_8)));
            entity = packages.saveAndFlush(entity);
            // 与 READY 元数据同一事务提交，只推进最高成功导出水位，不声称客户端已安装。
            scope.confirmExportedSequence(sequence);
            scope = scopes.saveAndFlush(scope);
            log.debug("吊销包已完成签名：packageId={}，sequence={}，partCount={}",
                    packageId, sequence, signedParts.size());
            publishAfterCommit(entity);
            return entity;
        } catch (RuntimeException failure) {
            entity.markFailed(category(failure));
            entity = packages.saveAndFlush(entity);
            log.debug("吊销包签名失败已保留序号：packageId={}，sequence={}，failureType={}",
                    packageId, sequence, category(failure));
            publishAfterCommit(entity);
            throw failure;
        }
    }

    /**
     * 只观察真正提交的 READY/FAILED；消费者异常不改变已持久化结果，也不掩盖原始签名异常。
     */
    private void publishAfterCommit(RevocationPackageEntity entity) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("吊销包未登记提交事件：packageId={}，无受管事务", entity.getPackageId());
            return;
        }
        final RevocationPackageCompleted event = new RevocationPackageCompleted(entity.getPackageId(),
                entity.getOperationId(), entity.getProductId(), entity.getSequence(), entity.getState(),
                entity.getReasonCategory());
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        events.publishEvent(event);
                        log.debug("吊销包终态事件已发布：packageId={}，sequence={}，state={}",
                                event.getPackageId(), event.getSequence(), event.getState());
                    } catch (RuntimeException failure) {
                        log.warn("吊销包终态事件消费失败：packageId={}，state={}，exceptionType={}",
                                event.getPackageId(), event.getState(), failure.getClass().getSimpleName());
                    }
                }
            });
        } catch (RuntimeException failure) {
            log.warn("吊销包终态事件登记失败：packageId={}，state={}，exceptionType={}",
                    event.getPackageId(), event.getState(), failure.getClass().getSimpleName());
        }
    }

    public RevocationPackageEntity get(String packageId) {
        return packages.findById(packageId).orElseThrow(LicenseHttpException::notFound);
    }

    public List<RevocationPackagePartEntity> content(String packageId) {
        RevocationPackageEntity entity = get(packageId);
        if (!"READY".equals(entity.getState())) {
            throw LicenseHttpException.conflict("吊销包尚未 READY");
        }
        return parts.findByPackageIdOrderByPartIndexAsc(packageId);
    }

    /**
     * 公开客户端同步面：返回最近 READY 吊销包与已登记的 REVOCATION 公钥资料。
     *
     * <p>knownSequence 作为后续增量同步的兼容输入保留；当前协议要求返回完整最新快照，
     * 调用方负责验签并按序号只增安装。</p>
     */
    public ObjectNode trustMaterial(String productId, long knownSequence) {
        ProductEntity product = products.findById(productId).orElseThrow(LicenseHttpException::notFound);
        RevocationPackageEntity latest = packages
                .findTopByProductIdAndStateOrderBySequenceDesc(productId, "READY")
                .orElseThrow(() -> LicenseHttpException.conflict("产品尚无 READY 吊销包"));
        RevocationScopeEntity scope = scopes.findByIssuerIdAndProductId(product.getIssuerId(), productId)
                .orElseThrow(() -> LicenseHttpException.conflict("产品吊销范围未登记"));
        long trustVersion = Math.max(1L, Math.max(scope.getTrustVersion(), latest.getSequence()));
        ObjectNode body = StrictJson.mapper().createObjectNode();
        body.put("issuerId", product.getIssuerId());
        body.put("productId", productId);
        body.put("sequence", latest.getSequence());
        body.put("issuedAt", issuedAt(latest.getManifestJws()));
        body.put("snapshotId", latest.getSnapshotId());
        body.put("manifestJws", latest.getManifestJws());
        ArrayNode partsNode = body.putArray("revocationPartJws");
        for (RevocationPackagePartEntity part : content(latest.getPackageId())) {
            partsNode.add(part.getPartJws());
        }
        ArrayNode keys = body.putArray("trustedPublicKeysJson");
        for (SigningProfileEntity profile : profiles.findByProductIdAndPurpose(productId, PURPOSE)) {
            ObjectNode key = StrictJson.mapper().createObjectNode();
            key.put("issuerId", product.getIssuerId());
            key.put("productId", productId);
            key.put("kid", profile.getKid());
            key.put("algorithm", "ES256");
            key.put("purpose", PURPOSE);
            key.put("trustVersion", trustVersion);
            key.put("spkiBase64", Base64Url.encode(hex(profile.getRegisteredPublicKeyHex())));
            // 客户端协议字段是 string[]：每个元素保留一条完整的公钥 JSON 文本，
            // 由受控宿主决定是否安装信任根，服务端不借响应自动升级信任。
            keys.add(StrictJson.write(key));
        }
        return body;
    }

    private List<String> signParts(String issuerId, String productId, long sequence, long issuedAt, String snapshotId,
                                   SigningProfileEntity profile, String operationId,
                                   List<RevocationEntity> source) {
        List<String> result = new ArrayList<String>();
        List<RevocationEntity> current = new ArrayList<RevocationEntity>();
        int index = 0;
        for (RevocationEntity item : source) {
            current.add(item);
            // 候选允许比预算多一条，先测实际 UTF-8 长度；最终编码仍使用 StrictJson 的硬门禁。
            if (partPayloadBytes(issuerId, productId, sequence, issuedAt, snapshotId, index, current)
                    > SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES) {
                current.remove(current.size() - 1);
                if (current.isEmpty()) {
                    throw LicenseHttpException.unprocessable(RevocationConstant.PACKAGE_ENTRY_TOO_LARGE);
                }
                result.add(signPart(issuerId, productId, sequence, issuedAt, snapshotId, index, current, profile, operationId));
                index++;
                current.clear();
                current.add(item);
                if (partPayloadBytes(issuerId, productId, sequence, issuedAt, snapshotId, index, current)
                        > SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES) {
                    throw LicenseHttpException.unprocessable(RevocationConstant.PACKAGE_ENTRY_TOO_LARGE);
                }
            }
            if (current.size() >= SmartLicenseCoreConstant.REVOCATION_ENTRIES_PER_PART_MAX) {
                result.add(signPart(issuerId, productId, sequence, issuedAt, snapshotId, index, current, profile, operationId));
                index++;
                current.clear();
            }
        }
        if (current.isEmpty() && result.isEmpty()) {
            result.add(signPart(issuerId, productId, sequence, issuedAt, snapshotId, 0, current, profile, operationId));
        } else if (!current.isEmpty()) {
            result.add(signPart(issuerId, productId, sequence, issuedAt, snapshotId, index, current, profile, operationId));
        }
        if (result.size() > SmartLicenseCoreConstant.REVOCATION_PARTS_MAX) {
            throw LicenseHttpException.unprocessable("吊销包分片数超协议上限");
        }
        return result;
    }

    private String signPart(String issuerId, String productId, long sequence, long issuedAt, String snapshotId, int index,
                            List<RevocationEntity> entries, SigningProfileEntity profile, String operationId) {
        String compact = encodePart(issuerId, productId, sequence, issuedAt, snapshotId, index, entries);
        String header = header(ProtocolHeader.TYP_REVOCATION, profile.getKid());
        String payload = compact.substring(compact.indexOf('.') + 1);
        payload = payload.substring(0, payload.indexOf('.'));
        return signAndVerify(profile, operationId, header, payload);
    }

    private String signManifest(String issuerId, String productId, long sequence, long issuedAt, String snapshotId,
                                SigningProfileEntity profile, String operationId, int partCount,
                                List<String> digests) {
        ObjectNode body = common(issuerId, productId, sequence, issuedAt, snapshotId);
        body.put("partCount", partCount);
        ArrayNode values = body.putArray("partDigests");
        for (String digest : digests) values.add(digest);
        String header = header(ProtocolHeader.TYP_REVOCATION_MANIFEST, profile.getKid());
        String payload = Base64Url.encode(StrictJson.write(body).getBytes(StandardCharsets.UTF_8));
        return signAndVerify(profile, operationId, header, payload);
    }

    /**
     * 按 Compact JWS 原文签名并立即核对登记期 SPKI；分片和 manifest 共用同一信任屏障。
     * 64 字节只表示 JOSE 形态，不能作为签名有效或登记钥版本正确的证据。
     */
    private String signAndVerify(SigningProfileEntity profile, String operationId, String header, String payload) {
        byte[] input = (header + "." + payload).getBytes(StandardCharsets.US_ASCII);
        byte[] signature = signer.sign(profile.getProfileId(), operationId, SigningInput.ofBytes(input));
        if (signature == null || signature.length != SmartLicenseCoreConstant.ES256_SIGNATURE_BYTES) {
            throw LicenseHttpException.dependencyUnavailable(RevocationConstant.PACKAGE_SIGNATURE_SHAPE_INVALID);
        }
        boolean verified;
        try {
            verified = Es256Primitives.verify(input, signature,
                    Es256Primitives.parseSpkiPublicKey(hex(profile.getRegisteredPublicKeyHex())));
        } catch (RuntimeException failure) {
            log.debug("吊销包本地验签执行失败：operationId={}，exceptionType={}",
                    operationId, failure.getClass().getSimpleName());
            throw LicenseHttpException.dependencyUnavailable(RevocationConstant.PACKAGE_LOCAL_VERIFY_FAILED);
        }
        if (!verified) {
            log.debug("吊销包签名与登记公钥不符：operationId={}", operationId);
            throw LicenseHttpException.dependencyUnavailable(RevocationConstant.PACKAGE_SIGNATURE_MISMATCH);
        }
        return CompactJws.join(header, payload, Base64Url.encode(signature));
    }

    private static String encodePart(String issuerId, String productId, long sequence, long issuedAt, String snapshotId,
                                     int index, List<RevocationEntity> entries) {
        ObjectNode body = partBody(issuerId, productId, sequence, issuedAt, snapshotId, index, entries);
        String header = header(ProtocolHeader.TYP_REVOCATION, "placeholder");
        String payload = Base64Url.encode(StrictJson.write(body).getBytes(StandardCharsets.UTF_8));
        return CompactJws.join(header, payload, "placeholder");
    }

    /** 固定协议结构的候选测量不放宽最终输出预算，不把任意序列化错误当作需要拆片。 */
    private static int partPayloadBytes(String issuerId, String productId, long sequence, long issuedAt,
                                        String snapshotId, int index, List<RevocationEntity> entries) {
        try {
            return StrictJson.mapper().writeValueAsBytes(partBody(
                    issuerId, productId, sequence, issuedAt, snapshotId, index, entries)).length;
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(RevocationConstant.PACKAGE_ENCODING_FAILED, failure);
        }
    }

    private static ObjectNode partBody(String issuerId, String productId, long sequence, long issuedAt,
                                       String snapshotId, int index, List<RevocationEntity> entries) {
        ObjectNode body = common(issuerId, productId, sequence, issuedAt, snapshotId);
        body.put("partIndex", index);
        ArrayNode values = body.putArray("entries");
        for (RevocationEntity item : entries) {
            ObjectNode entry = values.addObject();
            entry.put("targetKind", item.getTargetKind());
            entry.put("targetId", item.getTargetId());
            entry.put("revokedAt", item.getRevokedAt().getEpochSecond());
        }
        return body;
    }

    private static ObjectNode common(String issuerId, String productId, long sequence, long issuedAt, String snapshotId) {
        ObjectNode body = StrictJson.mapper().createObjectNode();
        body.put("protocolVersion", SmartLicenseCoreConstant.PROTOCOL_VERSION);
        ObjectNode scope = body.putObject("scope");
        scope.put("issuerId", issuerId);
        scope.put("productId", productId);
        body.put("sequence", sequence);
        body.put("issuedAt", issuedAt);
        body.put("snapshotId", snapshotId);
        return body;
    }

    private static String header(String typ, String kid) {
        ObjectNode header = StrictJson.mapper().createObjectNode();
        header.put("alg", "ES256");
        header.put("kid", kid);
        header.put("typ", typ);
        return Base64Url.encode(StrictJson.write(header).getBytes(StandardCharsets.UTF_8));
    }

    private static int countEntries(String compact) {
        try {
            String payload = new String(Base64Url.decode(CompactJws.parse(compact).getPayloadSegment()), StandardCharsets.UTF_8);
            return StrictJson.mapper().readTree(payload).path("entries").size();
        } catch (Exception failure) {
            throw new IllegalArgumentException(RevocationConstant.PACKAGE_ENCODING_FAILED, failure);
        }
    }

    private static long issuedAt(String manifest) {
        try {
            String payload = new String(Base64Url.decode(CompactJws.parse(manifest).getPayloadSegment()),
                    StandardCharsets.UTF_8);
            return StrictJson.mapper().readTree(payload).path("issuedAt").asLong(-1L);
        } catch (Exception failure) {
            throw new IllegalStateException("吊销 manifest 损坏", failure);
        }
    }

    private static byte[] hex(String value) {
        if (value == null || value.length() % 2 != 0) {
            throw new IllegalStateException("登记公钥摘要损坏");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalStateException("登记公钥摘要损坏");
            }
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value & 255));
            return result.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("JRE 缺少 SHA-256", failure);
        }
    }

    private static String category(RuntimeException failure) {
        return failure.getClass().getSimpleName().length() > 64
                ? "SIGNING_FAILED" : failure.getClass().getSimpleName();
    }
}
