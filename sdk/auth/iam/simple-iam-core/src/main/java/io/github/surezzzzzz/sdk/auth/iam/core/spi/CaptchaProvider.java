package io.github.surezzzzzz.sdk.auth.iam.core.spi;

/**
 * 登录链路人机验证提供方。
 *
 * <p>IAM 登录策略在失败达到阈值后要求请求方完成人机验证（验证码），
 * 出题与验题经本 SPI 解耦：默认图片码由 captcha 适配器桥接通用
 * captcha 模块提供；业务方需要滑块 / 行为码 / 云验证码时直接实现本接口，
 * 并移除 captcha adapter 依赖（不同实现不同引用，无让位场景）。</p>
 *
 * <p>实现契约：</p>
 * <ul>
 *   <li>何时要求验证码（失败计数、阈值判定）归 IAM 登录策略，实现不感知；</li>
 *   <li>{@link #verify} 必须一次性消费：同一挑战只允许校验一次，
 *       校验后无论成败挑战即失效（防试错爆破）；</li>
 *   <li>挑战有效期（TTL）归实现/提供方配置。</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public interface CaptchaProvider {

    /**
     * 生成一次验证挑战。
     *
     * @return 验证挑战（captchaId + 展示内容）
     */
    CaptchaChallenge generate();

    /**
     * 校验挑战答案（一次性消费）。
     *
     * @param captchaId 挑战 id
     * @param answer    请求方提交的答案
     * @return 校验通过返回 true；挑战不存在 / 已过期 / 已消费 / 答案错误返回 false
     */
    boolean verify(String captchaId, String answer);
}
