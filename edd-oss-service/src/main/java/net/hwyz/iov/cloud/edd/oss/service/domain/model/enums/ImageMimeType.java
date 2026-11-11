package net.hwyz.iov.cloud.edd.oss.service.domain.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 允许的图片 MIME 类型（EDD-OSS-DSN-CR-001 §5.2）。
 *
 * @author hwyz_leo
 */
@Getter
public enum ImageMimeType {

    /**
     * JPEG
     */
    IMAGE_JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg")),
    /**
     * PNG
     */
    IMAGE_PNG("image/png", "png", Set.of("png")),
    /**
     * WebP
     */
    IMAGE_WEBP("image/webp", "webp", Set.of("webp"));

    private final String value;
    private final String canonicalExtension;
    private final Set<String> supportedExtensions;

    ImageMimeType(String value, String canonicalExtension, Set<String> supportedExtensions) {
        this.value = value;
        this.canonicalExtension = canonicalExtension;
        this.supportedExtensions = supportedExtensions;
    }

    /**
     * 按声明 MIME 精确匹配（去首尾空格并转小写后比较）。
     *
     * @param raw 声明 MIME
     * @return 匹配结果
     */
    public static Optional<ImageMimeType> fromDeclared(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(item -> item.value.equals(normalized)).findFirst();
    }

    /**
     * 判断扩展名（不含点，忽略大小写）是否属于当前 MIME。
     *
     * @param extension 扩展名
     * @return 是否支持
     */
    public boolean supportsExtension(String extension) {
        return extension != null && supportedExtensions.contains(extension.toLowerCase(Locale.ROOT));
    }

}
