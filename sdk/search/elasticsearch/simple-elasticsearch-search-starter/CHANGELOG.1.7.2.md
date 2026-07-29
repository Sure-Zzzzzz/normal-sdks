# v1.7.2 更新日志

**发布日期：** 2026-07-29

**类型：** Bug Fix - 修复原生 Elasticsearch Scroll 续页因 `size` 造成的提前结束和数据截断

**依赖版本：**

| 依赖 | 版本 |
|------|------|
| `simple-elasticsearch-search-core` | 1.0.12 |
| `simple-elasticsearch-route-starter` | 1.2.0 |
| `simple-elasticsearch-search-metrics-starter` | 1.0.2 |
| `simple-elasticsearch-search-audit-listener-starter` | 1.0.4 |

---

## 修复内容

Elasticsearch Scroll 的批次大小只由首页 `_search` 请求中的 `size` 决定；后续 `/_search/scroll` 请求只接受 `scroll_id` 和保活时间，不能修改批次大小。

此前 SDK 会在续页保留或填充 `size`，并以“本页命中数是否等于该 `size`”判定是否结束。当续页 `size` 与首页不同，ES 仍按首页批次返回数据，SDK 却可能错误返回 `hasMore=false` 并清理仍有效的 Scroll 上下文，导致后续数据无法继续读取。

v1.7.2 调整为：

- Scroll 首页必须提供 `size`、`scrollTtl` 和非空 `sort`。
- Scroll 续页只允许提供 `scrollId` 和 `scrollTtl`；显式携带 `size` 返回 400。
- Scroll 续页不再填充 `size`，不会再以请求 `size` 推导结束状态。
- 续页返回非空数据时始终返回 `hasMore=true` 和刷新后的 `scrollId`；只有 ES 返回空列表时才返回 `hasMore=false`，并自动清理 Scroll 上下文。
- 无 `size` 的续页响应使用实际返回条数作为 `size`，终止空页返回 `size=0`。
- 标准查询、NL 查询和表达式查询入口统一应用上述续页契约；`countOnly=true` 继续直接走 `_count`，不受 Scroll 参数规则影响。

---

## 新增与调整测试

- 使用真实 ES 测试夹具覆盖标准 `/api/query` 的完整 Scroll 遍历、无重复无丢失、短尾页和整页末批后的终止空页。
- 覆盖首页缺少 `size`、续页携带 `size`、续页缺少 `scrollTtl` 的 400 错误，以及校验失败后 Scroll 上下文仍可正常结束。
- 覆盖 NL 和表达式入口的无 `size` 续页、续页携带 `size` 的 400 与终止空页。
- 覆盖 ES 6.2.2 secondary 数据源的低级 Scroll 续页和终止空页。
- 覆盖 `countOnly=true` 携带 Scroll 分页字段仍按既有 `_count` 语义执行。

---

## 向后兼容性与升级说明

这是 1.7.1 的 Bug Fix，不升级 search-core、route-starter、metrics-starter 或 audit-listener-starter 依赖。

调用方需要将所有 Scroll 续页请求中的 `pagination.size` 删除：

```json
{
  "pagination": {
    "type": "scroll",
    "scrollTtl": "2m",
    "scrollId": "{上一页返回的scrollId}"
  }
}
```

最后一批非空数据后，请继续使用该 `scrollId` 请求一次；收到空 `items`、`size=0`、`hasMore=false` 且无 `scrollId` 后结束遍历。
