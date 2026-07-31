# log-truncate-starter 1.1.0

- **类型**：行为修正与兼容性维护
- **兼容基线**：Spring Boot `2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5`、`2.7.9`

## 变更

- `max-total-bytes` 改为最终日志字符串的严格 UTF-8 字节上限。
- `max-field-chars` 改为 JSON 文本字段的严格 Unicode code point 上限。
- 截断标记计入上限；`{dropped}` 位数变化、中文、emoji 与 UTF-16 代理对均不会造成越界或半个字符。
- 阈值无法容纳完整标记时，返回不越界的安全原始前缀，不输出半段标记。
- 保持 `LogTruncateComponent` 标记扫描、`logTruncator` Bean 名和 SDK 私有 `ObjectMapper` 边界。
- 移除核心测试的环境变量跳过，补齐严格阈值、深度裁剪、异常降级、属性绑定和标记扫描覆盖。

## 兼容性与升级

Maven 坐标、`LogTruncator` API、配置前缀、六项配置键、默认值、自动配置入口和 `logTruncator` Bean 名均保持不变。

超限输入的输出可能比 1.0.0 更短：1.0.0 会在达到阈值后追加截断标记，最终结果可能超过配置值；1.1.0 保证最终结果不超过阈值。需要保留更多原始内容或完整标记时，请调大对应阈值。

## 验证

四档 Spring Boot 均执行完整模块测试，每档 16 个用例通过，无跳过、无失败。