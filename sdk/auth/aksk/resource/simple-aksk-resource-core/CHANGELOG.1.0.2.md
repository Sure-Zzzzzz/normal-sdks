# Changelog - v1.0.2

## 发布日期

2026-04-13

## 版本类型

BUG修复

## 变更概述

* 原 getList(String prefix) 只支持 indexed 格式（即 scope0、scope1、scope2… 这样的 key）。
* 但实际 scope 的存储方式是 单个 key="scope"，value 为 空格分隔的字符串（/api/** /api/client）。
* 因此 getList("scope") 直接去查 scope0，自然返回空列表。

## 贡献者

- @surezzzzzz
