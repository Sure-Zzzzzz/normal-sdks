# Normal SDKs

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.4%20%7C%202.7-brightgreen.svg)](https://spring.io/projects/spring-boot)

> 企业级通用 Spring Boot Starter 集合，提供开箱即用的基础设施组件，助力快速开发。

## 📦 SDK 目录

### 🔍 Elasticsearch

| SDK | 版本    | 说明 | 文档 |
|-----|-------|------|------|
| [simple-elasticsearch-route-starter](sdk/route/elasticsearch/simple-elasticsearch-route-starter) | 1.0.6 | Elasticsearch 多数据源路由 | [README](sdk/route/elasticsearch/simple-elasticsearch-route-starter/README.md) |
| [simple-elasticsearch-search-starter](sdk/search/elasticsearch/simple-elasticsearch-search-starter) | 1.1.3 | Elasticsearch 搜索查询框架 | [README](sdk/search/elasticsearch/simple-elasticsearch-search-starter/README.md) |

**核心特性**：
- 多数据源路由和自动切换
- ES 6.x 和 7.x+ 版本兼容
- 零代码配置驱动的查询和聚合
- 支持日期分割索引
- RESTful API 自动生成

### 🔐 认证与授权

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-aksk-server-starter](sdk/auth/aksk/simple-aksk-server-starter) | 1.0.4 | AKSK 认证服务器 | [USER_MANUAL](sdk/auth/aksk/USER_MANUAL.md) |
| [simple-aksk-feign-redis-client-starter](sdk/auth/aksk/client/simple-aksk-feign-redis-client-starter) | 1.0.0 | AKSK Feign 客户端 | [USER_MANUAL](sdk/auth/aksk/USER_MANUAL.md) |
| [simple-aksk-resttemplate-redis-client-starter](sdk/auth/aksk/client/simple-aksk-resttemplate-redis-client-starter) | 1.0.0 | AKSK RestTemplate 客户端 | [USER_MANUAL](sdk/auth/aksk/USER_MANUAL.md) |
| [simple-aksk-resource-server-starter](sdk/auth/aksk/resource/simple-aksk-resource-server-starter) | 1.0.0 | AKSK 资源服务器（JWT 验证） | [USER_MANUAL](sdk/auth/aksk/USER_MANUAL.md) |
| [simple-aksk-resource-core](sdk/auth/aksk/resource/simple-aksk-resource-core) | 1.0.0 | AKSK 资源核心库（权限控制） | [USER_MANUAL](sdk/auth/aksk/USER_MANUAL.md) |
| [simple-aksk-security-context-starter](sdk/auth/aksk/resource/simple-aksk-security-context-starter) | 1.0.1 | AKSK 安全上下文（Header 解析） | [USER_MANUAL](sdk/auth/aksk/USER_MANUAL.md) |

**核心特性**：
- **双层级 AKSK 管理**：支持平台级（AKP）和用户级（AKU）两种 AKSK 类型
- **OAuth2 标准协议**：基于 Spring Authorization Server 0.4.1，完全符合 OAuth2 规范
- **JWT Token 签发**：使用 RSA 算法签发 JWT Token，支持自定义公私钥和 auth_server_id
- **Scope 权限控制**：基于 OAuth2 scope 的细粒度权限控制，支持精确匹配和通配符防护
- **多种客户端实现**：支持 Feign、RestTemplate 等多种 HTTP 客户端
- **灵活的 Token 管理**：支持 Redis 和 HttpSession 两种 Token 缓存策略
- **资源保护**：提供 JWT 验证和 HTTP Header 解析两种资源保护方式
- **权限注解**：提供 @RequireExpression 等权限校验注解，支持 SpEL 表达式
- **Web 管理界面**：提供完整的 AKSK 和 Token 管理页面，支持在线测试

### 🔒 Redis

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-redis-lock-starter](sdk/lock/redis/simple-redis-lock-starter) | 1.0.0 | Redis 分布式锁 | [README](sdk/lock/redis/simple-redis-lock-starter/README.md) |
| [simple-redis-limiter-starter](sdk/limiter/redis/simple-redis-limiter-starter) | 1.0.1 | Redis 限流器（简单版） | [README](sdk/limiter/redis/simple-redis-limiter-starter/README.md) |
| [smart-redis-limiter-starter](sdk/limiter/redis/smart-redis-limiter-starter) | 1.0.2 | Redis 限流器（智能版） | [README](sdk/limiter/redis/smart-redis-limiter-starter/README.md) |

**核心特性**：
- 简单分布式锁（基于 SETNX + 过期时间）
- 智能限流器（基于固定窗口计数器算法）
- 注解驱动和拦截器模式，配置灵活

### 🔄 重试

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [task-retry-starter](sdk/retry/task-retry-starter) | 1.0.0 | 任务重试框架 | [README](sdk/retry/task-retry-starter/README.md) |
| [redis-retry-starter](sdk/retry/redis-retry-starter) | 1.0.0 | Redis 持久化重试 | [README](sdk/retry/redis-retry-starter/README.md) |

**核心特性**：
- 灵活的重试策略（指数退避、固定延迟）
- 支持持久化和跨实例重试

