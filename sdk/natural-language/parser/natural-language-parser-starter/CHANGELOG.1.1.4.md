# v1.1.4 更新日志

**发布日期：** 2026-06-15

**类型：** Minor Release - 新增前缀/后缀不匹配操作符

---

## 新增内容

### OperatorType 枚举新增三个不匹配操作符

| 枚举值 | 操作符字符串 | 说明 |
|--------|-------------|------|
| `NOT_PREFIX` | `not_prefix` | 前缀不匹配 |
| `NOT_SUFFIX` | `not_suffix` | 后缀不匹配 |

### DefaultKeywordRegistry 新增关键字映射

| 操作符 | 关键字 |
|--------|--------|
| NOT_PREFIX | `开头不是`、`前缀不匹配`、`not_prefix` |
| NOT_SUFFIX | `结尾不是`、`后缀不匹配`、`not_suffix` |

> `needsValue()` 和 `needsMultipleValues()` 无需修改，三个新操作符均需要值、不需要多值。

---

## 升级说明

**兼容性**：与 v1.1.3 完全兼容，无 API 变更，仅新增枚举值和关键字。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:natural-language-parser-starter:1.1.4'
}
```

配合 `simple-elasticsearch-search-starter:1.6.7` 使用。