# smart-redis-limiter-starter

智能Redis限流器启动器，提供基于注解和拦截器的灵活限流方案，支持多种限流策略和降级机制。

## 功能特性

- **双模式支持**：注解模式 + 拦截器模式
- **多种限流策略**：方法级、路径级、IP级限流
- **多时间窗口**：支持多个时间窗口的复合限流规则
- **智能降级**：Redis异常时的降级策略（放行/拒绝）
- **超时控制**：Redis命令超时保护机制
- **集群支持**：支持Redis集群模式
- **管理接口**：提供限流状态管理接口
- **高性能**：基于Lua脚本的原子操作

## 快速开始

### 1. 添加依赖

```gradle
    implementation 'io.github.surezzzzzz:smart-redis-limiter-starter:1.0.0'
    // 必须显式引入以下依赖
    implementation "org.springframework.boot:spring-boot-starter-data-redis"
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.springframework.boot:spring-boot-starter-web'
```

### 2. 基本配置

```yaml
spring:
  application:
    name: smart-limiter-test

# 智能限流器配置
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            smart:
              enable: true
              me: test-service
              mode: both  # 同时支持注解和拦截器
              redis:
                command-timeout: 3000         # 命令超时：3秒

              # 注解模式配置（SmartLimiterTest使用）
              annotation:
                default-key-strategy: method
                default-limits:
                  - count: 100
                    window: 1
                    unit: seconds

              # 拦截器模式配置（SmartLimiterWebTest使用）
              interceptor:
                default-key-strategy: path
                default-limits:
                  - count: 50
                    window: 10
                    unit: seconds

                exclude-patterns:
                  - /swagger-ui/**
                  - /v3/api-docs/**
                  - /api/health

                rules:
                  # 规则1：/api/public/** GET请求，每个路径独立限流
                  - path-pattern: /api/public/**
                    method: GET
                    key-strategy: path  # 独立：/api/public/test 和 /api/public/other 各限5次
                    limits:
                      - count: 5
                        window: 10
                        unit: seconds

                  # 规则2：/api/user/** GET请求，每个用户ID独立限流
                  - path-pattern: /api/user/**
                    method: GET
                    key-strategy: path  # 独立：/api/user/123 和 /api/user/456 各限10次
                    limits:
                      - count: 10
                        window: 10
                        unit: seconds

                  # 规则3：/api/user/** POST请求，所有用户共享限流
                  - path-pattern: /api/user/**
                    method: POST
                    key-strategy: path-pattern  # 共享：所有POST /api/user/** 共享5次
                    limits:
                      - count: 5
                        window: 10
                        unit: seconds

                  # 规则4：精确路径 /api/user/123，优先级最高
                  - path-pattern: /api/user/123
                    method: GET
                    key-strategy: path
                    limits:
                      - count: 15
                        window: 10
                        unit: seconds

              # 降级策略
              fallback:
                on-redis-error: deny  # Redis异常时拒绝请求

              # 异常处理
              management:
                enable-default-exception-handler: true

# 日志配置
logging:
  level:
    io.github.surezzzzzz.sdk.limiter.redis.smart: DEBUG
    redis.embedded: WARN
    org.springframework: WARN

```

## 配置详解

### 核心配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enable` | Boolean | `false` | 是否启用限流器 |
| `me` | String | - | 服务标识（必填） |
| `mode` | String | `both` | 限流模式：`annotation`、`interceptor`、`both` |

### 限流规则

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `count` | Integer | - | 时间窗口内允许的最大请求数 |
| `window` | Integer | - | 时间窗口大小 |
| `unit` | TimeUnit | `SECONDS` | 时间单位：`SECONDS`、`MINUTES`、`HOURS`、`DAYS` |

### Key生成策略

| 策略             | 说明 | 使用场景 |
|----------------|------|----------|
| `method`       | 方法级别 | 基于方法签名生成Key |
| `path`         | 路径级别 | 基于请求路径生成Key（独立限流） |
| `path-pattern` | 路径模式级别 | 基于路径模式生成Key（共享限流） |
| `ip`           | IP级别 | 基于客户端IP生成Key |

### 降级策略

| 策略 | 说明 |
|------|------|
| `allow` | Redis异常时放行请求 |
| `deny` | Redis异常时拒绝请求 |

## 高级特性

### 1. 多时间窗口限流

支持同时配置多个时间窗口的限流规则：

```yaml
rules:
  - count: 10    # 每秒最多10次
    window: 1
    unit: SECONDS
  - count: 100   # 每分钟最多100次
    window: 60
    unit: SECONDS
  - count: 1000  # 每小时最多1000次
    window: 3600
    unit: SECONDS
```

### 2. Redis集群支持

自动检测Redis集群模式，无需额外配置。

### 3. 超时保护

配置Redis命令超时时间，防止Redis故障影响服务：

```yaml
redis:
  command-timeout: 3000  # 3秒超时
```

### 4. 管理


```yaml
management:
  enable-default-exception-handler: true
```

## 性能优化

### 1. Lua脚本优化

使用原子性的Lua脚本执行限流检查，确保高并发下的准确性。

### 2. 连接池配置

建议配置合适的Redis连接池参数：

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
```

### 3. Key设计

合理设计限流Key，避免Key冲突：

```
smart-limiter:{me}:{key}:{window}s
```

## 监控与调试

### 2. 监控指标

限流触发时会记录日志：

```
SmartRedisLimiter 限流触发: key=smart-limiter:user-service:UserController.getUser:60s, rules=[10 requests/60s]
```

### 3. 调试模式

开启调试日志查看详细的限流过程：

```
SmartRedisLimiter 限流通过: key=smart-limiter:user-service:UserController.getUser:60s
```

## 最佳实践

### 1. 服务标识规范

使用有意义的服务标识，便于监控和管理：

```yaml
me: user-service # 包含服务名
```

### 2. 限流规则设计

- 根据业务特点设置合理的限流阈值
- 使用多时间窗口进行细粒度控制
- 为不同接口设置不同的限流策略

### 3. 异常处理

- 统一处理限流异常
- 提供友好的错误提示
- 记录限流日志便于分析

### 4. 性能考虑

- 合理设置Redis超时时间
- 使用连接池优化Redis性能
- 避免过度复杂的限流规则
