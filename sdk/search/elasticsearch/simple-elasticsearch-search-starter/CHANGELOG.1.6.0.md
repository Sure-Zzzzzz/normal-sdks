# v1.6.0 更新日志

**发布日期：** 2026-05-13

**类型：** Feature Release

---

## 新功能

### NL 解析器集成升级（nl-parser 1.0.6 → 1.1.3）

- **FieldBinder 字段绑定**：`SearchFieldBinder` 基于 field-mapping 配置自动将中文字段名（如"年龄"）映射为 ES 字段名（如"age"），无需手动翻译
- **IntentTranslator SPI 适配**：`SimpleElasticsearchIntentTranslator` 实现新版 `IntentTranslator` 接口，通过 `TranslateContext` 传递 FieldBinder 和数据源
- **Collapse 字段折叠**：翻译器新增 Collapse 转换，支持 NL 去重查询（如"按源IP去重"）
- **逻辑条件翻译修复**：逻辑组合（AND/OR）不再将父节点当作子条件，避免产生无效的 null field 子条件
- **nl-parser 1.1.0 API 适配**：`getName()` → `getNameHint()`，`getChildren()` → `getSubAggs()`，`getLimit()` → `getSize()`

### NL 集成测试

- 新增 `NLIntegrationTest`：8 个端到端场景覆盖（多条件AND、IN、跨字段OR、BETWEEN、字段绑定等）
- 新增 `Playbook` 测试：验证通配符索引名 DSL 转换
- 新增 `test_nl_user_index` 测试索引配置及 field-mapping

---

## 依赖升级

- `natural-language-parser-starter` 1.0.6 → 1.1.3
- `simple-elasticsearch-route-starter` 1.0.8 → 1.0.10（`ElasticsearchApiConstant` 重命名为 `SimpleElasticsearchRouteConstant`，完全向后兼容）

---

## 升级说明

**兼容性**：与 v1.5.8 完全兼容，无破坏性 API 变更。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.6.0'
}
```
