# simple-aksk-resource-audit-listener-starter 1.x 封版文档

> **说明**：此文档为 1.x 版本（1.0.0）的冻结快照。如果你使用的是 1.x 版本，请参考此文档。

## 版本

1.0.0

## 前提

项目中已引入以下任一认证模块，它们负责发布 `AkskAccessEvent` 事件：
- `simple-aksk-security-context-starter`（Header 认证）
- `simple-aksk-resource-server-starter`（JWT 认证）

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-audit-listener-starter:1.0.0'
```

## 审计记录 source 字段

| source 值 | 含义 |
|-----------|------|
| `header` | Header 认证（security-context-starter） |
| `jwt` | JWT 认证（resource-server-starter） |

## 版本历史

详见 [CHANGELOG.1.0.0.md](CHANGELOG.1.0.0.md)（如存在）
