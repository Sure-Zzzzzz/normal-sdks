package io.github.surezzzzzz.sdk.naturallanguage.parser.keyword;

/**
 * 关键字贡献者接口
 * 实现此接口并注册为 Spring Bean，即可在启动时扩展关键字
 * 多个 Contributor 按 @Order 或 Ordered 接口的顺序执行，数字越小越先执行
 *
 * @author surezzzzzz
 */
public interface KeywordContributor {

    /**
     * 向注册表贡献关键字
     *
     * @param registry 关键字注册表
     */
    void contribute(KeywordRegistry registry);
}
