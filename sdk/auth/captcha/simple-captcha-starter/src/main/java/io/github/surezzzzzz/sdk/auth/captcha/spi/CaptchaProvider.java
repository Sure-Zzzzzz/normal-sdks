package io.github.surezzzzzz.sdk.auth.captcha.spi;

import io.github.surezzzzzz.sdk.auth.captcha.model.CaptchaChallenge;

/**
 * Captcha Provider SPI
 *
 * <p>人机验证出题 / 验题扩展点。默认实现为图片验证码（Java2D 自绘），
 * 是本模块唯一内置实现（组件装配）；业务方接入滑块、行为码、云验证码等
 * 厂商方案时实现本接口，且不引本模块运行时（compileOnly 取本接口即可），
 * 不同实现不同引用，无让位场景。
 *
 * <p>存储职责归实现：图片码用 Redis；远程校验型（滑块等）用厂商 token，无需本地存储。
 *
 * @author surezzzzzz
 */
public interface CaptchaProvider {

    /**
     * 生成挑战（captchaId + 展示载体），存储由实现自管
     *
     * @return 验证码挑战（captchaId / type / content）
     */
    CaptchaChallenge generate();

    /**
     * 校验答案；一次性消费（校验后挑战即失效），过期或不存在返回 false
     *
     * @param captchaId 挑战 id（generate 返回）
     * @param answer    用户提交的答案
     * @return true 校验通过，false 校验失败（错误 / 过期 / 已消费）
     */
    boolean verify(String captchaId, String answer);
}
