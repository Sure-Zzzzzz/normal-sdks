package io.github.surezzzzzz.sdk.feedback.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交人联系提示值对象（SPI {@code SubmitterProfileProvider} 的返回）。
 *
 * <p>非直观约束：email 仅作提交表单预填默认值（可改可清），提交值=用户确认值，
 * 「自由输入不关联账号」语义不受影响。</p>
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionContactHint {

    /**
     * 预填邮箱（可空：无资料或获取失败=空输入框）
     */
    private String email;

    /**
     * 预填显示名（可空；供提交人快照补全参考，不作鉴权依据）
     */
    private String displayName;
}
