# v1.1.3 更新日志

## ✨ 新功能

### Feature 1: 字段折叠（Collapse）支持 ✅

**功能描述：**

新增字段折叠（collapse）功能，实现字段去重查询。按指定字段折叠，每个唯一值只返回一条文档，支持深度分页。

**支持场景：**

- 按单个字段去重（如：源IP、用户ID等）
- 去重后的数据支持 search_after 深度分页
- 可与字段投影、条件查询、排序等功能组合使用

**示例：**

**基础用法 - 去重获取所有源IP：**
```json
{
  "index": "logs-*",
  "fields": ["源IP", "@timestamp"],
  "collapse": {
    "field": "源IP"
  },
  "pagination": {
    "type": "offset",
    "page": 1,
    "size": 100,
    "sort": [
      {"field": "@timestamp", "order": "desc"}
    ]
  }
}
```

**深度分页 - 去重后翻页：**
```json
{
  "index": "logs-*",
  "fields": ["源IP"],
  "collapse": {
    "field": "源IP"
  },
  "pagination": {
    "type": "search_after",
    "size": 100,
    "searchAfter": ["192.168.1.100"],
    "sort": [
      {"field": "源IP", "order": "asc"}
    ]
  }
}
```

**设计考量：**

- **单字段折叠**：仅支持单个字段去重（ES 原生限制）
- **必须有排序**：使用 collapse 时必须指定排序字段（确保翻页一致性）
- **多字段去重**：ES 不支持多字段组合去重，建议：
  - 方案1：索引时创建组合字段
  - 方案2：使用 Composite Aggregation
  - 方案3：应用层去重

**新增字段：**

- `QueryRequest.collapse` - 字段折叠配置
- `QueryRequest.CollapseField` - 折叠字段定义

**修改文件：**

- [QueryRequest.java](src/main/java/io/github/surezzzzzz/sdk/elasticsearch/search/query/model/QueryRequest.java) - 添加 collapse 字段
- [QueryExecutorImpl.java](src/main/java/io/github/surezzzzzz/sdk/elasticsearch/search/query/executor/QueryExecutorImpl.java) - 实现 collapse DSL 生成
- [ErrorCode.java](src/main/java/io/github/surezzzzzz/sdk/elasticsearch/search/constant/ErrorCode.java) - 添加 COLLAPSE_SORT_REQUIRED 错误码

---

## ✅ 测试验证

**测试场景：**

1. ✅ 基础 collapse - 按字段去重
2. ✅ Collapse + search_after - 去重后深度分页
3. ✅ Collapse + 字段投影 - 只返回指定字段
4. ✅ Collapse 排序验证 - 必须指定排序

**测试文件：**

- [CollapseTest.java](src/test/java/io/github/surezzzzzz/sdk/elasticsearch/search/test/cases/CollapseTest.java)

---

## 🔄 升级说明

**兼容性：** 完全向后兼容 v1.1.2，无破坏性变更。

**升级方式：**

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.1.3'
}
```

**新增能力：**

字段去重查询，适合以下场景：

- 获取所有不同的 IP 地址
- 查询所有不同的用户
- 按某个字段去重并分页

**使用限制：**

1. 使用 collapse 时**必须指定排序字段**
2. 仅支持**单字段去重**（ES 原生限制）
3. 多字段去重需自行处理（组合字段或聚合）

---

## 📊 影响分析

**新功能影响：**

- 受益用户：需要字段去重查询的用户
- 新增能力：字段折叠去重 + 深度分页
- 建议操作：可选升级

**对比 TERMS 聚合：**

| 特性 | TERMS 聚合 | Collapse |
|------|-----------|----------|
| 去重 | ✅ | ✅ |
| 翻页 | ❌ (一次性返回) | ✅ (search_after) |
| 返回数量 | 最多 65535 | 无限制 |
| 性能 | 适合小数据量 | 适合大数据量 |
| 使用场景 | 统计分析 | 查询列表 |

---

