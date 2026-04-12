# Changelog - v1.3.0

## 发布日期

2026-04-12

## 版本类型

**Minor Release** - 新增功能，向后兼容

## 变更概述

新增 search_after 翻页模式配置（`searchAfterMode`），支持 PIT 快照翻页和无 tiebreaker 模式，解决 `_id` fielddata 内存压力问题；同时完成架构自闭环，移除对 route-starter 内部 API 的耦合。

---

## 新增功能

### search_after 翻页模式（`searchAfterMode`）

**背景：**
v1.2.1 自动追加 `_id ASC` 作为 tiebreaker，在内存较小的 ES 集群上触发 fielddata OOM：
```
[fielddata] Data too large, data for [_id] would be [13318046244/12.4gb]
```

**新增 `pagination.searchAfterMode` 字段，支持三种模式：**

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| `tiebreaker`（默认） | 自动追加 `_id ASC`，兼容 v1.2.1 行为 | 内存充足，需要稳定排序 |
| `pit` | 使用 Point In Time 快照翻页，不追加 `_id` | 内存敏感，需要数据一致性，ES 7.10+ |
| `none` | 不追加任何 tiebreaker | 排序字段本身已唯一 |

**PIT 模式使用示例：**

第一页请求（不需要 pitId）：
```json
{
  "pagination": {
    "type": "search_after",
    "searchAfterMode": "pit",
    "pitKeepAlive": "1m",
    "size": 100,
    "sort": [{"field": "timestamp", "order": "desc"}]
  }
}
```

第一页响应（库自动 open PIT）：
```json
{
  "pagination": {
    "hasMore": true,
    "pitId": "46ToAwMD...",
    "nextSearchAfter": [1704110400000]
  }
}
```

后续翻页将 `pitId` 和 `nextSearchAfter` 带回即可，最后一页 `hasMore=false` 时库自动 close PIT。

### PIT 服务端配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            pit:
              max-keep-alive: 5m  # PIT 保活时间上限，默认 5m
```

- 用户传入的 `pitKeepAlive` 不能超过此值，超过则报 400
- `pitKeepAlive` 是两次翻页之间的空闲超时（滑动窗口），每次查询自动续期
- 泄漏上限 = `max-keep-alive`，与翻页总时长无关

### PIT 版本校验

- ES 版本不支持 PIT（< 7.10）：报 400，提示不支持
- ES 版本未就绪（探测中）：报 400，提示稍后重试
- 错误信息不暴露具体版本号，防止信息泄露

---

## 架构优化

### 移除对 route-starter 内部 API 的耦合

**问题：**
search-starter 1.0.4 为绕过 Spring Boot 2.4.x CGLIB 问题，在 `SimpleElasticsearchSearchAutoConfiguration` 中抢先注册 `elasticsearchRestTemplate` bean，导致直接依赖 route-starter 内部的 `RouteTemplateProxy`、`IndexNameExtractor` 等类。route-starter 1.0.6 包路径重构后，这个耦合导致编译失败。

**修复：**
route-starter 1.0.7 已将 CGLIB 降级逻辑收归内部自闭环处理，search-starter 删除 `elasticsearchRestTemplate` bean，不再感知 route 内部实现。

---

## 依赖升级

- `simple-elasticsearch-search-core`: `1.0.1` → `1.0.3`
- `simple-elasticsearch-route-starter`: `1.0.5` → `1.0.7`

---

## 向后兼容性

✅ **完全向后兼容**

- 不传 `searchAfterMode` 时默认 `tiebreaker`，行为与 v1.2.1 完全一致
- 现有调用方无需任何修改

---

## 升级指南

### 从 1.2.1 升级到 1.3.0

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-starter:1.3.0"
```

无需修改任何代码或配置。如需解决 `_id` fielddata 内存问题，按需配置 `searchAfterMode`。

## 贡献者

- @surezzzzzz
