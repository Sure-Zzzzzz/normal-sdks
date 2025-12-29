# [1.0.1] - 2025-12-29

## 新增功能

### Patterns 别名映射
- **功能**: 支持为关键词配置多个别名/简称，统一脱敏策略
- **实现**: 基于AC自动机的高效pattern匹配，所有patterns共享主keyword的strategy和meta

### 预定义Meta信息
- **功能**: 为关键词预定义元信息(region/industry/org-type/brand)，跳过自动提取
- **优势**:
  - 准确性: 避免自动提取的误判
  - 性能: 跳过NLP调用，脱敏速度更快
  - 一致性: 所有patterns使用统一的meta信息

## Bug修复

### 1. 修复Meta配置缺失brand字段
- **问题**: `SmartKeywordSensitiveProperties.Meta`类缺少`brand`字段
- **影响**: 用户无法在配置中定义brand元信息
- **修复**: 添加`brand`字段及相应的`setBrand`调用

### 2. 修复Patterns未共享主keyword的meta和strategy
- **问题**: Patterns匹配后未正确使用主keyword的预定义meta和strategy
  - `KeywordRegistry`只为主keyword存储了meta和strategy
  - Pattern匹配时`getMetaInfo(pattern)`返回null
  - 触发自动提取，可能导致误判
- **影响**: Patterns功能不完整，预定义meta对patterns无效
- **修复**:
  - 在注册patterns时，同时为pattern存储主keyword的strategy和meta
  - 确保pattern匹配时优先使用预定义信息
- **测试**: 添加`PatternsWithMetaTest`和`PredefinedMetaDebugTest`端到端测试

### 3. 修复日志格式化占位符错误

## 改进

### 文档更新
- **README.md**: 新增Patterns和Meta配置说明，包含完整的应用场景和最佳实践
