# v1.1.1 更新日志

**发布日期：** 2026-05-11

**类型：** Bug Fix Release

---

## Bug 修复

### 1. OR 条件字段错乱

**症状**：OR 逻辑被吞、字段名错乱，导致 search-starter NL 集成测试失败。

| 输入                | 期望                               | 修复前                               |
|-------------------|----------------------------------|-----------------------------------|
| `"年龄小于25或城市等于深圳"` | `age < 25 OR city = "深圳"`        | `age < "城市" AND age = "深圳"`       |
| `"名字包含张或李"`       | `name LIKE "张" OR name LIKE "李"` | `name LIKE "张" AND name LIKE "李"` |

**根因**：

1. `LogicKeywordSplitStrategy.isAsciiIdentifierChar()` 将数字视为标识符字符，导致 `"25或城市"` 不被切分
2. `ConditionParser` 的 OR 上下文处理存在三个问题：
    - `setLogic(OR)` 将**所有**已有条件打包成 OR 组，破坏 AND 条件独立性
    - 无法区分同字段 OR（"名字包含张或李"）和跨字段 OR（"年龄小于25或城市等于深圳"）
    - embedded logic 兜底逻辑导致 EXPECT_LOGIC_OR_END 嵌套 4 层

**修复**：

- **Tokenizer**：`isAsciiIdentifierChar()` 去掉数字判断，`"25或城市"` 正确拆分为 `[25, 或, 城市]`
- **Parser**：引入 `normalizeTokens()` 预处理管道，将残留 embedded logic token 拆分为独立 token；`setLogic(OR)` 只打包最后一个条件；
  `sameFieldOrMode` 标志区分同字段/跨字段 OR

### 2. IN 列表多含"中"

**症状**：`"城市在北京、上海、深圳中"` 产生 `city IN ["北京", "上海", "深圳", "中"]`

**修复**：`buildConditionAndTransitionState()` 截断 IN 值末尾的"中"

---

## 重构

### ConditionParser 状态模式重构

| 改动                        | 效果                                                                        |
|---------------------------|---------------------------------------------------------------------------|
| `ParseContext` 上下文对象      | 封装全部可变状态，handler 签名统一为 `(ParseContext) → void`                            |
| `normalizeTokens()` 预处理管道 | parser 只处理干净 token 流，删除 embedded logic 兜底分支                               |
| 4 个 `handleExpect*()` 方法  | 状态处理器独立方法，switch 只做分派                                                     |
| `sameFieldOrMode` 替代三标志   | 消除 `pendingValueField` / `pendingConditionBuilt` / `orPending` 三个散落布尔     |
| `orGroupIndex` 精确定位       | `setLogic(OR)` 只打包最后条件，OR 组追加 children                                    |
| 删除 8 个辅助方法                | `extractEmbeddedLogic` / `findLogicKeywordIndex` 等移入 `normalizeTokens` 内联 |

**if-else 嵌套**：最大 4 层 → 1 层

---

## 测试

新增 3 个端到端测试场景：

| 场景                   | 输入                 |
|----------------------|--------------------|
| Bug1：跨字段 OR（数字+或+中文） | `"年龄小于25或城市等于深圳"`  |
| Bug1 变体：积分+或+城市      | `"积分大于100或城市等于北京"` |
| Bug2：IN 列表末尾"中"      | `"城市在北京、上海、深圳中"`   |

---

## 升级说明

**兼容性**：与 v1.1.0 完全兼容，无 API 变更。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:natural-language-parser-starter:1.1.1'
}
```
