# CHANGELOG - simple-aksk-resource-server-starter 2.0.1

> 发布日期：2026-07-01  
> 版本类型：Patch Release

## 变更概述

支持 `server.servlet.context-path` 场景下的 Spring Security matcher 路径归一化。

在 2.0.0 中，如果业务配置：

```yaml
server:
  servlet:
    context-path: /api

io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                security:
                  protected-paths:
                    - /api/**
```

而 Controller 使用应用内路径：

```java
@GetMapping("/user/current")
```

外部访问 `/api/user/current` 时，Spring Security matcher 实际看到的是应用内路径 `/user/current`，导致 2.0.0 默认 `/api/**` 无法命中。

2.0.1 默认启用 context-path-aware 归一化，会将带 `server.servlet.context-path` 前缀的安全路径转换为 matcher 实际使用的应用内路径：

```text
/api/**        -> /**
/api/public/** -> /public/**
```

## 新增配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `security.context-path-aware` | `true` | 是否启用 context-path-aware 路径归一化 |

如需保留 2.0.0 的路径匹配语义，可关闭：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                security:
                  context-path-aware: false
```

## 变更详情

1. 新增 `SecurityPathHelper`，负责：
   - 规范化 `server.servlet.context-path`
   - 清洗 `protected-paths` / `permit-all-paths`
   - 剥离 context-path 前缀
   - 去重并保留原始顺序
2. `ResourceServerSecurityConfiguration` 在构建 `SecurityFilterChain` 前归一化 protected / permit 路径。
3. `permit-all-paths` 和 `protected-paths` 使用相同归一化规则，确保白名单仍优先。
4. 对归一化后的 `/**` universal matcher 做折叠注册，避免 Spring Security matcher 顺序非法。
5. 当 `permit-all-paths` 归一化后包含 `/**` 且 `protected-paths` 非空时 fail fast，避免白名单覆盖保护路径。
6. 当 security path 配置中包含 query string（`?`）时 fail fast，避免误以为支持按 query 条件保护接口。

## 兼容性说明

- 不改变 token introspect 行为。
- 不改变安全上下文注入逻辑。
- 不改变权限注解逻辑。
- 不引入新依赖。
- 不引入 Spring Boot 3.x / `jakarta.*` API。
- `simple-aksk-resource-core` 仍为 `2.0.0`，本次不升级。

## 使用提醒

- `server.servlet.context-path` 和 `spring.mvc.servlet.path` 不是同一层路径。2.0.1 只剥离 `server.servlet.context-path`，不剥离 `spring.mvc.servlet.path`。
- SDK 不引入 Actuator 依赖，`/actuator/health` 只作为普通路径处理。
- 不要通过 `permit-all-paths: /api/**` 解决 CORS 预检问题；该配置在 `server.servlet.context-path=/api` 下会归一化为 `/**` 并触发 fail fast。
- 2.0.1 不改变业务自定义 `SecurityFilterChain` 场景的接管/退让语义。

## 测试说明

已覆盖：

- `SecurityPathHelperTest`
- `ContextPathProtectedOnlyIntegrationTest`
- `ContextPathPermitAllIntegrationTest`
- `ContextPathAwareDisabledIntegrationTest`
- `NoContextPathCompatibilityIntegrationTest`
- `ContextPathInvalidConfigurationTest`
- `UniversalMatcherRegistrationIntegrationTest`

验证命令：

```bash
cd "d:\code\github\Sure-Zzzzzz\normal-sdks" && "D:\code\gradle-8.5\bin\gradle.bat" :sdk:auth:aksk:resource:simple-aksk-resource-server-starter:test -Dorg.gradle.java.home="C:\Program Files\Zulu\zulu-11" -Dfile.encoding=UTF-8
```

测试结果：`BUILD SUCCESSFUL`。

## 贡献者

- surezzzzzz
