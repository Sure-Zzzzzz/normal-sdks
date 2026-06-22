package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.template.doc.exception.EmbeddedFontParseException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DOCX 嵌入字体（ODTTF）提取与解混淆
 * <p>
 * Word 把嵌入字体保存在 word/fonts/font*.odttf，文件经过 obfuscation：
 * 前 32 字节用 fontKey GUID 的字节序列 XOR。本类负责：
 * <ul>
 *   <li>读取 word/fontTable.xml + word/_rels/fontTable.xml.rels，建立 [w:name → odttf 文件路径 → fontKey] 映射</li>
 *   <li>从 zip 中读出 odttf 字节，解 XOR 得到原始 TTF 字节</li>
 *   <li>返回 {@link ExtractedFont} 列表，调用方负责注册到 PDF 渲染器</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
public class EmbeddedFontExtractor {

    private static final String FONT_TABLE_XML = "word/fontTable.xml";
    private static final String FONT_TABLE_RELS = "word/_rels/fontTable.xml.rels";
    private static final String FONT_DIR_PREFIX = "word/fonts/";

    /**
     * 单个字体的不同变体 attribute（Regular/Bold/Italic/BoldItalic 都要解出来）
     */
    private static final String[] EMBED_TAGS = {
            "embedRegular", "embedBold", "embedItalic", "embedBoldItalic"
    };

    /**
     * fontTable.xml 中匹配 <w:font w:name="...">...<w:embedXxx r:id="..." w:fontKey="{GUID}"/>...</w:font>
     * 一个 <w:font> 里可能含多个 embedXxx，分别处理。
     */
    private static final Pattern FONT_BLOCK_PATTERN = Pattern.compile(
            "<w:font\\s+w:name=\"([^\"]+)\"[^>]*>(.*?)</w:font>",
            Pattern.DOTALL);

    /**
     * 匹配 rels 文件中 <Relationship Id="..." Target="fonts/fontN.odttf"/>
     */
    private static final Pattern REL_PATTERN = Pattern.compile(
            "<Relationship\\s+[^>]*Id=\"([^\"]+)\"[^>]*Target=\"([^\"]+)\"[^>]*/>");

    /**
     * 从 docx 字节中提取所有嵌入字体（解混淆完成）
     *
     * @param docxBytes DOCX 字节
     * @return 解混淆后的字体列表，按 w:name 索引
     */
    public List<ExtractedFont> extract(byte[] docxBytes) {
        List<ExtractedFont> result = new ArrayList<>();
        try {
            // 1. 一次扫描收集 fontTable.xml + rels + odttf 字节
            String fontTableXml = null;
            String relsXml = null;
            Map<String, byte[]> odttfBytes = new HashMap<>();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (FONT_TABLE_XML.equals(name)) {
                        fontTableXml = readEntryString(zis);
                    } else if (FONT_TABLE_RELS.equals(name)) {
                        relsXml = readEntryString(zis);
                    } else if (name.startsWith(FONT_DIR_PREFIX)) {
                        odttfBytes.put(name, readEntryBytes(zis));
                    }
                    zis.closeEntry();
                }
            }
            if (fontTableXml == null || relsXml == null || odttfBytes.isEmpty()) {
                log.debug("DOCX 无嵌入字体: fontTable={} rels={} odttfCount={}",
                        fontTableXml != null, relsXml != null, odttfBytes.size());
                return result;
            }
            log.info("发现嵌入字体: fontTable.xml 字节={}, rels 字节={}, odttf 文件数={}",
                    fontTableXml.length(), relsXml.length(), odttfBytes.size());

            // 2. 解析 rels：rId → fonts/fontN.odttf 完整路径
            Map<String, String> relIdToPath = new HashMap<>();
            Matcher relMatcher = REL_PATTERN.matcher(relsXml);
            while (relMatcher.find()) {
                String rId = relMatcher.group(1);
                String target = relMatcher.group(2);
                // target 是 "fonts/fontN.odttf"，相对 word/，拼成 word/fonts/fontN.odttf
                String fullPath = target.startsWith("/") ? target.substring(1) : "word/" + target;
                relIdToPath.put(rId, fullPath);
                log.debug("rels 映射: {} → {}", rId, fullPath);
            }
            log.debug("rels 解析完成: 条目数={}", relIdToPath.size());

