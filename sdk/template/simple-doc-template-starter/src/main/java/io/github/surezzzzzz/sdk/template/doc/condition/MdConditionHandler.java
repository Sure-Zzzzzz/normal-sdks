package io.github.surezzzzzz.sdk.template.doc.condition;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.ConditionBlockException;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.support.BooleanHelper;
import io.github.surezzzzzz.sdk.template.doc.support.TagHelper;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Markdown Condition Handler
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class MdConditionHandler implements ConditionHandler {

    private final TagHelper tagHelper;
    private final BooleanHelper booleanHelper;

    @Override
    public byte[] process(byte[] templateBytes, Map<String, Object> data) {
        String markdown = new String(templateBytes, StandardCharsets.UTF_8);
        String processed = processMarkdown(markdown, data);
        return processed.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String supportedSuffix() {
        return SimpleDocTemplateConstant.SUFFIX_MD;
    }

    private String processMarkdown(String markdown, Map<String, Object> data) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        validateConditionNotInsideLoop(lines);
        List<String> output = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trim = line.trim();
            if (isStart(trim)) {
                String key = tagHelper.extractKey(trim, tagHelper.startPrefix());
                int endIndex = findMatchingEnd(lines, i + 1, key);
                boolean keep = data != null && booleanHelper.isTrue(data.get(key));
                if (keep) {
                    for (int j = i + 1; j < endIndex; j++) {
                        output.add(lines[j]);
                    }
                }
                i = endIndex;
            } else if (isEnd(trim)) {
                String key = tagHelper.extractKey(trim, tagHelper.endPrefix());
                throw ConditionBlockException.mismatch(tagHelper.getPrefix(), SimpleDocTemplateConstant.EMPTY, key);
            } else {
                output.add(line);
            }
        }
        return joinLines(output);
    }

    private int findMatchingEnd(String[] lines, int from, String key) {
        for (int i = from; i < lines.length; i++) {
            String trim = lines[i].trim();
            if (isStart(trim)) {
                throw ConditionBlockException.nested(tagHelper.extractKey(trim, tagHelper.startPrefix()));
            }
            if (isEnd(trim)) {
                String endKey = tagHelper.extractKey(trim, tagHelper.endPrefix());
                if (!key.equals(endKey)) {
                    throw ConditionBlockException.mismatch(tagHelper.getPrefix(), key, endKey);
                }
                return i;
            }
        }
        throw ConditionBlockException.mismatch(tagHelper.getPrefix(), key, SimpleDocTemplateConstant.EMPTY);
    }

    private void validateConditionNotInsideLoop(String[] lines) {
        boolean inLoop = false;
        for (String line : lines) {
            String trim = line.trim();
            if (isFor(trim)) {
                inLoop = true;
            } else if (isEndfor(trim)) {
                inLoop = false;
            } else if (inLoop && (isStart(trim) || isEnd(trim))) {
                throw TemplateRenderException.markdownUnsupportedFeature(ErrorMessage.MD_CONDITION_INSIDE_LOOP);
            }
        }
    }

    private boolean isStart(String trim) {
        return trim.startsWith(tagHelper.startPrefix()) && trim.endsWith("]");
    }

    private boolean isEnd(String trim) {
        return trim.startsWith(tagHelper.endPrefix()) && trim.endsWith("]");
    }

    private boolean isFor(String trim) {
        return trim.startsWith(tagHelper.forPrefix()) && trim.endsWith("]");
    }

    private boolean isEndfor(String trim) {
        return trim.startsWith(tagHelper.endforPrefix()) && trim.endsWith("]");
    }

    private String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append(SimpleDocTemplateConstant.MARKDOWN_LINE_SEPARATOR);
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }
}
