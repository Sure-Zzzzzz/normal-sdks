package io.github.surezzzzzz.sdk.feedback.server.adaptor;

import io.github.surezzzzzz.sdk.feedback.server.adaptor.annotation.SimpleFeedbackIamAdaptorComponent;
import io.github.surezzzzzz.sdk.feedback.server.model.SubmissionContactHint;
import io.github.surezzzzzz.sdk.feedback.server.spi.SubmitterProfileProvider;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import lombok.extern.slf4j.Slf4j;

/**
 * IAM 联系提示 SPI 实现（聚形态）。
 *
 * <p>非直观约束：IamUser 响应不含 subjectId（id 为内部数字标识）——
 * 配对键=调用入参侧绑定（请求是谁就归谁），不依赖响应回传。</p>
 *
 * <p>容错语义：IAM 客户端 Bean 缺失（传输件未引）或出站失败均按降级处理
 * （返回 null+WARN，不拒提交）——便利性失败不扩大为可用性故障。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackIamAdaptorComponent
public class IamSubmitterProfileProvider implements SubmitterProfileProvider {

    private final IamUserClient iamUserClient;

    public IamSubmitterProfileProvider(IamUserClient iamUserClient) {
        this.iamUserClient = iamUserClient;
    }

    /**
     * 查询提交人联系提示（邮箱作预填默认值，用户可改可清——「自由输入不关联账号」语义不变）。
     *
     * @param subjectId 提交人 subjectId（裸值，入参侧绑定）
     * @return 联系提示；客户端缺失/出站失败/资料无邮箱返回 null（空输入框）
     */
    @Override
    public SubmissionContactHint contactHint(String subjectId) {
        long startNanos = System.nanoTime();
        try {
            IamUser user = iamUserClient.getUser(subjectId);
            log.debug("hint 邮箱取数完成 耗时ms={} 邮箱有无={}", (System.nanoTime() - startNanos) / 1_000_000L,
                    user != null && user.getEmail() != null);
            if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
                return null;
            }
            return SubmissionContactHint.builder()
                    .email(user.getEmail())
                    .displayName(user.getDisplayName())
                    .build();
        } catch (RuntimeException exception) {
            log.warn("hint 邮箱取数出站失败，该项降级（不拒提交）结果=IAM客户端异常 耗时ms={}",
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return null;
        }
    }
}