            // 3. 解析 fontTable.xml：每个 <w:font> 可能含多个 embedXxx
            Matcher fontMatcher = FONT_BLOCK_PATTERN.matcher(fontTableXml);
            int fontBlockCount = 0;
            int embedHitCount = 0;
            while (fontMatcher.find()) {
                fontBlockCount++;
                String fontName = fontMatcher.group(1);
                String inner = fontMatcher.group(2);
                log.debug("匹配 <w:font>: name={}, inner长度={}", fontName, inner.length());
                for (String tag : EMBED_TAGS) {
                    Pattern embedPattern = Pattern.compile(
                            "<w:" + tag + "\\s+r:id=\"([^\"]+)\"\\s+w:fontKey=\"\\{([^}]+)\\}\"\\s*/>");
                    Matcher m = embedPattern.matcher(inner);
                    if (!m.find()) continue;
                    embedHitCount++;
                    String rId = m.group(1);
                    String guid = m.group(2);
                    String odttfPath = relIdToPath.get(rId);
                    log.debug("命中 embed: font={}, tag={}, rId={}, guid={}, odttfPath={}",
                            fontName, tag, rId, guid, odttfPath);
                    if (odttfPath == null) {
                        log.warn("embed 引用的 rId={} 在 rels 中未找到", rId);
                        continue;
                    }
                    byte[] obfuscated = odttfBytes.get(odttfPath);
                    if (obfuscated == null) {
                        log.warn("embed 引用的 odttf 路径={} 在 zip 中未找到，可用路径={}",
                                odttfPath, odttfBytes.keySet());
                        continue;
                    }
                    try {
                        byte[] ttf = deobfuscate(obfuscated, guid);
                        result.add(new ExtractedFont(fontName, tag, ttf));
                        log.info("提取嵌入字体: {} ({}) ← {}", fontName, tag, odttfPath);
                    } catch (Exception e) {
                        log.warn("解混淆字体失败: {} ({}) - {}", fontName, odttfPath, e.getMessage());
                    }
                }
            }
            log.info("fontTable 解析统计: <w:font> 块={}, embed 命中={}, 提取成功={}",
                    fontBlockCount, embedHitCount, result.size());
        } catch (IOException e) {
            log.warn("读取 DOCX 嵌入字体失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * ODTTF 解混淆：前 32 字节用 fontKey GUID 字节循环 XOR
     * <p>
     * GUID 字符串 "AC81D250-4DA3-43BC-8F82-7B4D540D1D85" → 16 字节，
     * 但 Word 的字节序按 GUID structure（前三段小端、后两段大端），
     * 实际作为 mask 时按 ECMA-376 规范以「逆序」16 字节循环 XOR 前 32 字节。
     */
    private byte[] deobfuscate(byte[] obfuscated, String guid) {
        byte[] keyBytes = parseGuidToMaskBytes(guid);
        byte[] result = obfuscated.clone();
        // 前 32 字节循环 XOR（每 16 字节一轮，共 2 轮）
        int len = Math.min(32, result.length);
        for (int i = 0; i < len; i++) {
            result[i] = (byte) (result[i] ^ keyBytes[i % 16]);
        }
        return result;
    }

    /**
     * 将 GUID 字符串解析为 ECMA-376 规定的 mask 字节序列（逆序 16 字节）。
     * <p>
     * GUID "AC81D250-4DA3-43BC-8F82-7B4D540D1D85" 的字节为
     * D2 50 81 AC A3 4D BC 43 8F 82 7B 4D 54 0D 1D 85（标准 GUID 字节序）；
     * Word ODTTF 解混淆要求按这 16 字节的**逆序**循环 XOR：
     * 85 1D 0D 54 4D 7B 82 8F 43 BC 4D A3 AC 81 50 D2
     */
    private byte[] parseGuidToMaskBytes(String guid) {
        // 去掉 "-"，得到 32 个 hex 字符
        String hex = guid.replace("-", "");
        if (hex.length() != 32) {
            throw EmbeddedFontParseException.failed(String.format(ErrorMessage.EMBEDDED_FONT_GUID_INVALID, guid));
        }
        // 标准 GUID 字节序：第一段（4 字节）小端，第二段（2 字节）小端，
        // 第三段（2 字节）小端，第四段（2 字节）大端，第五段（6 字节）大端
        byte[] guidBytes = new byte[16];
        // data1: hex[0..8] 小端
        guidBytes[0] = parseHex(hex, 6);
        guidBytes[1] = parseHex(hex, 4);
        guidBytes[2] = parseHex(hex, 2);
        guidBytes[3] = parseHex(hex, 0);
        // data2: hex[8..12] 小端
        guidBytes[4] = parseHex(hex, 10);
        guidBytes[5] = parseHex(hex, 8);
        // data3: hex[12..16] 小端
        guidBytes[6] = parseHex(hex, 14);
        guidBytes[7] = parseHex(hex, 12);
        // data4: hex[16..20] 大端
        guidBytes[8] = parseHex(hex, 16);
        guidBytes[9] = parseHex(hex, 18);
        // data5: hex[20..32] 大端
        for (int i = 0; i < 6; i++) {
            guidBytes[10 + i] = parseHex(hex, 20 + i * 2);
        }
        // 逆序作为 mask
        byte[] mask = new byte[16];
        for (int i = 0; i < 16; i++) {
            mask[i] = guidBytes[15 - i];
        }
        return mask;
    }

    private byte parseHex(String hex, int offset) {
        return (byte) Integer.parseInt(hex.substring(offset, offset + 2), 16);
    }

    private String readEntryString(InputStream is) throws IOException {
        return new String(readEntryBytes(is), StandardCharsets.UTF_8);
    }

    private byte[] readEntryBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    /**
     * 已解混淆的嵌入字体
     */
    @Getter
    @AllArgsConstructor
    public static class ExtractedFont {
        /**
         * fontTable.xml 中 w:font@w:name，DOCX run 引用此名字
         */
        private final String name;
        /**
         * 字体变体：embedRegular / embedBold / embedItalic / embedBoldItalic
         */
        private final String variant;
        /**
         * 解混淆后的 TTF 字节
         */
        private final byte[] ttfBytes;
    }
}
