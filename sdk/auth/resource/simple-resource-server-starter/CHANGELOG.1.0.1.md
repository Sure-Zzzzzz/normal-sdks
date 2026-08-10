# simple-resource-server-starter 1.0.1 变更记录

## 兼容性修复

- 修复 Spring Boot `2.2.13.RELEASE`、`2.3.12.RELEASE` 中资源安全链无法按 Spring Security 生命周期初始化的问题。
- 在 Spring Boot `2.2.x`、`2.3.x` 使用 `WebSecurityConfigurerAdapter` 装配资源窄安全链；在 `2.4.5`、`2.7.9` 保持 `SecurityFilterChain` 装配方式。
- 两种安全配置按 Spring Boot 版本严格互斥，未声明支持的版本不会装配资源安全链。

## 兼容承诺

从 `1.0.0` 升级至 `1.0.1` 不需要修改业务代码、配置项、Provider 适配器或认证授权契约。资源路径边界、CSRF 范围、认证 Filter 顺序、401/403 与 API 权限语义保持不变。

Boot `2.2.x`、`2.3.x` 与 Boot `2.4.x`、`2.7.x` 分别使用对应的 Spring Security 配置生命周期，但对业务方保持同一套资源路径、Bearer 认证、Provider 路由和 API 权限契约。

## 已验证矩阵

- Spring Boot `2.2.13.RELEASE`
- Spring Boot `2.3.12.RELEASE`
- Spring Boot `2.4.5`
- Spring Boot `2.7.9`

每组均实际执行 28 项测试，`skipped=0`、`failures=0`、`errors=0`。测试覆盖公共/受保护路径、Bearer 隔离、401/403、API 权限规则、资源窄链与宿主宽链边界、CSRF 以及旧版/现代安全配置互斥。
