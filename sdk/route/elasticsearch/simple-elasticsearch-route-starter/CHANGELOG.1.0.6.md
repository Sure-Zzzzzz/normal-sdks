# [1.0.6]

## 重大重构

### 包结构优化

* 重构包结构，提升代码组织清晰度：
    - `extractor/` - 索引名称提取器
    - `model/` - 数据模型（ClusterInfo, ServerVersion）
    - `proxy/` - 代理相关类
    - `resolver/` - 路由解析器（RouteResolver, RoutePatternMatcher）
    - `support/` - 工具类（SpELResolver）
* 从 `registry/` 包移出 `ClusterInfo` 和 `ServerVersion` 到 `model/` 包
* 从 `support/` 包移出 `RouteResolver`、`RoutePatternMatcher`、`RouteTemplateProxy` 到各自专属包

### 动态加载机制

* **IndexNameExtractor 支持动态加载**
    - 所有提取器通过 Spring 自动扫描和注入
    - 使用 `@SimpleElasticsearchRouteComponent` + `@Order` 注解控制加载和优先级
    - 支持用户自定义提取器，无需修改框架代码
    - 启动时自动显示已加载的提取器列表

### 内置提取器

* `IndexCoordinatesExtractor` - Order(1) - 从 IndexCoordinates 参数提取索引名
* `EntityObjectExtractor` - Order(2) - 从实体对象 @Document 注解提取索引名
* `ClassTypeExtractor` - Order(3) - 从 Class 类型参数提取索引名
* `IndexQueryExtractor` Order (4) - 从 `IndexQuery` 参数提取手动指定的索引名

## 扩展性提升

### 自定义提取器示例

```java

@SimpleElasticsearchRouteComponent
@Order(10)  // 数字越小优先级越高
public class MyCustomExtractor implements IndexNameExtractor {
    @Override
    public String extract(Method method, Object[] args) {
        // 自定义提取逻辑
    }

    @Override
    public boolean supports(Object arg) {
        // 判断是否支持该参数类型
    }
}
```

## 影响范围

* **破坏性变更**: 部分类的包路径发生变化，如果外部直接引用这些类需要更新 import
* **兼容性**: 对于仅使用配置的用户，无影响
