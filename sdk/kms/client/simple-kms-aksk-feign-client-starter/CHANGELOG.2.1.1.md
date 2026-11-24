# 2.1.1

## 发布信息

- 类型：Bugfix
- 基线版本：2.1.0

## 变更

- Feign Profile 拦截器显式实现 `Ordered`：默认 AKP 拦截器顺序最高，named Profile 拦截器顺序最低。
- Feign 子上下文使用同名 Bean 遮蔽父上下文默认拦截器，避免默认与 Profile 拦截器同时执行。
- Authorization 固定先移除旧值再写入当前选择 AKP 的 Token。

## 兼容性

- 配置键、契约方法、Bean 行为和错误口径不变。
- 使用 named Profile 且曾偶发 401 的宿主应升级本版本。