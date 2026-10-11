package io.github.surezzzzzz.sdk.auth.iam.server.service.user;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部身份 JIT 开号的用户名冲突处置策略（1.3.7，判定与执行分离）。
 *
 * <p>两策略封闭：AUTO_SUFFIX（默认，GitHub/GitLab 同款）与 REJECT（1.3.6 现状行为
 * 收紧保留）。配置值非法在启动期 fail-fast（{@link #of}）。续号语义：起算点=现存
 * 最大数字后缀+1（非"从 1 找空位"——免线性扫描且中间空洞不复用）；尾部号删除后
 * max 回退的复用边角见 DESIGN.1.3.7 R1，接受并记录。大小写变体共享续号空间
 * （判定走 DB collation，MySQL ci 下 Zhangsan 与 zhangsan 互撞）。</p>
 *
 * @author surezzzzzz
 */
public enum JitUsernameConflictStrategy {

    /**
     * 自动后缀（默认）：建议名撞已有 username 时按"建议名-{最大号+1}"续（连字符数字，GitHub 同款）。
     */
    AUTO_SUFFIX {
        @Override
        public String resolve(String suggestion, Predicate<String> occupied,
                              List<String> existingPrefixMatches) {
            if (!occupied.test(suggestion)) {
                return suggestion;
            }
            long max = 0;
            for (String existing : existingPrefixMatches) {
                Matcher matcher = NUMERIC_SUFFIX.matcher(existing);
                if (matcher.matches()) {
                    max = Math.max(max, Long.parseLong(matcher.group(1)));
                }
            }
            String candidate = suggestion + SUFFIX_SEPARATOR + (max + 1);
            if (occupied.test(candidate)) {
                // 二次确认失败=并发插入窗口（前缀集合已涵盖全部数字后缀，max+1 必然空闲）
                // 概率极低且用户重登即恢复（下次 max 已含新行）；终态与拒绝一致，走管理员兜底
                throw conflictExhausted(suggestion);
            }
            return candidate;
        }
    },

    /**
     * 拒绝（收紧项）：撞已有 username 走既有管理员预绑定语义（与 1.3.6 行为零变化）。
     */
    REJECT {
        @Override
        public String resolve(String suggestion, Predicate<String> occupied,
                              List<String> existingPrefixMatches) {
            if (!occupied.test(suggestion)) {
                return suggestion;
            }
            throw new SimpleIamServerException(ErrorCode.EXTERNAL_IDENTITY_NOT_BOUND,
                    ServerErrorMessage.EXTERNAL_IDENTITY_NOT_BOUND);
        }
    };

    /**
     * 后缀分隔形态：连字符数字（GitHub 同款，如 zhangsan-1）
     */
    static final String SUFFIX_SEPARATOR = "-";

    /**
     * 数字后缀解析：整串=前缀+分隔符+纯数字结尾（非数字后缀如 "-abc"/"-2b" 不参与 max 计算）
     */
    static final Pattern NUMERIC_SUFFIX = Pattern.compile(".*-(\\d+)");

    /**
     * 解析配置值为策略；非法值启动期 fail-fast（null/空白视为默认 auto-suffix）。
     *
     * @param configured 配置值
     * @return 策略实例
     */
    public static JitUsernameConflictStrategy of(String configured) {
        String trimmed = configured == null ? "" : configured.trim();
        if (trimmed.isEmpty()
                || SimpleIamServerConstant.USERNAME_CONFLICT_STRATEGY_AUTO_SUFFIX.equals(trimmed)) {
            return AUTO_SUFFIX;
        }
        if (SimpleIamServerConstant.USERNAME_CONFLICT_STRATEGY_REJECT.equals(trimmed)) {
            return REJECT;
        }
        throw new SimpleIamServerException(
                io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.CONFIG_VALIDATION_FAILED,
                String.format(ServerErrorMessage.USERNAME_CONFLICT_STRATEGY_INVALID, configured));
    }

    static SimpleIamServerException conflictExhausted(String suggestion) {
        return new SimpleIamServerException(ErrorCode.EXTERNAL_IDENTITY_NOT_BOUND,
                ServerErrorMessage.EXTERNAL_IDENTITY_NOT_BOUND);
    }

    /**
     * 处置建议名：不撞即原样，撞则按策略续号或拒绝。
     *
     * @param suggestion            外部身份建议的用户名（已 trim 非空）
     * @param occupied              精确占用判定（走 DB collation，大小写语义与唯一索引一致）
     * @param existingPrefixMatches "建议名-" 前缀下的现存串全量（供 max 解析；AUTO_SUFFIX 必传）
     * @return 最终落库用户名
     */
    public abstract String resolve(String suggestion, Predicate<String> occupied,
                                   List<String> existingPrefixMatches);
}
