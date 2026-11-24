# 1.1.1

## 发布信息

- 类型：Bugfix
- 基线版本：1.1.0

## 变更

- IAM Profile Feign 拦截器显式实现 `Ordered`：默认 AKP 拦截器顺序最高，命名 Profile 拦截器顺序最低。
- IAM 子上下文使用同名 Bean 遮蔽底座默认拦截器，避免默认与 Profile 拦截器同时执行导致排序不稳定。
- Profile 认证头固定先移除旧 `Authorization` 再写入目标 Profile Token。

## 兼容性

- `1.1.0` 配置命名 Profile 后仍偶发 401 的宿主应升级本版本。
- 配置键、契约方法、Bean 名和错误口径不变。
