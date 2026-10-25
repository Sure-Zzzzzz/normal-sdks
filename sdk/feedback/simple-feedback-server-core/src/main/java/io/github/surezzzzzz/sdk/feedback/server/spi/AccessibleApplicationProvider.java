package io.github.surezzzzzz.sdk.feedback.server.spi;

import io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication;

import java.util.List;

/**
 * 提交人可访问应用集 SPI（出站取数点一，提供页面准入详情与全局目录）。
 *
 * <p>聚散契约：</p>
 * <ul>
 *   <li>server-starter 内置降级实现：恒返回 {@code null}（散形态，零远程面）；</li>
 *   <li>adaptor 聚形态实现覆盖降级实现（@ConditionalOnMissingBean），出站 IAM 取数
 *       （页面准入详情口径：编码+展示名+描述，展示名与门户侧边栏同源），出站失败按
 *       降级处理（返回 {@code null} 并记 WARN，不拒提交）；</li>
 *   <li>返回值语义：{@code null}=该取数点不支持（降级为注册表启用项全量）；
 *       非 null（含空集）=有效答案——空集由调用方按「交集空回退全量」处理，
 *       便利性失败不扩大为可用性故障。</li>
 * </ul>
 *
 * <p>纯消费方纪律：实现方出站持 AKP，只授取数所需最小 API 码，不给 PAGE。</p>
 *
 * @author surezzzzzz
 */
public interface AccessibleApplicationProvider {

    /**
     * 查询提交人可访问（有页面准入投影）的可信应用详情集。
     *
     * @param subjectId 提交人 subjectId（裸值，不带 iam: 前缀）
     * @return 可访问应用详情集；null=该取数点不支持（降级），空集=有效答案（交集空回退由调用方处理）
     */
    List<AccessibleApplication> accessibleApplications(String subjectId);

    /**
     * 查询 IAM 可信应用全局目录（用于注册表数据来源核对）。
     *
     * <p>与 {@link #accessibleApplications(String)} 的口径差异：本方法无用户维度，
     * 用于注册表标注「IAM 同源」行并锁定展示名/说明的编辑权——同源行的名称与说明由
     * IAM 单一事实源承担（登记时自动抄录、编辑时拒绝改写、可显式重同步），注册表只
     * 保留受理门（启用/停用）与处理组。默认实现返回 {@code null}（散形态不支持），
     * 出站失败同样按降级返回 {@code null}：来源未知时注册表保持散形态自主面
     * （全字段可编辑），不因核对不可用而锁死。</p>
     *
     * @return 全局目录（编码+展示名+描述）；null=该取数点不支持（来源未知，注册表全可编辑）
     */
    default List<AccessibleApplication> catalogApplications() {
        return null;
    }
}
