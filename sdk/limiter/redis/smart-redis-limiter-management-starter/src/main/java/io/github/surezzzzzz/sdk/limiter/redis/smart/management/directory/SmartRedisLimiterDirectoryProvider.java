package io.github.surezzzzzz.sdk.limiter.redis.smart.management.directory;

import java.util.List;

/**
 * v2 类型化目录提供方 SPI
 *
 * <p>目录是 Management 的授权数据源：服务协议模式（LEGACY_V1 / TYPED_V2）、
 * 资源与维度声明、命名空间与自定义类型，以及可选的对象目录（客户、人员等
 * 稳定对象的 ID 与名称）。宿主以部署配置或自有实现提供；Management 不做
 * 自动注册、心跳或反查内部接口，对象名称仅作展示。
 *
 * <p>实现必须线程安全；返回列表可为空。所有编码区分大小写。
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterDirectoryProvider {

    /**
     * 列出全部服务声明
     */
    List<SmartRedisLimiterServiceDeclaration> listServices();

    /**
     * 查找服务声明；不存在返回 null
     */
    SmartRedisLimiterServiceDeclaration findService(String serviceCode);

    /**
     * 按维度（CUSTOM 维度另带 customType）检索对象目录，id 与名称均可命中关键词；
     * limit 为返回上限（调用方传入，实现必须遵守），无匹配返回空列表。
     */
    List<SmartRedisLimiterDirectoryObject> listObjects(String serviceCode,
                                                       String dimension,
                                                       String customType,
                                                       String keyword,
                                                       int limit);
}
