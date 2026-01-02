# Changelog

## [1.0.1] - 2026-01-02

### 🐛 Bug修复

#### 日志等级优化

修复了 Token 化结果日志等级不当的问题，将调试信息从 `INFO` 级别改为 `DEBUG` 级别。

**变更详情：**

- `NLParser.logTokenizationResult()` 方法中的所有日志调用从 `log.info()` 改为 `log.debug()`
- 日志等级检查从 `log.isInfoEnabled()` 改为 `log.isDebugEnabled()`

**影响范围：**

在生产环境中，默认日志级别为 INFO 时，不再打印 Token 化的详细调试信息，减少日志输出量。

**如何启用调试日志（如需调试）：**

```yaml
# application.yml
logging:
  level:
    io.github.surezzzzzz.sdk.naturallanguage.parser: DEBUG
```

**升级说明：**

- ✅ 完全向后兼容，无需修改代码
- ✅ 无破坏性变更
- ✅ 推荐升级以优化生产环境日志输出
