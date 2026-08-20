# [1.0.1] - 2026-08-20

## 类型

Feature

## 依赖升级

无。

## 变更内容

- 新增 `io.github.surezzzzzz.sdk.http.xff.capture.order`，用于配置 XFF Capture Filter 的注册顺序。
- 未配置 `order` 时继续使用 `Ordered.HIGHEST_PRECEDENCE + 10`，与 1.0.0 保持一致。
- 不修改 XFF 采集规则、URL pattern、REQUEST dispatcher、Core 事件契约或自动配置启停语义。

## 新增测试

- `SimpleXffCaptureAutoConfigurationTest` 覆盖未配置时的默认顺序，以及配置绑定后 `FilterRegistrationBean` 使用指定顺序。
- 保留真实 HTTP 200、400、404 采集测试，验证顺序配置未改变 Filter 的请求覆盖范围和采集语义。
- 在 Spring Boot `2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5`、`2.7.9` 完整测试通过。

## 向后兼容性

完全向后兼容。现有 1.0.0 配置无需改动；未设置 `capture.order` 的应用保持原 Filter 顺序。

## 升级指南

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-starter:1.0.1'
}
```

只有需要协调 XFF Filter 与应用已有 Filter 顺序时，才设置 `capture.order`。调用方应根据自身容器和安全链的实际顺序确定该整数值。
