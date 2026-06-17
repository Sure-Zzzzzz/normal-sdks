# 更新日志

## v1.0.4 (2026-06-17)

**依赖升级：**

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-elasticsearch-search-core` | 1.0.11 | 1.0.12 |
| `simple-elasticsearch-search-starter`（测试依赖） | 1.6.6 | 1.6.7 |

**说明：**

search-core 1.0.12 新增 `NOT_PREFIX` / `NOT_SUFFIX` / `NOT_REGEX` 三个不匹配操作符枚举值，本模块仅监听 Spring 事件，无直接引用，零功能变更，完全向后兼容。

---