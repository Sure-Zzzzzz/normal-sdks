# Changelog - simple-iam-server-starter 1.3.4

## 变更概述

修复 `IamWebAccountPhoneController` 漏标 `@SimpleIamServerComponent` 导致 `/iam/web/account/**`
全族端点 404 的缺陷：精准组件扫描（includeFilters=自定义注解）下该控制器从未注册为 Bean，
账号手机号自助功能（状态查询/换绑挑战/绑定/解绑）在容器化部署上整体不可用。属 Bug Fix。

## 变更内容

- 问题背景：该控制器 1.3.0 引入时即漏标注解（全仓 28 个控制器中唯一漏标者）。1.3.0 时代
  联调环境为源码起服形态（测试 classpath 全量扫描），缺陷被掩盖；环境容器化（走 starter
  自动配置的精准扫描）后端点从未注册，未登录请求被认证层以 401 先拦进一步掩盖路由缺失，
  直至登录态页面（Portal 账号手机号）访问才以 404 暴露。
- 根因：`@RestController` 不在精准扫描的 includeFilters 内，未叠加 `@SimpleIamServerComponent`
  的控制器类不会进入容器；无编译期或启动期校验拦截漏标。
- 修复：补 `@SimpleIamServerComponent` 注解（一行）。全仓扫描确认其余 27 个控制器均已标注，
  无同类缺陷。

## 新增测试

- `IamWebControllerRegistrationTest`（2）：容器内 `RequestMappingHandlerMapping` 对
  `/iam/web/account` 四端点逐一断言注册；对六个 `/iam/web/**` 人员端点族做注册齐全性断言
  ——堵住"漏注解静默逃逸"的测试盲区（1.3.0 时代的集成测试只覆盖 auth 族登录挑战端点，
  未断言 account 族控制器注册）。

## 向后兼容性

- API 形状零变化；修复后 `/iam/web/account/phone`（GET）、`/phone-challenges`（POST）、
  `/phone`（PUT/DELETE）恢复可用。
- 无数据库变更（本版本无迁移脚本，schema 与 1.3.3 一致）。

## 升级指南

- 直接升级 starter 至 1.3.4 并重建部署；无数据操作。
- 已知前提：手机号绑定/换绑路径需装配短信投递适配器（b2m）；未装配时端点按既有语义返回
  400（`短信能力未开放`），本次修复不改变该行为。
