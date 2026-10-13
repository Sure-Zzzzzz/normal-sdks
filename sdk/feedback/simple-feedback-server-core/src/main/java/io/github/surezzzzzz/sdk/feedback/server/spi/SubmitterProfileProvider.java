package io.github.surezzzzzz.sdk.feedback.server.spi;

import io.github.surezzzzzz.sdk.feedback.server.model.SubmissionContactHint;

/**
 * 提交人联系提示 SPI（出站取数点二，DESIGN D12/修正⑩）。
 *
 * <p>聚散契约：降级实现恒返回 null（散形态）；adaptor 聚形态实现经
 * {@code IamUserClient.getUser(subjectId)} 取邮箱（仓内现成接口，响应不含 subjectId——
 * 配对键=调用入参侧绑定，DESIGN 修正㉑）；出站失败按降级处理（返回 null 并记 WARN）。</p>
 *
 * @author surezzzzzz
 */
public interface SubmitterProfileProvider {

    /**
     * 查询提交人联系提示（邮箱预填默认值）。
     *
     * @param subjectId 提交人 subjectId（裸值，不带 iam: 前缀）
     * @return 联系提示；null=该取数点不支持（降级为空输入框）
     */
    SubmissionContactHint contactHint(String subjectId);
}
