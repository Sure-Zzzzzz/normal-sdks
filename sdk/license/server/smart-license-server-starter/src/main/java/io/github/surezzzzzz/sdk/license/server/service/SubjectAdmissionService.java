package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.core.spi.IamDirectoryPort;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.entity.OrganizationBindingEntity;
import io.github.surezzzzzz.sdk.license.server.repository.OrganizationBindingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * 部门准入裁决（CUSTOMER_MEMBERS 在线判定的主体准入执行链）：
 * 绑定存在即启用——按客户绑定行回查 IAM 目录子树成员归属；
 * 无绑定客户不启用目录准入（NOT_BOUND 直通，规则链照常）。
 *
 * <p>目录不可达为UNAVAILABLE，在线入口返回依赖故障；明确主体子树外才返回业务拒绝。</p>
 *
 * @author surezzzzzz
 */
@lombok.extern.slf4j.Slf4j
@SimpleLicenseServerComponent
public class SubjectAdmissionService {

    /** 准入结论四态。 */
    public enum Admission {
        /** 未绑定组织：不启用目录准入（直通）。 */
        NOT_BOUND,
        /** 子树内：准入。 */
        ADMITTED,
        /** 子树外或主体不存在：拒绝。 */
        DENIED,
        /** 目录暂不可达：失败关闭，在线入口按依赖不可用返回503，不冒充确定业务拒绝。 */
        UNAVAILABLE
    }

    private final OrganizationBindingRepository bindings;
    private final IamDirectoryPort directory;

    public SubjectAdmissionService(OrganizationBindingRepository bindings,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                          IamDirectoryPort directory) {
        this.bindings = bindings;
        this.directory = directory;
    }

    /**
     * 裁决主体对客户的准入。
     *
     * @param customerId 客户编号
     * @param subjectId  公开主体标识（null 视为信息不足：绑定客户按拒绝）
     */
    public Admission isAdmitted(String customerId, String subjectId) {
        Optional<OrganizationBindingEntity> bound = bindings.findById(customerId);
        if (!bound.isPresent()) {
            return Admission.NOT_BOUND;
        }
        if (directory == null) {
            log.warn("部门准入失败关闭：客户指纹={} 已绑定组织但目录端口未装配",
                    identifierFingerprint(customerId));
            return Admission.UNAVAILABLE;
        }
        if (subjectId == null || subjectId.isEmpty()) {
            return Admission.DENIED;
        }
        Optional<Boolean> withinRoot;
        try {
            withinRoot = directory.isMemberWithinRoot(bound.get().getRootDepartmentId(), subjectId);
        } catch (RuntimeException failure) {
            log.warn("部门准入目录回查异常：客户指纹={} 按失败关闭处理，异常类型={}",
                    identifierFingerprint(customerId), failure.getClass().getName());
            return Admission.UNAVAILABLE;
        }
        if (!withinRoot.isPresent()) {
            return Admission.UNAVAILABLE;
        }
        return withinRoot.get() ? Admission.ADMITTED : Admission.DENIED;
    }

    /** 运行日志只保留稳定短指纹，避免把客户业务标识扩散到日志系统。 */
    private static String identifierFingerprint(String value) {
        if (value == null || value.isEmpty()) {
            return "empty";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", digest[index] & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException failure) {
            // JDK 必须提供 SHA-256；异常时不回退输出原值。
            return "unavailable";
        }
    }
}
