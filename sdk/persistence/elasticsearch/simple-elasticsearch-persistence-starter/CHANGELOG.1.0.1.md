# simple-elasticsearch-persistence-starter 1.0.1 CHANGELOG

## 发布日期

2026-07-08

## 版本定位

1.0.1 是写入链路增强版本，主线是补齐写入前扩展能力，并修复 Spring Boot 2.2.x / ES 6.2.2 下 byQuery 的低版本兼容问题。

## 新增功能

### DocumentPreProcessor 写入前处理链

新增 `DocumentPreProcessor` / `DocumentProcessContext` / `DocumentPreProcessorChain`。

支持在 ES 请求构建前统一处理写入文档：

- `index`
- `create`
- `bulk index`
- `bulk create`

不处理：

- 局部 `update`
- script `update`
- `delete`
- `updateByQuery`
- `deleteByQuery`

### DocumentIdHelper

新增 `DocumentIdHelper`，提供：

- 无横线 UUID
- SHA-1 稳定 ID
- SHA-256 稳定 ID
- 多字段稳定拼接

### FieldValueNormalizerHelper

新增 `FieldValueNormalizerHelper`，提供：

- trim
- lowerCase
- trimLowerCase
- fullWidthToHalfWidth
- blankToNull
- collapseWhitespace
- normalizeList

## 兼容性修复

### byQuery 低版本兼容

同步 `updateByQuery` / `deleteByQuery` 改为 low-level REST 执行，避免 Spring Boot 2.2.x 对应的 ES 6.8.x HighLevelClient 自动附带 `ignore_throttled` 参数，导致 ES 6.2.2 服务端拒绝请求。

服务端异步 byQuery 仍复用 low-level REST 提交任务路径。

## 向后兼容性

- 无 `DocumentPreProcessor` Bean 时，写入行为与 1.0.0 保持一致
- 既有 `PersistenceEngine` API 不变
- core 版本不变，仍依赖 `simple-elasticsearch-persistence-core:1.0.1`
- route 版本不变，仍依赖 `simple-elasticsearch-route-starter:1.1.2`

## 测试覆盖

新增覆盖：

- `DocumentPreProcessorChainTest`
- `DocumentIdHelperTest`
- `FieldValueNormalizerHelperTest`
- index 写入前处理集成测试
- bulk 写入前处理集成测试
- `TypedPersistence.withIdResolver` + `DocumentIdHelper` 集成测试
- 2.2.x byQuery 集成测试恢复执行

## 非本版本范围

- audit listener
- metrics listener
- 自动反射扫描实体字段并批量标准化
- 复杂 checkpoint / 续跑
- route async-write 语义调整
- core 模型继续扩展
