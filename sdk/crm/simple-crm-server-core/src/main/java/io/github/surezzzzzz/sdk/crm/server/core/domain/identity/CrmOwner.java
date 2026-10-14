package io.github.surezzzzzz.sdk.crm.server.core.domain.identity;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * CRM 归属主体：商业事实的创建者归属。
 *
 * <p>人=IAM 人员（owner 形如 {@code iam:{subjectId}}），服务凭证=AKSK 客户端（owner 形如
 * {@code aksk:{clientId}}）。owner 只能由入口适配器从已验证请求上下文派生，永不出现在请求体；
 * 领域记录与持久化层保存 owner 字符串。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class CrmOwner {

    /**
     * 人员主体前缀。
     */
    public static final String PREFIX_HUMAN = "iam:";

    /**
     * 服务凭证主体前缀。
     */
    public static final String PREFIX_SERVICE = "aksk:";

    private final String owner;
    private final CrmSubjectType subjectType;

    private CrmOwner(String owner, CrmSubjectType subjectType) {
        this.owner = CrmValidationHelper.required(owner, "owner");
        this.subjectType = CrmValidationHelper.requiredObject(subjectType, "subjectType");
    }

    /**
     * 构造 IAM 人员归属主体。
     *
     * @param subjectId IAM 人员标识
     * @return 人员归属主体
     */
    public static CrmOwner human(String subjectId) {
        return new CrmOwner(PREFIX_HUMAN + CrmValidationHelper.required(subjectId, "subjectId"),
                CrmSubjectType.HUMAN);
    }

    /**
     * 构造 AKSK 服务凭证归属主体。
     *
     * @param clientId AKSK 客户端标识
     * @return 服务凭证归属主体
     */
    public static CrmOwner service(String clientId) {
        return new CrmOwner(PREFIX_SERVICE + CrmValidationHelper.required(clientId, "clientId"),
                CrmSubjectType.SERVICE);
    }

    /**
     * 解析已持久化的 owner 字符串。
     *
     * @param owner owner 字符串（iam:/aksk: 前缀）
     * @return 归属主体
     * @throws CrmException 前缀非法时抛出校验错误
     */
    public static CrmOwner of(String owner) {
        CrmValidationHelper.required(owner, "owner");
        if (owner.startsWith(PREFIX_HUMAN)) {
            return new CrmOwner(owner, CrmSubjectType.HUMAN);
        }
        if (owner.startsWith(PREFIX_SERVICE)) {
            return new CrmOwner(owner, CrmSubjectType.SERVICE);
        }
        throw new CrmException(CrmErrorCode.INVALID_FORMAT,
                String.format("owner 前缀非法，仅支持 %s 与 %s", PREFIX_HUMAN, PREFIX_SERVICE));
    }

    /**
     * 是否人员主体。
     *
     * @return 人员主体返回 true
     */
    public boolean isHuman() {
        return subjectType == CrmSubjectType.HUMAN;
    }

    /**
     * 是否服务凭证主体。
     *
     * @return 服务凭证主体返回 true
     */
    public boolean isService() {
        return subjectType == CrmSubjectType.SERVICE;
    }
}
