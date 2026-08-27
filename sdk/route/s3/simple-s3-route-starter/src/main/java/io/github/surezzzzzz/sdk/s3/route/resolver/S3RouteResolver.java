package io.github.surezzzzzz.sdk.s3.route.resolver;

/**
 * S3 target 精确解析 SPI。
 *
 * @author surezzzzzz
 */
public interface S3RouteResolver {

    /**
     * 解析并校验调用方指定的 target key。
     *
     * @param targetKey 调用方指定的 target key
     * @return 已登记的精确 target key
     */
    String resolveTargetKey(String targetKey);
}
