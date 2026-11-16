package net.hwyz.iov.cloud.edd.oss.service.domain.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 日志声明 Content-Type 白名单（EDD-OSS-DSN-CR-003 §4.3）。
 * <p>
 * 不复用图片 MIME/扩展名推断规则；日志不接受 JPEG/PNG/WebP。
 *
 * @author hwyz_leo
 */
@Getter
@AllArgsConstructor
public enum LogContentType {

    /**
     * gzip 压缩日志
     */
    GZIP("application/gzip"),
    /**
     * zip 压缩日志
     */
    ZIP("application/zip"),
    /**
     * zstd 压缩日志
     */
    ZSTD("application/zstd"),
    /**
     * tar 归档日志
     */
    TAR("application/x-tar"),
    /**
     * 未知二进制日志
     */
    OCTET_STREAM("application/octet-stream");

    private final String value;

    /**
     * 解析声明 Content-Type，忽略首尾空白与大小写。
     *
     * @param value 声明值
     * @return Content-Type
     */
    public static Optional<LogContentType> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.value.equals(normalized))
                .findFirst();
    }

}
