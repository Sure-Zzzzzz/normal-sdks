package io.github.surezzzzzz.sdk.template.doc.renderer.md;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.document.Document;
import io.github.surezzzzzz.sdk.template.doc.document.MdDocument;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import io.github.surezzzzzz.sdk.template.doc.model.MdImageReference;
import io.github.surezzzzzz.sdk.template.doc.renderer.Renderer;
import io.github.surezzzzzz.sdk.template.doc.support.TagHelper;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Markdown Renderer
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class MdRenderer implements Renderer {

    private final TagHelper tagHelper;

    @Override
    public Document render(byte[] templateBytes, Map<String, Object> data) {
        String markdown = new String(templateBytes, StandardCharsets.UTF_8);
        RenderState state = new RenderState();
        String expanded = expandLoops(markdown, data, state);
        detectChart(expanded);
        String internal = replaceTags(expanded, data, state, true);
        String visible = replaceTags(expanded, data, state.copyWithoutReferences(), false);
        return new MdDocument(visible.getBytes(StandardCharsets.UTF_8),
                internal.getBytes(StandardCharsets.UTF_8), state.imageReferences);
    }

    @Override
    public String supportedSuffix() {
        return SimpleDocTemplateConstant.SUFFIX_MD;
    }

    private String expandLoops(String markdown, Map<String, Object> data, RenderState state) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<String> output = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String trim = lines[i].trim();
            if (isEndfor(trim)) {
                throw TemplateRenderException.markdownRenderFailed(
                        String.format(ErrorMessage.MD_LOOP_ORPHAN_ENDFOR, trim));
            }
            if (isFor(trim)) {
                String key = tagHelper.extractKey(trim, tagHelper.forPrefix());
                int endIndex = findEndfor(lines, i + 1, key);
                Object value = data == null ? null : data.get(key);
                if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    for (Object item : list) {
                        if (!(item instanceof Map)) {
                            throw TemplateRenderException.markdownRenderFailed(
                                    String.format(ErrorMessage.MD_LOOP_ITEM_NOT_MAP, key));
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemData = (Map<String, Object>) item;
                        Map<String, Object> scoped = new FallbackMap(itemData, data);
                        String block = joinLines(lines, i + 1, endIndex);
                        String expandedBlock = expandLoops(block, scoped, state);
                        output.add(replaceTags(expandedBlock, scoped, state, true));
                    }
                }
                i = endIndex;
            } else {
                output.add(lines[i]);
            }
        }
        return joinList(output);
    }

    private int findEndfor(String[] lines, int from, String key) {
        int depth = 0;
        for (int i = from; i < lines.length; i++) {
            String trim = lines[i].trim();
            if (isFor(trim)) {
                depth++;
                continue;
            }
            if (isEndfor(trim)) {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                String endKey = tagHelper.extractKey(trim, tagHelper.endforPrefix());
                if (!key.equals(endKey)) {
                    throw TemplateRenderException.markdownRenderFailed(
                            String.format(ErrorMessage.MD_LOOP_KEY_MISMATCH, key, endKey));
                }
                return i;
            }
        }
        throw TemplateRenderException.markdownRenderFailed(
                String.format(ErrorMessage.MD_LOOP_MISSING_ENDFOR, key));
    }

    private String replaceTags(String text, Map<String, Object> data, RenderState state, boolean internal) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean tableLine = !inFence && hasUnescapedPipe(line);
            String replaced = replaceImages(line, data, state, internal);
            replaced = replaceVariables(replaced, data, tableLine);
            result.append(replaced);
            if (i < lines.length - 1) {
                result.append(SimpleDocTemplateConstant.MARKDOWN_LINE_SEPARATOR);
            }
            if (isFenceLine(line)) {
                inFence = !inFence;
            }
        }
        return result.toString();
    }

    private String replaceVariables(String text, Map<String, Object> data, boolean tableLine) {
        String prefix = tagHelper.varPrefix();
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (pos < text.length()) {
            int start = text.indexOf(prefix, pos);
            if (start < 0) {
                sb.append(text.substring(pos));
                break;
            }
            int end = text.indexOf(']', start);
            if (end < 0) {
                sb.append(text.substring(pos));
                break;
            }
            sb.append(text.substring(pos, start));
            String key = text.substring(start + prefix.length(), end);
            Object value = data == null ? null : data.get(key);
            sb.append(escapeMarkdown(value == null ? SimpleDocTemplateConstant.EMPTY : String.valueOf(value), tableLine));
            pos = end + 1;
        }
        return sb.toString();
    }

    private String replaceImages(String text, Map<String, Object> data, RenderState state, boolean internal) {
        String prefix = tagHelper.imgPrefix();
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (pos < text.length()) {
            int start = text.indexOf(prefix, pos);
            if (start < 0) {
                sb.append(text.substring(pos));
                break;
            }
            int end = text.indexOf(']', start);
            if (end < 0) {
                sb.append(text.substring(pos));
                break;
            }
            sb.append(text.substring(pos, start));
            String key = text.substring(start + prefix.length(), end);
            Object value = data == null ? null : data.get(key);
            if (value == null) {
                sb.append(SimpleDocTemplateConstant.EMPTY);
            } else if (!(value instanceof Image)) {
                throw TemplateRenderException.markdownRenderFailed("图片变量必须是 Image: " + key);
            } else {
                Image image = (Image) value;
                String src;
                if (internal) {
                    src = String.format(SimpleDocTemplateConstant.MARKDOWN_IMAGE_TOKEN_TEMPLATE, state.imageReferences.size());
                    state.imageReferences.add(new MdImageReference(key, src, image));
                } else {
                    src = image.getSrc();
                }
                sb.append(String.format(SimpleDocTemplateConstant.MARKDOWN_IMAGE_TEMPLATE,
                        escapeImageDescription(image.getDescription()), escapeImageSrc(src)));
            }
            pos = end + 1;
        }
        return sb.toString();
    }

    private void detectChart(String text) {
        if (text != null && text.contains(tagHelper.chartPrefix())) {
            throw TemplateRenderException.markdownUnsupportedFeature("Markdown chart");
        }
    }

    private String escapeMarkdown(String value, boolean tableLine) {
        String escaped = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        escaped = escaped.replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("<", "\\<")
                .replace(">", "\\>")
                .replace("&", "\\&");
        if (tableLine) {
            escaped = escaped.replace("|", "\\|");
        }
        return escaped;
    }

    private String escapeImageDescription(String description) {
        return description == null ? SimpleDocTemplateConstant.EMPTY : description.replace("]", "\\]");
    }

    private String escapeImageSrc(String src) {
        return src == null ? SimpleDocTemplateConstant.EMPTY : src.replace(")", "\\)");
    }

    private boolean hasUnescapedPipe(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '|' && (i == 0 || line.charAt(i - 1) != '\\')) {
                return true;
            }
        }
        return false;
    }

    private boolean isFenceLine(String line) {
        String trim = line.trim();
        return trim.startsWith("```") || trim.startsWith("~~~");
    }

    private boolean isFor(String trim) {
        return trim.startsWith(tagHelper.forPrefix()) && trim.endsWith("]");
    }

    private boolean isEndfor(String trim) {
        return trim.startsWith(tagHelper.endforPrefix()) && trim.endsWith("]");
    }

    private String joinLines(String[] lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                sb.append(SimpleDocTemplateConstant.MARKDOWN_LINE_SEPARATOR);
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private String joinList(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append(SimpleDocTemplateConstant.MARKDOWN_LINE_SEPARATOR);
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private static class RenderState {
        private final List<MdImageReference> imageReferences = new ArrayList<>();

        RenderState copyWithoutReferences() {
            return new RenderState();
        }
    }

    private static class FallbackMap implements Map<String, Object> {
        private final Map<String, Object> primary;
        private final Map<String, Object> fallback;

        FallbackMap(Map<String, Object> primary, Map<String, Object> fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public int size() {
            return primary.size();
        }

        @Override
        public boolean isEmpty() {
            return primary.isEmpty() && (fallback == null || fallback.isEmpty());
        }

        @Override
        public boolean containsKey(Object key) {
            return primary.containsKey(key) || (fallback != null && fallback.containsKey(key));
        }

        @Override
        public boolean containsValue(Object value) {
            return primary.containsValue(value) || (fallback != null && fallback.containsValue(value));
        }

        @Override
        public Object get(Object key) {
            return primary.containsKey(key) ? primary.get(key) : (fallback == null ? null : fallback.get(key));
        }

        @Override
        public Object put(String key, Object value) {
            return primary.put(key, value);
        }

        @Override
        public Object remove(Object key) {
            return primary.remove(key);
        }

        @Override
        public void putAll(Map<? extends String, ?> m) {
            primary.putAll(m);
        }

        @Override
        public void clear() {
            primary.clear();
        }

        @Override
        public java.util.Set<String> keySet() {
            return primary.keySet();
        }

        @Override
        public java.util.Collection<Object> values() {
            return primary.values();
        }

        @Override
        public java.util.Set<Entry<String, Object>> entrySet() {
            return primary.entrySet();
        }
    }
}
