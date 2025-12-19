# [1.0.1]

## 🎯 项目重命名
- **ORM → Search**：从 `simple-elasticsearch-orm-starter` 重命名为 `simple-elasticsearch-search-starter`
- 更贴合实际功能定位：零代码侵入的 Elasticsearch 查询框架，而非完整的 ORM 框架
- 全面更新包名、类名、配置项、文档中的所有 ORM 引用

## ✨ 功能增强
- **多粒度日期分割索引支持**：
  - 支持按年分割：`yyyy` 格式（如 `log_2025`）
  - 支持按月分割：`yyyy.MM` 或 `yyyy-MM` 格式（如 `log_2025.01`）
  - 支持按天分割：`yyyy.MM.dd` 或 `yyyy-MM-dd` 格式（如 `log_2025.01.15`）
  - 智能检测日期格式粒度，自动生成对应的索引列表
  - 去重逻辑优化，避免按月/年分割时生成重复索引名

## 🔧 代码优化
- **DateGranularity 枚举提取**：
  - 从 IndexRouteProcessor 内部类提取到 `constant` 包
  - 添加 `identifier` 字段（'y', 'm', 'd'），消除硬编码字符
  - 添加 `description` 字段，提供中文说明
  - 封装 `detectFromPattern()` 静态方法，自动检测日期粒度
  - 封装 `increment()` 实例方法，根据粒度递增日期
  - 简化 IndexRouteProcessor 代码约 50 行

## 🐛 Bug 修复
- **IndexRouteProcessor 异常处理**：
  - 修复 `datePattern` 为 null 时异常未被统一包装的问题
  - 所有异常现在都包装为 "Index routing failed: ..." 格式
  - 统一异常处理逻辑，便于上层调用方捕获和处理

## ✅ 测试完善
- **IndexRouteProcessorTest**（19 个测试用例）：
  - 日期分割测试：按天/月/年，多种分隔符（点/横杠/无分隔符）
  - 边界测试：跨月边界、跨年边界、单日查询、单月查询
  - 异常场景：缺少日期格式、无效日期格式
  - 性能测试：大范围日期查询（366 天、120 个月）

- **DateGranularityTest**（27 个测试用例）：
  - 粒度检测测试：多种日期格式、大小写混合、默认值
  - 日期递增测试：按天/月/年递增、跨月/跨年、闰年处理
  - 枚举属性测试：identifier、description 字段验证
  - 连续递增测试：验证多次递增的正确性

## 📝 文档更新
- 更新 README.md：
  - 添加日期分割索引详细说明
  - 补充多粒度支持的配置示例
  - 完善查询示例和说明文档
- 所有配置文件和注释中的 ORM 引用已更新为 Search

## 📊 技术细节
- Java 8 兼容：所有新增代码兼容 Java 8+
- 零依赖：DateGranularity 使用 JDK 内置 LocalDate API
- 线程安全：所有日期操作均为无状态设计
