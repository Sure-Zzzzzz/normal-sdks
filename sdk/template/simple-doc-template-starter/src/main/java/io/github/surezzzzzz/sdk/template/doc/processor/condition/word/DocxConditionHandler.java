package io.github.surezzzzzz.sdk.template.doc.processor.condition.word;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.ConditionBlockException;
import io.github.surezzzzzz.sdk.template.doc.processor.condition.ConditionHandler;
import io.github.surezzzzzz.sdk.template.doc.support.BooleanHelper;
import io.github.surezzzzzz.sdk.template.doc.support.TagHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * DOCX Condition Handler
 *
 * <p>扫描 DOCX 的 OOXML DOM，找到 [suredt.start/end] 标记段落，
 * 根据条件值删除或保留标记之间的内容（段落、表格等）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
public class DocxConditionHandler implements ConditionHandler {

    @Autowired
    private TagHelper tagHelper;

    @Autowired
    private BooleanHelper booleanHelper;

    @Override
    public byte[] process(byte[] templateBytes, Map<String, Object> data) {
        try {
            return processInternal(templateBytes, data);
        } catch (ConditionBlockException e) {
            throw e;
        } catch (Exception e) {
            throw ConditionBlockException.processFailed(e.getMessage(), e);
        }
    }

    private byte[] processInternal(byte[] templateBytes, Map<String, Object> data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(templateBytes));
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (SimpleDocTemplateConstant.DOCX_DOCUMENT_XML.equals(entry.getName())) {
                    byte[] documentBytes = toByteArray(zis);
                    String documentXml = new String(documentBytes, SimpleDocTemplateConstant.CHARSET_UTF8);
                    String processedXml = processDocumentXml(documentXml, data);
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zos.write(processedXml.getBytes(SimpleDocTemplateConstant.CHARSET_UTF8));
                } else {
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    copy(zis, zos);
                }
                zos.closeEntry();
                zis.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private String processDocumentXml(String xml, Map<String, Object> data) {
        // 提取 <w:body>...</w:body> 内容段
        int bodyStart = xml.indexOf("<w:body>");
        int bodyEnd = xml.lastIndexOf("</w:body>");
        if (bodyStart == -1 || bodyEnd == -1) {
            return xml;
        }

        int contentStart = bodyStart + "<w:body>".length();
        String bodyContent = xml.substring(contentStart, bodyEnd);

        // 在原 XML 上定位条件块标记，收集需要删除的区间
        List<DeleteRange> deleteRanges = findDeleteRanges(bodyContent, data);

        if (deleteRanges.isEmpty()) {
            return xml;
        }

        // 按开始位置降序排列，从后往前删除，避免偏移
        deleteRanges.sort((a, b) -> Integer.compare(b.start, a.start));

        StringBuilder sb = new StringBuilder(bodyContent);
        for (DeleteRange range : deleteRanges) {
            sb.delete(range.start, range.end);
        }

        StringBuilder result = new StringBuilder();
        result.append(xml.substring(0, contentStart));
        result.append(sb);
        result.append(xml.substring(bodyEnd));
        return result.toString();
    }

    private List<DeleteRange> findDeleteRanges(String bodyContent, Map<String, Object> data) {
        // 匹配所有顶级 <w:p> 段落（不进入表格内部）
        // 顶级段落 = 在 <w:body> 直接子级的 <w:p>，即前面不是 <w:tc> 里的内容
        // 用正则匹配 <w:p> 开始到 </w:p> 结束，但排除表格单元格内的
        Pattern paraPattern = Pattern.compile("<w:p[\\s>][\\s\\S]*?</w:p>", Pattern.DOTALL);
        Matcher m = paraPattern.matcher(bodyContent);

        // 收集所有段落的位置和标记信息
        List<MarkInfo> marks = new ArrayList<>();
        while (m.find()) {
            String paraXml = m.group();
            String pureText = extractPureText(paraXml);
            if (tagHelper.matches(pureText, tagHelper.startPrefix())) {
                String key = tagHelper.extractKey(pureText, tagHelper.startPrefix());
                marks.add(new MarkInfo(m.start(), m.end(), true, key));
            } else if (tagHelper.matches(pureText, tagHelper.endPrefix())) {
                String key = tagHelper.extractKey(pureText, tagHelper.endPrefix());
                marks.add(new MarkInfo(m.start(), m.end(), false, key));
            }
        }

        List<DeleteRange> deleteRanges = new ArrayList<>();
        int i = 0;
        while (i < marks.size()) {
            MarkInfo mark = marks.get(i);
            if (mark.isStart) {
                String key = mark.key;
                // 找配对的 end
                int endIdx = -1;
                for (int j = i + 1; j < marks.size(); j++) {
                    if (!marks.get(j).isStart && key.equals(marks.get(j).key)) {
                        endIdx = j;
                        break;
                    }
                    if (marks.get(j).isStart) {
                        throw ConditionBlockException.nested(marks.get(j).key);
                    }
                }
                if (endIdx == -1) {
                    throw ConditionBlockException.mismatch(tagHelper.getPrefix(), key, "");
                }

                MarkInfo endMark = marks.get(endIdx);
                boolean conditionTrue = booleanHelper.isTrue(data.get(key));

                if (conditionTrue) {
                    // 只删除 start 和 end 标记段落
                    deleteRanges.add(new DeleteRange(mark.start, mark.end));
                    deleteRanges.add(new DeleteRange(endMark.start, endMark.end));
                } else {
                    // 删除 start 到 end 之间的所有内容（含标记段落）
                    deleteRanges.add(new DeleteRange(mark.start, endMark.end));
                }

                i = endIdx + 1;
            } else {
                // 孤立 end 标记，跳过
                i++;
            }
        }

        return deleteRanges;
    }

    private String extractPureText(String xml) {
        StringBuilder sb = new StringBuilder();
        Pattern runPattern = Pattern.compile("<w:t[^>]*>(.*?)</w:t>", Pattern.DOTALL);
        Matcher matcher = runPattern.matcher(xml);
        while (matcher.find()) {
            sb.append(matcher.group(1));
        }
        return sb.toString();
    }

    private static class MarkInfo {
        final int start;
        final int end;
        final boolean isStart;
        final String key;

        MarkInfo(int start, int end, boolean isStart, String key) {
            this.start = start;
            this.end = end;
            this.isStart = isStart;
            this.key = key;
        }
    }

    private static class DeleteRange {
        final int start;
        final int end;

        DeleteRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private byte[] toByteArray(java.io.InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[SimpleDocTemplateConstant.IO_BUFFER_SIZE];
        int len;
        while ((len = is.read(buf)) != -1) {
            bos.write(buf, 0, len);
        }
        return bos.toByteArray();
    }

    private void copy(java.io.InputStream is, java.io.OutputStream os) throws IOException {
        byte[] buf = new byte[SimpleDocTemplateConstant.IO_BUFFER_SIZE];
        int len;
        while ((len = is.read(buf)) != -1) {
            os.write(buf, 0, len);
        }
    }

    @Override
    public String supportedSuffix() {
        return SimpleDocTemplateConstant.SUFFIX_DOCX;
    }
}