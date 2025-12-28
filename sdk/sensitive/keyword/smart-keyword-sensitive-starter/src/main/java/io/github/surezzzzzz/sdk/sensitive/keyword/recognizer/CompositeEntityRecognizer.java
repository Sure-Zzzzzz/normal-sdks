package io.github.surezzzzzz.sdk.sensitive.keyword.recognizer;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Composite Entity Recognizer
 * 组合多个识别器的结果
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class CompositeEntityRecognizer implements EntityRecognizer {

    private final RuleBasedRecognizer ruleBasedRecognizer;
    private final NLPBasedRecognizer nlpBasedRecognizer;

    /**
     * 构造函数 - 使用构造器注入（统一注入风格）
     *
     * @param ruleBasedRecognizer 规则识别器（必须）
     * @param nlpBasedRecognizer  NLP识别器（可选）
     */
    @Autowired
    public CompositeEntityRecognizer(RuleBasedRecognizer ruleBasedRecognizer,
                                     @Autowired(required = false) NLPBasedRecognizer nlpBasedRecognizer) {
        this.ruleBasedRecognizer = ruleBasedRecognizer;
        this.nlpBasedRecognizer = nlpBasedRecognizer;

        log.info("CompositeEntityRecognizer initialized: RuleBasedRecognizer={}, NLPBasedRecognizer={}",
                ruleBasedRecognizer != null,
                nlpBasedRecognizer != null && nlpBasedRecognizer.isAvailable());
    }

    @Override
    public List<RecognizeResult> recognize(String text) {
        if (!StringUtils.hasText(text)) {
            return new ArrayList<>();
        }

        if (ruleBasedRecognizer == null) {
            log.warn("RuleBasedRecognizer is not available");
            return new ArrayList<>();
        }

        List<RecognizeResult> allResults = new ArrayList<>();

        // 1. 收集规则识别器的结果（必须）
        try {
            List<RecognizeResult> ruleResults = ruleBasedRecognizer.recognize(text);
            allResults.addAll(ruleResults);
            log.debug("RuleBasedRecognizer found {} organizations", ruleResults.size());
        } catch (Exception e) {
            log.warn("RuleBasedRecognizer recognition failed", e);
        }

        // 2. 收集NLP识别器的结果（可选）
        if (nlpBasedRecognizer != null && nlpBasedRecognizer.isAvailable()) {
            try {
                List<RecognizeResult> nlpResults = nlpBasedRecognizer.recognize(text);
                allResults.addAll(nlpResults);
                log.debug("NLPBasedRecognizer found {} organizations", nlpResults.size());
            } catch (Exception e) {
                log.warn("NLPBasedRecognizer recognition failed", e);
            }
        }

        // 3. 合并去重
        return mergeResults(allResults);
    }

    /**
     * 合并识别结果（去重、去除重叠、按位置排序）
     */
    private List<RecognizeResult> mergeResults(List<RecognizeResult> results) {
        if (results.isEmpty()) {
            return results;
        }

        // 1. 按位置排序
        results.sort(Comparator.comparingInt(RecognizeResult::getStartIndex));

        // 2. 去除重叠（保留最长的）
        List<RecognizeResult> nonOverlapping = new ArrayList<>();

        for (RecognizeResult current : results) {
            boolean shouldAdd = true;

            // 检查是否与已添加的结果重叠
            for (int i = 0; i < nonOverlapping.size(); i++) {
                RecognizeResult existing = nonOverlapping.get(i);

                // 判断是否重叠
                if (isOverlapping(current, existing)) {
                    // 保留更长的那个
                    if (current.getEntity().length() > existing.getEntity().length()) {
                        nonOverlapping.set(i, current);
                        log.debug("Replaced overlapping result: {} -> {}",
                                existing.getEntity(), current.getEntity());
                    }
                    shouldAdd = false;
                    break;
                }
            }

            if (shouldAdd) {
                nonOverlapping.add(current);
            }
        }

        // 3. 按组织名去重，保留置信度最高的
        Map<String, RecognizeResult> resultMap = new LinkedHashMap<>();
        for (RecognizeResult result : nonOverlapping) {
            String key = result.getEntity();
            RecognizeResult existing = resultMap.get(key);

            if (existing == null || result.getConfidence() > existing.getConfidence()) {
                resultMap.put(key, result);
            }
        }

        // 4. 按位置排序
        List<RecognizeResult> merged = new ArrayList<>(resultMap.values());
        merged.sort(Comparator.comparingInt(RecognizeResult::getStartIndex));

        log.debug("Merged {} unique non-overlapping organizations from {} total results",
                merged.size(), results.size());

        return merged;
    }

    /**
     * 判断两个识别结果是否重叠
     */
    private boolean isOverlapping(RecognizeResult r1, RecognizeResult r2) {
        // 有交集就算重叠
        return !(r1.getEndIndex() <= r2.getStartIndex() || r2.getEndIndex() <= r1.getStartIndex());
    }

    @Override
    public boolean isAvailable() {
        return ruleBasedRecognizer != null;
    }
}
