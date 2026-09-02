package io.github.surezzzzzz.sdk.auth.captcha.spi;

import io.github.surezzzzzz.sdk.auth.captcha.annotation.SimpleCaptchaComponent;
import io.github.surezzzzzz.sdk.auth.captcha.configuration.SimpleCaptchaProperties;
import io.github.surezzzzzz.sdk.auth.captcha.constant.SimpleCaptchaConstant;
import io.github.surezzzzzz.sdk.auth.captcha.exception.SimpleCaptchaException;
import io.github.surezzzzzz.sdk.auth.captcha.model.CaptchaChallenge;
import io.github.surezzzzzz.sdk.auth.captcha.storage.ChallengeStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Image Captcha Provider（默认实现）
 *
 * <p>Java2D 自绘图片验证码（零第三方依赖）：随机字符 + 干扰线 + 噪点 + 逐字符旋转，
 * 输出 PNG data URI；挑战答案经 {@link ChallengeStore} 暂存（强制 Redis 共享存储，
 * TTL 可配），校验原子取出即删（一次性消费）。
 *
 * <p>默认实现即本模块唯一内置实现，组件装配；自定义实现（滑块 / 云验证码）
 * 的接入方不引本模块运行时（compileOnly 取 spi 接口即可），不同实现不同引用，
 * 不存在让位场景。
 *
 * <p>部署前提：字符渲染依赖 JRE 字体栈，常规 JDK 镜像与 debian-slim 满足；
 * alpine/distroless 类瘦身容器需自带 fontconfig + 基本字体。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleCaptchaComponent
@RequiredArgsConstructor
public class ImageCaptchaProvider implements CaptchaProvider {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 字符旋转最大角度（度）
     */
    private static final double MAX_ROTATE_DEGREE = 25.0;

    /**
     * RGB 色值上界
     */
    private static final int RGB_BOUND = 256;

    /**
     * 深色系字符色上界（保证与浅底对比度）
     */
    private static final int DARK_COLOR_BOUND = 160;

    /**
     * 噪点边长（像素）
     */
    private static final int NOISE_POINT_SIZE = 1;

    /**
     * 干扰线粗细
     */
    private static final float INTERFERENCE_LINE_WIDTH = 1.5f;

    private final ChallengeStore challengeStore;
    private final SimpleCaptchaProperties properties;

