# CHANGELOG - simple-iam-server-core 1.3.4

## 发布信息

- 版本：`1.3.4`
- 类型：Patch / 错误消息常量补充（随 starter 1.3.6 口令生存期特性）
- 基线版本：`1.3.3`

## 版本定位

为 starter 1.3.6 的两条用户可见错误消息补常量（消息无硬编码规范）：`PASSWORD_SAME_AS_CURRENT`
（三处改密/重置点拒绝新密码=当前密码）与 `PASSWORD_WARN_WINDOW_EXCEEDS_MAX_AGE`（启动校验：
提醒窗口大于生存期）。仅 `ServerErrorMessage` 增常量，其余零变化。
