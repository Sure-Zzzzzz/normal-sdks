# Changelog

## [1.0.1]

### ✨ 新增功能

#### 细粒度降级策略支持

支持在多个级别配置降级策略，优先级从高到低：

**注解级别：**
```java
@SmartRedisLimiter(
    rules = {...},
    fallback = SmartRedisLimiterFallbackStrategy.ALLOW_CODE  // 注解级别
)
public void queryOrder() { }

@SmartRedisLimiter(
    rules = {...},
    fallback = SmartRedisLimiterFallbackStrategy.DENY_CODE  // 支付接口拒绝
)
public void payment() { }
```

**拦截器规则级别:**
```yaml
interceptor:
  rules:
    - path-pattern: /api/query/**
      fallback: allow  # 查询接口Redis异常时放行
      
    - path-pattern: /api/payment/**
      fallback: deny   # 支付接口Redis异常时拒绝
```

**模式默认级别：**
```yaml
annotation:
  default-fallback: allow  # 注解模式默认降级策略

interceptor:
  default-fallback: allow  # 拦截器模式默认降级策略
```

**降级策略优先级：**
```code
注解级别 > 规则级别 > 模式默认 > 全局默认
```
#### 枚举常量支持
新增枚举常量，避免硬编码：
```java
// 使用枚举常量
SmartRedisLimiterFallbackStrategy.ALLOW_CODE
SmartRedisLimiterFallbackStrategy.DENY_CODE

// 替代硬编码字符串
"allow"
"deny"
```

#### 📝 配置兼容性
完全向后兼容 1.0.0 版本，无需修改现有配置。  
1.0.0 配置（仍然有效）：
```yaml
fallback:
  on-redis-error: deny  # 全局配置
```  
1.0.1 推荐配置（可选）：
```yaml
# 全局降级策略
fallback:
  on-redis-error: deny

# 新增：Redis超时控制
redis:
  command-timeout: 3000

# 新增：注解模式默认降级
annotation:
  default-fallback: allow

# 新增：拦截器模式默认降级
interceptor:
  default-fallback: allow
  rules:
    # 新增：规则级别降级
    - path-pattern: /api/payment/**
      fallback: deny
```