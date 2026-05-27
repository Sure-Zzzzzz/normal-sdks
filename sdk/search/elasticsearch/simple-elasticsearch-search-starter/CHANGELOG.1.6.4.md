# v1.6.4 更新日志

**发布日期：** 2026-05-27

**类型：** Feature Enhancement

**依赖升级：** 无（`simple-elasticsearch-search-core` 版本不变，仍为 1.0.10）

---

## 功能增强

### 条件表达式支持中英混合标签

**问题**：表达式中的中英混合中文标签（如 `订单ID`）无法被解析器识别，导致语法错误。虽然英文字段名可以直接使用，但前端搜索框场景下用户更习惯输入中文标签。

**修复**：在表达式解析前，将表达式中的中文标签预替换为英文字段名，再交给解析器处理。按标签长度降序替换，避免短标签（如"订单"）优先于长标签（如"订单ID"）被匹配。

**受影响方法：**
- `ExpressionService.translate()` — 已支持中英混合标签
- `ExpressionService.validate()` — 已支持中英混合标签，`index` 参数变更为必填（required=true），确保 label 预替换使用正确的字段映射

**配置示例：**

```yaml
indices:
  - name: "order_*"
    alias: order
    field-mapping:
      order_id:
        - 订单号
        - 订单ID       # 中英混合标签
      status:
        - 状态
        - 订单状态
```

**表达式示例：**

```json
// 以下表达式现在均可正常工作
POST /api/query/expression
{
  "index": "order",
  "expression": "订单ID = 'xxx' AND 状态 = '已完成'"
}
```

```bash
# validate 端点新增 index 参数，支持中文标签校验
GET /api/expression/validate?expression=订单ID = 'xxx' AND 状态 = '已完成'&index=order
```

**实现细节：**

`normalizeFieldNames()` 展开 `field-mapping` 为 `label → fieldName` 列表，按长度降序排列后依次替换，确保长标签优先匹配。替换发生在 AST 解析之前，字符串值中的标签（如 `'订单ID'`）也会被正确替换，解析器最终收到的是纯英文字段名。

---

## 升级说明

**兼容性**：与 v1.6.3 兼容，`/api/query/expression` 和 `/api/agg/expression` 接口行为不变。

**API 变更**：`/api/expression/validate` 接口 `index` 参数由可选变为必填。调用方需传入目标索引别名，以确保 label 预替换使用正确的字段映射。建议尽早升级，避免后续版本移除对 `index` 参数的兼容处理。

```gradle
dependencies {
    implementation 'io.github.surezzzzz:simple-elasticsearch-search-starter:1.6.4'
}
```