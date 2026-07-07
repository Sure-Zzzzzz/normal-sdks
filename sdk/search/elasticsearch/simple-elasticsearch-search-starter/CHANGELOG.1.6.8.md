# v1.6.8 更新日志

**发布日期：** 2026-07-07

**类型：** Bug Fix Release — 通配符索引字段元数据合并

**依赖升级：**

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-elasticsearch-search-core` | 1.0.12 | 1.0.12（不变） |

---

## Bug Fix

### 1. 通配符索引字段元数据合并修复

**问题背景**：通配符索引（日期分割，匹配多个具体索引，如 `test_wildcard--2024` / `test_wildcard--2025` / `test_wildcard--2026`）存在以下问题：

1. `GET /api/indices/test_wildcard--*/fields` **不返回**部分索引新增的字段（如仅存在于 2025/2026 的 `extraField`）。
2. `POST /api/query` 带 `extraField.keyword` 条件查询，报错 `字段 [extraField.keyword] 不存在`。
3. 去掉 `extraField` 查询条件，返回的数据里**却包含** `extraField` 字段。

已知：2024 索引 mapping 没有 `extraField`，2025 / 2026 有。

**根因**：`MappingManager.loadMetadata` 在处理通配符索引时，用 `actualIndices.get(0)` 只取第一个匹配的索引（`ConcurrentHashMap.keySet()` 顺序非确定，可能取到没有该字段的最老索引），解析该索引的 mapping properties 作为字段元数据，完全没有跨索引合并。`mappings` 里其实已经有多份 mapping。

两个加剧因素：
- `ConcurrentHashMap.keySet()` 顺序非确定，`get(0)` 取到哪个索引靠运气
- 解析结果被缓存，fields 接口和 query 接口共用同一份残缺缓存，三个症状同源

**修复**：`specificIndexName == null`（通配符 / 默认）时，合并所有匹配索引的 properties：字段取并集、同名字段最新索引胜出、子字段取并集、类型冲突打 warn；`specificIndexName` 指定时仍单索引解析。

**合并规则**：
- 字段取并集，按字段名去重
- 同名字段：取最新索引的 type / format / searchable / sortable / aggregatable / sensitive / masked / reason
- 子字段（multi-fields）：取所有索引的并集，冲突时最新索引胜出
- 类型冲突：打 warn 日志，采用最新索引定义

**新增方法**：
- `FieldMetadataParser.parseAndMerge()`：解析并合并多个索引的字段元数据
- `FieldMetadataParser.mergeFieldMetadata()`：合并同名字段（私有）
- `MappingManager.parseSingleIndex()`：解析单个索引的字段元数据（私有，抽自原逻辑）

**变更文件**：
- `MappingManager.java`：字段解析逻辑改为合并；`actualIndices` 增加升序排序保证确定性
- `FieldMetadataParser.java`：新增 `parseAndMerge` + `mergeFieldMetadata`

---

## 新增测试

### FieldMetadataParserTest（7 个合并单元测试）

| 测试 | 说明 |
|------|------|
| `testParseAndMergeFieldUnion` | 字段并集，name 被 idxB 覆盖为 text，新增字段 |
| `testParseAndMergeSubFieldUnion` | 子字段并集，title.subFields={keyword, raw}（idxB 新增 raw） |
| `testParseAndMergeTypeConflictNewestWins` | 类型冲突 keyword→text，warn 日志发出，最新类型胜出 |
| `testParseAndMergeEmpty` | 空 map 返回空列表 |
| `testParseAndMergeSingleIndex` | 单索引等价于 parse 单个 |
| `testParseAndMergeSubFieldConflictNewestWins` | 子字段冲突，最新索引子字段胜出 |
| `testParseAndMergeNewSubFieldAppears` | 老索引无子字段，新索引新增 keyword 子字段 |

### IndexDowngradeEndToEndTest（5 个端到端场景，Order 8-12）

| 测试 | 说明 |
|------|------|
| `testFieldsApiMergesAllFields` | /fields 通配符合并后返回全部字段（含部分索引新增的字段） |
| `testFieldsApiExtraFieldKeywordSubField` | 新增字段的 keyword 子字段 type/name 正确返回 |
| `testQueryWithExtraFieldKeyword` | 新增字段.keyword 查询命中（合并后 fieldMap 存在） |
| `testQueryWithExtraField2` | 另一新字段查询命中 |
| `testQueryMultiMonthFieldsData` | 跨月查询数据正确（1月无值、2/3月有值） |

---

## 向后兼容性

✅ **完全向后兼容**

此次变更仅修复通配符索引的字段探测行为，无破坏性变更。现有调用无需修改。

**唯一行为变更**：通配符索引的 fields 元数据从"非确定单索引"变为"全量合并（最新胜出）"。这是修复而非破坏——旧的非确定行为本就无法保证，合并后语义更正确。

---

## 升级指南

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.6.8'
}
```

依赖的 `simple-elasticsearch-search-core:1.0.12` 不变。
