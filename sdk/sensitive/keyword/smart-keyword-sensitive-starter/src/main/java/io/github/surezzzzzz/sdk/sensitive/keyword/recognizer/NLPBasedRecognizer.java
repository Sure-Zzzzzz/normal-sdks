package io.github.surezzzzzz.sdk.sensitive.keyword.recognizer;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NLP-Based Entity Recognizer
 * 基于HanLP的NER识别实体
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
@ConditionalOnClass(name = "com.hankcs.hanlp.HanLP")
@ConditionalOnProperty(prefix = SmartKeywordSensitiveConstant.CONFIG_PREFIX + ".nlp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NLPBasedRecognizer implements EntityRecognizer {

    private volatile boolean available = false;

    public NLPBasedRecognizer() {
        try {
            HanLP.segment("测试");
            this.available = true;
            log.info("NLPBasedRecognizer initialized successfully");
        } catch (Exception e) {
            log.warn("NLPBasedRecognizer initialization failed, will be disabled", e);
        }
    }

    @Override
    public List<RecognizeResult> recognize(String text) {
        if (!StringUtils.hasText(text) || !available) {
            return new ArrayList<>();
        }

        List<RecognizeResult> results = new ArrayList<>();
        Set<String> recognized = new HashSet<>();

        try {
            List<Term> terms = HanLP.segment(text);
            int currentIndex = 0;

            for (Term term : terms) {
                String word = term.word;
                String nature = term.nature.toString();

                // 查找当前词在文本中的位置
                int startIndex = text.indexOf(word, currentIndex);
                if (startIndex == -1) {
                    continue;
                }
                int endIndex = startIndex + word.length();
                currentIndex = endIndex;

                // 识别机构名（nt: 机构团体）
                if ("nt".equals(nature) || "ni".equals(nature)) {
                    if (recognized.add(word)) {
                        results.add(new RecognizeResult(word, startIndex, endIndex, 0.7, "NLP"));
                        log.debug("NLP recognizer found: {} (nature={}) at [{}, {})",
                                word, nature, startIndex, endIndex);
                    }
                }
            }

            log.debug("NLP-based recognizer found {} organizations", results.size());

        } catch (Exception e) {
            log.warn("NLP recognition failed for text: {}", text, e);
        }

        return results;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
