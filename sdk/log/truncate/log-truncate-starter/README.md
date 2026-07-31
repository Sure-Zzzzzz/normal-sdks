# log-truncate-starter

对超长日志、异常堆栈和 Java 对象进行安全截断的 Spring Boot Starter，避免日志输出或网络传输被异常大字段放大。

## 版本选择

| 版本 | 适用场景 |
|---|---|
| `1.1.0` | 当前推荐版本。最终输出严格遵守字段字符与总字节上限。 |
| `1.0.0` | 历史版本。超限后追加截断标记可能使最终输出超过配置上限。 |

## 接入

```groovy
dependencies {
    implementation 'io.github.surezzzzzz:sdk-log-truncate-starter:1.1.0'
}
```

本模块使用业务项目已有的 Jackson 版本；若项目未提供 Jackson，请自行引入兼容版本。

## 使用

引入依赖后自动注册名为 `logTruncator` 的 `LogTruncator` Bean：

```java
@Autowired
@Qualifier("logTruncator")
private LogTruncator logTruncator;

String shortLog = logTruncator.truncate(anyHugeObject);
String shortRawLog = logTruncator.truncateRaw(rawLog);
```

- `truncate(Object)`：字符串直接截断；异常输出堆栈；其他对象转为 JSON 后裁剪深度和文本字段。
- `truncateRaw(String)`：只按总字节数截断原始字符串。
- `truncate(null)` 返回字符串 `"null"`；`truncateRaw(null)` 返回 `null`。
- SDK 维护独立 `ObjectMapper`，不会注入、替换或修改业务的 Spring `ObjectMapper`。

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        log:
          truncate:
            max-total-bytes: 8192
            max-field-chars: 1024
            max-depth: 8
            ellipsis: "..."
            truncated-note-template: " [truncated {dropped}]"
            depth-exceeded-placeholder: "__depth_exceeded__"
```

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `max-total-bytes` | `8192` | 最终日志字符串最大 UTF-8 字节数。 |
| `max-field-chars` | `1024` | JSON 中每个文本字段的最终最大 Unicode code point 数。 |
| `max-depth` | `8` | 对象和数组展开的最大深度，达到深度后使用占位符。 |
| `ellipsis` | `...` | 截断标记前缀。 |
| `truncated-note-template` | ` [truncated {dropped}]` | 截断提示模板，`{dropped}` 会替换为实际省略数量。 |
| `depth-exceeded-placeholder` | `__depth_exceeded__` | 超过最大深度时的文本占位符。 |

`max-field-chars` 按 Unicode code point 计算，`max-total-bytes` 按 UTF-8 字节计算；中文、emoji 和代理对都不会被截断到半个字符。

## 兼容性

| Spring Boot | 验证状态 |
|---|---|
| `2.2.13.RELEASE` | 已验证 |
| `2.3.12.RELEASE` | 已验证 |
| `2.4.5` | 已验证 |
| `2.7.9` | 已验证 |

JDK 8 源码兼容；四档 Spring Boot 均执行完整模块测试。

## 1.1.0 升级说明

1.1.0 不修改 Maven 坐标、`LogTruncator` API、配置前缀、配置键、默认值或默认 Bean 名。

对于超限输入，`max-total-bytes` 和 `max-field-chars` 从 1.0.0 的“原始内容保留阈值”收口为**最终输出严格上限**。截断标记也计入上限；当阈值小到无法容纳完整标记时，结果优先保证不越界，可能不显示完整截断标记。若需要保留更多原始内容或完整标记，请调大相应阈值。
