# [1.0.4]

## 🐛 Bug 修复

### Spring Boot 版本兼容性修复

**问题描述**：
在 Spring Boot 2.4.x 版本下，由于 CGLIB 代理无法访问 `AbstractElasticsearchTemplate` 的 protected 成员，导致 `RouteTemplateProxy` 创建失败，应用启动报错：
```
org.springframework.cglib.core.CodeGenerationException: java.lang.IllegalAccessException
-->class io.github.surezzzzzz.sdk.elasticsearch.route.support.RouteTemplateProxy
cannot access a member of class org.springframework.data.elasticsearch.core.AbstractElasticsearchTemplate
with modifiers "protected"
```

**解决方案**：

在 search-starter 的 `SimpleElasticsearchSearchAutoConfiguration` 中优先创建 `ElasticsearchRestTemplate` bean，避免触发 route-starter 的代理创建逻辑。

**智能降级策略**：
1. **尝试创建 RouteTemplateProxy**：优先支持多数据源路由
2. **CGLIB 失败时智能判断**：
   - **单数据源**：自动降级到简单 ElasticsearchRestTemplate（路由无意义，功能正常）
   - **多数据源**：立即抛出异常并提供解决方案，避免静默失败导致路由失效

**影响范围**：
- ✅ **Spring Boot 2.7.x+**：RouteTemplateProxy 创建成功，支持多数据源路由
- ✅ **Spring Boot 2.4.x 单数据源**：自动降级，功能正常
- ⚠️ **Spring Boot 2.4.x 多数据源**：启动失败，提示升级 Spring Boot 版本

## 🔧 技术细节

### 配置加载顺序调整

- 将 `@AutoConfigureAfter` 改为 `@AutoConfigureBefore(SimpleElasticsearchRouteConfiguration.class)`
- search-starter 先于 route-starter 创建 template bean
- route-starter 的 `@ConditionalOnMissingBean` 检测到已存在 bean，不再创建代理

### Bean 创建逻辑

```java
@Bean
@ConditionalOnMissingBean
public ElasticsearchOperations elasticsearchRestTemplate(...) {
    try {
        // 尝试创建 RouteTemplateProxy（多数据源路由）
        return new RouteTemplateProxy(registry, routeResolver, routeProperties);
    } catch (Exception e) {
        int datasourceCount = routeProperties.getSources().size();
        if (datasourceCount > 1) {
            // 多数据源：必须报错
            throw new BeanCreationException(...);
        } else {
            // 单数据源：降级到简单 template
            return new ElasticsearchRestTemplate(client);
        }
    }
}
```

### 错误提示信息

多数据源场景下启动失败时，提供明确的解决方案：
1. 升级到 Spring Boot 2.7.x+（已验证兼容）
2. 升级到 Spring Boot 3.x
3. 联系技术支持获取替代方案

## 📝 向后兼容

- ✅ **API 接口**：无变更
- ✅ **配置格式**：无变更
- ✅ **功能行为**：Spring Boot 2.7.x+ 用户无感知
- ⚠️ **潜在影响**：Spring Boot 2.4.x 多数据源用户需升级版本

## 🎯 升级指南

### 从 1.0.3 升级到 1.0.4

1. **更新依赖**：
   ```gradle
   implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.0.4'
   ```

2. **检查 Spring Boot 版本**：
   - **单数据源**：任何版本均可（2.4.x 会自动降级）
   - **多数据源**：推荐 Spring Boot 2.7.x+ 或 3.x

3. **配置文件**：
   - 无需修改，完全向后兼容

4. **代码变更**：
   - 业务代码无需修改

### 多数据源用户升级建议

如果使用 Spring Boot 2.4.x 且配置了多数据源，升级后可能遇到启动失败。解决方案：

**方案 1**：升级 Spring Boot（推荐）
```gradle
// 升级到 2.7.x
ext {
    springBootVersion = '2.7.18'
}
```

**方案 2**：升级到 Spring Boot 3.x
```gradle
ext {
    springBootVersion = '3.2.0'
}
```

## 💡 使用建议

1. **推荐 Spring Boot 版本**：
   - 新项目：Spring Boot 2.7.x 或 3.x
   - 现有项目（单数据源）：保持现有版本即可
   - 现有项目（多数据源）：升级到 2.7.x+

2. **测试验证**：
   - 升级后启动应用，检查日志是否有降级提示
   - 多数据源场景下验证路由功能是否正常

3. **问题反馈**：
   - 如遇到兼容性问题，请在 GitHub 提交 issue
   - 提供 Spring Boot 版本、数据源配置、完整错误堆栈

## 🔗 相关链接

- **问题报告**：用户反馈 Spring Boot 2.4.5 启动失败
- **根本原因**：Java 模块系统 + CGLIB 对 protected 成员的访问限制
- **验证版本**：Spring Boot 2.7.9 测试通过
