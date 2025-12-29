# [1.0.2] - 2025-12-29

## 新增功能

### 1. 固定星号数量可配置

- **功能**: 新增`fixed-mask-length`配置项
- **作用**: 当`keep-length=false`时，使用配置的固定数量星号替代原文长度
- **默认值**: 3个星号
- **配置示例**:
  ```yaml
  io.github.surezzzzzz.sdk.sensitive.keyword:
    default-strategy:
      keep-length: false
      fixed-mask-length: 5  # 自定义固定星号数量
  ```

### 2. 括号内容固定星号数量可配置

- **功能**: 新增`fixed-bracket-mask-length`配置项
- **作用**: 当`keep-length=false`时，括号内容使用配置的固定数量星号
- **默认值**: `null`（未配置时继承自`fixed-mask-length`）
- **应用场景**: 为括号内容（通常是分支机构信息）单独配置脱敏长度
- **配置示例**:
  ```yaml
  io.github.surezzzzzz.sdk.sensitive.keyword:
    default-strategy:
      keep-length: false
      fixed-mask-length: 10           # 主体部分使用10个星号
      fixed-bracket-mask-length: 2    # 括号内容使用2个星号
  ```
- **效果示例**:
  - 配置前（继承主体配置）：`北京科技有限公司（海淀分公司）` → `**********有限公司（**********公司）`
  - 配置后（括号单独配置）：`北京科技有限公司（海淀分公司）` → `**********有限公司（**公司）`

## Bug修复

### 1. 修复keep-length配置未生效

- **问题**: `AsteriskMaskStrategy`完全未使用`config.getKeepLength()`配置
    - `keep-length=false`时仍按原文长度生成星号
    - 无法实现固定长度星号脱敏
    - **Fallback脱敏也未使用keep-length配置**
- **影响**: 配置无效，用户无法隐藏长度信息
- **修复**:
    - `keep-length=true`: 保持原逻辑，每个字符一个星号
    - `keep-length=false`: 使用固定数量星号替换连续脱敏段（数量可通过`fixed-mask-length`配置）
    - **Fallback脱敏同样支持keep-length配置**

### 2. 修复placeholder配置缺失默认值

- **问题**:
    - `mask-type=placeholder`但未配置`placeholder`字段时抛出异常
    - `Strategy.placeholder`无默认值，但验证要求非空
    - `RuntimeStrategy.placeholder`有默认值`[ORG_001]`但未自动填充
- **影响**: 用户配置即报错，无法使用placeholder模式
- **修复**:
    - 在`Strategy.toRuntimeStrategy()`中自动填充默认placeholder
    - 移除强制非空验证，允许运行时使用默认值
- **默认值**: `[ORG_001]`

### 3. 修复fixed-mask-length字段未完整处理

- **问题**: `fixedMaskLength`字段在多个关键方法中缺失处理
    - `RuntimeStrategy.copy()`: 降级时缺少字段复制
    - `RuntimeStrategy.mergeWithDefault()`: 自定义关键词策略合并时缺失
    - `FallbackStrategy`: 缺少`fixedMaskLength`字段
    - `DefaultStrategy.toFallbackRuntimeStrategy()`: Fallback脱敏时未传递配置
- **影响**: 多个场景下无法使用自定义的固定星号数量
    - 智能降级场景：金融/政府/教育机构触发降级后回退到默认值3
    - 自定义关键词：keywords配置的`fixed-mask-length`被忽略
    - Fallback脱敏：无法使用自定义固定星号数量
- **修复**:
    - `copy()`: 添加 `copied.fixedMaskLength = this.fixedMaskLength;`
    - `mergeWithDefault()`: 添加字段合并逻辑
    - `FallbackStrategy`: 添加 `fixedMaskLength` 字段及默认值
    - `toFallbackRuntimeStrategy()`: 添加 `config.setFixedMaskLength(this.fallback.getFixedMaskLength());`

### 4. 修复括号内元信息导致主体脱敏失败

- **问题**: 从括号内容提取的元信息（如行业、组织类型）会导致主体部分脱敏异常
- **修复**:
    - 先提取主体部分`mainBody`（去除括号）：`keyword.substring(0, bracketStart) + keyword.substring(bracketEnd)`
    - 所有元信息字段（地域、行业、品牌、组织类型）只在`mainBody`中查找，不在括号内查找
    - 找到后转换为完整关键词中的实际位置：`actualIndex = (bracketStart >= 0 && index >= bracketStart) ? index + (bracketEnd - bracketStart) : index`
    - 确保括号内提取的元信息不会错误标记到主体部分，避免触发Fallback

## 改进

### 1. 改进保留率计算日志

- **改进**: 添加详细的元信息保留状态日志
- **示例**: `Retention details: region=北京 (masked), industry=邮政 (kept), brand=null (masked), orgType=银行 (kept)`
- **价值**: 帮助用户理解保留字符计数逻辑，避免误解

### 2. 策略日志显示固定星号数量

- **改进**: 当`keep-length=false`时，在策略日志中显示固定星号数量
- **位置**:
    - 结构化数据部分: `[策略: 保留地域=否 保留行业=是 ... 保留长度=否 固定星号数量=10]`
    - 自然语言叙述部分: "保留行业、组织类型，不保留地域、品牌、括号、长度信息，使用固定10个星号"
- **价值**: 明确告知用户当前脱敏使用的固定星号数量，提高日志可读性
