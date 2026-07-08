# simple-elasticsearch-persistence-core 1.0.1 更新日志

## 发布日期

2026-07-08

## 版本定位

1.0.1 是小版本增强，在 `UpdateOptions` 上新增 `upsertDoc` 和 `scriptedUpsert` 两个字段，
支持 ES scripted_upsert 语义（文档不存在时通过 Painless 脚本初始化字段）。
其他模型、枚举、异常、事件均无变化，向后兼容。

## 新增功能

### UpdateOptions 新增 scriptedUpsert + upsertDoc

**变更文件**：`model/option/UpdateOptions.java`

新增两个字段：

```java
/**
 * 文档不存在时的兜底初始化内容（Map 或 @Document 实体均可）。
 * 配合 scriptedUpsert=true 使用：脚本负责写字段，upsertDoc 仅用于在文档不存在时触发脚本执行。
 */
private Object upsertDoc;

/**
 * true 时无论文档是否存在都执行脚本（scripted_upsert）；
 * false/null 时仅已存在文档才执行脚本。
 */
private Boolean scriptedUpsert;
```

**典型用途**：实现「createTime 只写一次，updateTime 每次刷新」的 upsert 语义：

```java
UpdateOptions.builder()
    .scriptedUpsert(true)
    .upsertDoc(Map.of())   // 空 Map 即可，让 ES 在文档不存在时触发脚本
    .build()
```

## 行为说明

### scripted_upsert 执行模型

| 文档状态 | scriptedUpsert=false/null | scriptedUpsert=true |
|----------|--------------------------|---------------------|
| 已存在 | 执行脚本 | 执行脚本 |
| 不存在 | 返回 404 / 报错 | 以 upsertDoc 为基础执行脚本 |

- `upsertDoc` 为空 Map 时，ES 以空文档为起点执行脚本，脚本可以写入任意字段。
- `scriptedUpsert=true` 时 `upsertDoc` 必须非 null，否则 ES 会返回请求错误。

### 与 docAsUpsert 的区别

| 选项 | 含义 | 脚本 |
|------|------|------|
| `docAsUpsert=true` | 文档不存在时将 `doc` 字段作为初始内容插入 | 无脚本 |
| `scriptedUpsert=true` | 文档不存在时以 `upsertDoc` 为基础执行脚本 | 必须有脚本 |

两者互斥，同时设置时 `scriptedUpsert` 优先。

## 向后兼容性

- `UpdateOptions` 新增字段默认为 `null`，不影响已有调用。
- 其余所有类（请求模型、结果模型、枚举、异常、事件）无任何变化。
- `simple-elasticsearch-persistence-starter` 从 `1.0.0` 升级依赖至 `1.0.1` 后，
  `buildUpdateRequest` 中新增对两个字段的透传处理。

## 测试覆盖

`PersistenceEngineIntegrationTest` 新增 `testScriptedUpsert` 用例，覆盖：

1. 文档不存在时首次写入：`createTime` 和 `updateTime` 均被脚本初始化
2. 文档已存在时再次写入：`createTime` 保留首次值，`updateTime` 更新为最新值

4 版本矩阵（2.2.x / 2.3.12 / 2.4.5 / 2.7.9）全量验证通过。

## 非本版本范围

- `UpdateOptions` 以外的其他 Option 模型变更
- 枚举、异常、事件模型变更
