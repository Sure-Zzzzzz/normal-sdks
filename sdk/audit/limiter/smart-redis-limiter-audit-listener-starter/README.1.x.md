# smart-redis-limiter-audit-listener-starter 1.x 封版文档

> **说明**：此文档是 `1.0.0` 的冻结快照。1.x 已封版，不再提供功能、兼容性或维护版本；请升级到 [2.x README](README.md)。

## 发布组合

| limiter-starter | limiter-core | audit-listener |
|-----------------|--------------|----------------|
| 1.1.3 | 1.1.6 | 1.0.0 |

历史 limiter 主线最终停在 starter `1.1.4` / core `1.1.7`，但没有为该最终组合再发布 audit artifact。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter:1.1.3'
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-audit-listener-starter:1.0.0'
```

## 历史限制

- 不支持 2.x Redis Route、fallback 和远程动态策略的完整执行快照。
- 历史 `extra` 会透传 Context attributes；该行为不再作为安全审计扩展机制支持。
- 升级时应将 limiter 与 audit listener 一起升级到当前 2.x 映射。
