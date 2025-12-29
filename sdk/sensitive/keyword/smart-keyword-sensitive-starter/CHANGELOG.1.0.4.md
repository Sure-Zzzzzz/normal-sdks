# [1.0.4] - 2025-12-29

## Bug修复

### 1. 修复保留率低于阈值时日志描述误导性问题

- **问题**: 当保留率低于阈值时（无需降级），日志显示"保留率X%未达到阈值，但无可降级项，维持原策略"，容易让人误解为"需要降级但没法降"
- **实际情况**: 保留率低于阈值说明脱敏充分，无需降级，这是正常的预期结果
- **修复**:
    - 修改 `TEMPLATE_RATE_NOT_REACH` 为 `TEMPLATE_RATE_BELOW_THRESHOLD`
    - 新日志格式："保留率X%低于阈值Y%，无需降级"
    - 移除误导性的 `NARRATIVE_NO_DOWNGRADE_OPTION`（"但无可降级项"）和 `NARRATIVE_KEEP_ORIGINAL`（"维持原策略"）
- **示例**:
    - 修复前：`保留率15.4%未达到阈值，但无可降级项，维持原策略`
    - 修复后：`保留率15.4%低于阈值60%，无需降级`
- **价值**: 避免用户混淆，清晰表达保留率低于阈值是符合预期的正常情况

## 改进

### 1. 代码重构：消除冗余代码

- **重构内容**:
    - **AsteriskMaskStrategy.java**:
        - 重构 `maskWithFallbackAsterisk()` 方法，使用 `keepBothEnds` 标志统一处理逻辑，减少重复代码
        - 清理注释掉的旧代码
    - **PlaceholderMaskStrategy.java**:
        - 提取 `wrapPlaceholder()` 方法，消除占位符包装逻辑的重复（原在 `maskWithPlaceholder` 和 `maskWithFallback` 中重复）
        - 使用 `Map` 替代 `getCategoryPrefix()` 中的长 if-else-if 链，提升可读性和性能
        - 新增 `CATEGORY_PREFIX_MAP` 静态映射表
    - **BrandExtractor.java**:
        - 提取 `findMatchingBrand()` 方法，消除 `extract()` 和 `extractIndustry()` 中的重复循环逻辑
        - 代码减少约30%，逻辑更清晰
- **价值**: 提高代码可维护性，减少潜在的bug风险，提升性能

## 技术细节

### AsteriskMaskStrategy.java - Fallback方法重构

**重构前**:
- 4个if-else分支，每个分支独立构建脱敏结果
- 存在大量重复的字符串拼接逻辑
- 代码约50行

**重构后**:
- 使用 `keepBothEnds` 布尔标志统一判断逻辑
- 统一脱敏结果构建代码
- 代码减少到约45行
- 逻辑更清晰，易于维护

### PlaceholderMaskStrategy.java - 双重优化

**优化1: 提取占位符包装方法**
- 问题: `maskWithPlaceholder()` 和 `maskWithFallback()` 中存在相同的占位符包装逻辑
- 方案: 新增 `wrapPlaceholder()` 方法统一处理
- 效果: 消除约12行重复代码

**优化2: Map替代if-else链**
- 问题: `getCategoryPrefix()` 方法包含5个if-else-if分支
- 方案: 使用静态 `CATEGORY_PREFIX_MAP` 映射表 + `getOrDefault()`
- 效果:
  - 代码从约20行减少到1行
  - 性能从O(n)提升到O(1)
  - 可读性显著提高

### BrandExtractor.java - 合并重复循环

**重构前**:
- `extract()` 和 `extractIndustry()` 包含几乎相同的循环逻辑
- 只是返回值不同（品牌 vs 行业）
- 重复代码约15行

**重构后**:
- 提取 `findMatchingBrand(String text, boolean returnBrand)` 私有方法
- 通过布尔参数控制返回品牌还是行业
- 代码减少约30%

### 2. 机构类型判定逻辑优化

- **优化内容**:
    - **MaskStrategyHelper.detectSensitiveOrgType()**:
        - 新增MetaInfo参数，优先使用MetaInfo中的行业信息进行机构类型判定
        - 调整关键词匹配顺序，避免政府机构被误判为金融机构
    - **KeywordSensitiveMaskHelper.java** 和 **AsteriskMaskStrategy.java**:
        - 更新调用detectSensitiveOrgType的地方，传入MetaInfo参数
- **价值**: 提高机构类型判定准确率，避免误判导致保留率阈值错误

### 3. 降级策略优化

- **优化内容**:
    - **AsteriskMaskStrategy.java**:
        - 修改降级判断逻辑，将保留率检查从 `<` 改为 `<=`，避免保留率等于阈值时的不必要降级
    - **KeywordSensitiveMaskHelper.java**:
        - 更新降级原因生成逻辑，使用动态retentionThreshold替代固定的SECOND_DOWNGRADE_THRESHOLD
        - 修改比较运算符，确保降级行为一致性
- **价值**: 避免不必要的降级操作，提升脱敏结果的可读性

### 4. 配置驱动优化

- **优化内容**:
    - **MaskStrategyHelper.java**:
        - 移除硬编码的行业关键词检查
        - 改为使用配置驱动的keywordSets进行行业到机构类型的映射
- **价值**: 提高系统灵活性，便于后续扩展和维护