### 📧 通信

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [mail-client-starter](sdk/mail/mail-client-starter) | 1.0.0 | 邮件发送客户端 | [README](sdk/mail/mail-client-starter/README.md) |
| [b2m-sms-client-starter](sdk/sms/b2m/b2m-sms-client-starter) | 1.0.0 | B2M 短信客户端 | [README](sdk/sms/b2m/b2m-sms-client-starter/README.md) |

**核心特性**：
- 支持 HTML/纯文本邮件
- 短信模板管理
- 异步发送和重试

### ☁️ 对象存储

| SDK | 版本    | 说明 | 文档 |
|-----|-------|------|------|
| [s3-client-starter](sdk/oss/s3-client-starter) | 1.0.0 | AWS S3 兼容存储客户端 | [README](sdk/oss/s3-client-starter/README.md) |

**核心特性**：
- 支持 AWS S3、MinIO、阿里云 OSS 等
- 文件上传下载、预签名 URL
- 自动分片上传

### 📊 监控

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [prometheus-core](sdk/prometheus/prometheus-core) | 1.0.0 | Prometheus 核心库 | [README](sdk/prometheus/prometheus-core/README.md) |
| [prometheus-client-starter](sdk/prometheus/prometheus-client-starter) | 1.0.0 | Prometheus 客户端 | [README](sdk/prometheus/prometheus-client-starter/README.md) |

**核心特性**：
- 自定义指标采集
- 自动暴露 Prometheus 端点
- 与 Spring Actuator 集成
- 支持Remote Write

### 🌐 HTTP 客户端

| SDK | 版本             | 说明 | 文档 |
|-----|----------------|------|------|
| [daydaymap-client-starter](sdk/curl/daydaymap/daydaymap-client-starter) | 1.0.0-SNAPSHOT | DayDayMap API 客户端 | - |
| [opsalert-client-starter](sdk/curl/opsalert/opsalert-client-starter) | 1.0.0-SNAPSHOT    | OpsAlert API 客户端 | - |

**核心特性**：
- 声明式 HTTP 客户端
- 自动重试和熔断
- 请求/响应日志拦截

### 🔒 数据安全

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [smart-keyword-sensitive-starter](sdk/sensitive/keyword/smart-keyword-sensitive-starter) | 1.0.5 | 关键词敏感信息脱敏 | [README](sdk/sensitive/keyword/smart-keyword-sensitive-starter/README.md) |
| [simple-ip-sensitive-starter](sdk/sensitive/ip/simple-ip-sensitive-starter) | 1.0.0 | IP 地址脱敏 | [README](sdk/sensitive/ip/simple-ip-sensitive-starter/README.md) |

**核心特性**：
- **关键词脱敏**：自动识别组织机构（NLP + 规则引擎），智能元信息提取，三级智能降级机制
- **IP 脱敏**：支持 IPv4/IPv6 地址和 CIDR 格式脱敏，自动识别 IP 类型，Jackson 注解支持

### 🧠 自然语言处理

| SDK | 版本    | 说明 | 文档 |
|-----|-------|------|------|
| [natural-language-parser-starter](sdk/natural-language/parser/natural-language-parser-starter) | 1.0.6 | 自然语言查询解析器 | [README](sdk/natural-language/parser/natural-language-parser-starter/README.md) |

**核心特性**：
- 🎯 智能解析中英文查询，支持 15+ 种操作符
- 🚀 AND/OR 逻辑组合，聚合查询，排序分页
- 🌐 多分隔符智能识别，逗号歧义自动处理
- ⚠️ 详细错误提示和智能拼写建议（Levenshtein 算法）
- 🏗️ 策略模式 + 状态机设计，易扩展、高性能

### 🎯 表达式解析

| SDK | 版本    | 说明 | 文档 |
|-----|-------|------|------|
| [condition-expression-parser-starter](sdk/expression/condition/condition-expression-parser-starter) | 1.0.1 | 条件表达式解析器 | [README](sdk/expression/condition/condition-expression-parser-starter/README.md) |

**核心特性**：
- 🎯 **ANTLR 驱动** - 基于 ANTLR 4.10.1，语法严谨，性能优异
- 🚀 **功能完善** - 6大类运算符：比较、集合、模糊匹配、空值检查、逻辑运算、括号优先级
- 📊 **多值类型** - 字符串、整数、浮点数、布尔值、时间范围枚举（30+ 种预定义）
- 🔧 **Visitor 模式** - AST 输出，业务层自由转换为 SQL、ES DSL、MongoDB Query 等
- 📦 **策略模式** - 值解析采用策略模式，优先级可配置，易扩展
- 🌐 **中英文支持** - 关键字支持中英文，大小写不敏感
- ⚠️ **友好错误** - 自定义异常，详细错误信息、位置提示、友好消息

### 📝 日志

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [log-truncate-starter](sdk/log/truncate/log-truncate-starter) | 1.0.0 | 日志截断工具 | [README](sdk/log/truncate/log-truncate-starter/README.md) |

**核心特性**：
- 自动截断超长日志
- 敏感信息脱敏
- 性能优化

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 🔗 相关链接

- [GitHub Issues](https://github.com/Sure-Zzzzzz/normal-sdks/issues)
- [Maven Central](https://central.sonatype.com/search?q=io.github.sure-zzzzzz)

## 📮 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 GitHub Issue

---

**Made with ❤️ by Sure-Zzzzzz**
