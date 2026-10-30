package io.github.surezzzzzz.sdk.license.core.spi;

import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import java.util.Optional;

/**
 * 可信当前使用者提供端口：核验离线环境下真实人员/服务的本地身份。
 *
 * <p>提供方返回 sourceId/profileVersion/证明有效截止及支持能力；调用方任意 subjectId、表单、
 * 用户名、待验文件内容不是可信身份。普通在线令牌到期后不能仅凭其中 subject 继续离线授权。</p>
 *
 * @author surezzzzzz
 */
public interface TrustedSubjectProvider {

    /** 当前可信人员/服务身份（离线/混合本地验证用）。 */
    Optional<SubjectRef> currentTrustedSubject(String offlineProofProfile);

    /** 该 profile 的能力版本编号。 */
    String profileVersion();

    /** 证明有效截止（UTC 秒）。 */
    long proofValidUntil();
}