    @Override
    public CaptchaChallenge generate() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String answer = randomAnswer(properties.getImageCharLength());
        String content = String.format(SimpleCaptchaConstant.TEMPLATE_DATA_URI, drawPngBase64(answer));
        challengeStore.save(buildChallengeKey(captchaId), answer.toLowerCase(),
                Duration.ofSeconds(properties.getChallengeTtlSeconds()));
        log.debug("验证码挑战已生成：captchaId={}, charLength={}", captchaId, answer.length());
        return new CaptchaChallenge(captchaId, SimpleCaptchaConstant.CHALLENGE_TYPE_IMAGE, content);
    }

    @Override
    public boolean verify(String captchaId, String answer) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(answer)) {
            return false;
        }
        String stored = challengeStore.consume(buildChallengeKey(captchaId));
        boolean passed = stored != null && stored.equalsIgnoreCase(answer.trim());
        log.debug("验证码校验：captchaId={}, passed={}", captchaId, passed);
        return passed;
    }

    /**
     * 构建挑战 Redis key
     *
     * <p>格式：sure-auth-captcha:challenge:{me}::{captchaId}（me 段 HashTag 包裹，支持 Cluster）
     *
     * @param captchaId 挑战 id
     * @return Redis key
     */
    private String buildChallengeKey(String captchaId) {
        return SimpleCaptchaConstant.KEY_PREFIX
                + SimpleCaptchaConstant.SEPARATOR_COLON + SimpleCaptchaConstant.BUSINESS_CHALLENGE
                + SimpleCaptchaConstant.SEPARATOR_COLON + SimpleCaptchaConstant.BRACE_PREFIX
                + properties.getRedis().getMe() + SimpleCaptchaConstant.BRACE_SUFFIX
                + SimpleCaptchaConstant.SEPARATOR_DOUBLE_COLON + captchaId;
    }

    /**
     * 从字符池随机生成答案（统一小写存储，校验忽略大小写）
     *
     * @param length 字符个数
     * @return 随机答案
     */
    private String randomAnswer(int length) {
        String pool = SimpleCaptchaConstant.IMAGE_CHAR_POOL;
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(pool.charAt(SECURE_RANDOM.nextInt(pool.length())));
        }
        return builder.toString();
    }

    /**
     * 绘制验证码图片并输出 PNG base64
     *
     * @param answer 验证码答案
     * @return base64 编码的 PNG 内容
     */
    private String drawPngBase64(String answer) {
        int width = SimpleCaptchaConstant.IMAGE_PADDING * 2
                + answer.length() * SimpleCaptchaConstant.IMAGE_WIDTH_PER_CHAR;
        int height = SimpleCaptchaConstant.IMAGE_HEIGHT;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            drawInterferenceLine(graphics, width, height);
            drawNoisePoint(graphics, width, height);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, height
                    - SimpleCaptchaConstant.IMAGE_PADDING));
            for (int i = 0; i < answer.length(); i++) {
                drawRotatedChar(graphics, answer.charAt(i), i, height);
            }
        } finally {
            graphics.dispose();
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, SimpleCaptchaConstant.IMAGE_FORMAT_PNG, output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            // 内存流写 PNG 不产生 IO 故障，此处防御 ImageIO 编码器缺失的异常面
            throw new SimpleCaptchaException(SimpleCaptchaConstant.ERROR_CODE_IMAGE_ENCODE_FAILED,
                    "验证码图片编码失败", exception);
        }
    }

    /**
     * 绘制单个旋转字符（随机颜色 / 角度 / 纵向偏移）
     */
    private void drawRotatedChar(Graphics2D graphics, char value, int index, int height) {
        int x = SimpleCaptchaConstant.IMAGE_PADDING
                + index * SimpleCaptchaConstant.IMAGE_WIDTH_PER_CHAR;
        int y = height - SimpleCaptchaConstant.IMAGE_PADDING
                - SECURE_RANDOM.nextInt(SimpleCaptchaConstant.IMAGE_PADDING);
        double theta = Math.toRadians((SECURE_RANDOM.nextDouble() * 2 - 1) * MAX_ROTATE_DEGREE);
        graphics.setColor(new Color(SECURE_RANDOM.nextInt(DARK_COLOR_BOUND),
                SECURE_RANDOM.nextInt(DARK_COLOR_BOUND), SECURE_RANDOM.nextInt(DARK_COLOR_BOUND)));
        graphics.rotate(theta, x, y);
        graphics.drawString(String.valueOf(value), x, y);
        graphics.rotate(-theta, x, y);
    }

    /**
     * 绘制干扰线
     */
    private void drawInterferenceLine(Graphics2D graphics, int width, int height) {
        graphics.setStroke(new BasicStroke(INTERFERENCE_LINE_WIDTH));
        for (int i = 0; i < SimpleCaptchaConstant.INTERFERENCE_LINE_COUNT; i++) {
            graphics.setColor(new Color(SECURE_RANDOM.nextInt(RGB_BOUND),
                    SECURE_RANDOM.nextInt(RGB_BOUND), SECURE_RANDOM.nextInt(RGB_BOUND)));
            graphics.drawLine(SECURE_RANDOM.nextInt(width), SECURE_RANDOM.nextInt(height),
                    SECURE_RANDOM.nextInt(width), SECURE_RANDOM.nextInt(height));
        }
    }

    /**
     * 绘制噪点
     */
    private void drawNoisePoint(Graphics2D graphics, int width, int height) {
        for (int i = 0; i < SimpleCaptchaConstant.NOISE_POINT_COUNT; i++) {
            graphics.setColor(new Color(SECURE_RANDOM.nextInt(RGB_BOUND),
                    SECURE_RANDOM.nextInt(RGB_BOUND), SECURE_RANDOM.nextInt(RGB_BOUND)));
            graphics.fillRect(SECURE_RANDOM.nextInt(width), SECURE_RANDOM.nextInt(height),
                    NOISE_POINT_SIZE, NOISE_POINT_SIZE);
        }
    }
}
