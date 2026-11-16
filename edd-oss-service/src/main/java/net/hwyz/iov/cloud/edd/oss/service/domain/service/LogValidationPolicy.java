package net.hwyz.iov.cloud.edd.oss.service.domain.service;

import net.hwyz.iov.cloud.edd.oss.service.domain.exception.LogValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogContentType;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 日志参数与格式策略（EDD-OSS-DSN-CR-003 §4.3、§4.4）。
 * <p>
 * 只校验元数据，不读取日志正文；对象键只允许服务端生成。
 *
 * @author hwyz_leo
 */
public final class LogValidationPolicy {

    /**
     * 对象键前缀
     */
    public static final String OBJECT_KEY_PREFIX = "logs/";
    /**
     * 调用方服务名格式
     */
    public static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    /**
     * SHA-256 十六进制格式
     */
    public static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    /**
     * businessId/businessSubId/requestId 最大长度
     */
    public static final int MAX_BUSINESS_ID_LENGTH = 128;
    /**
     * 原始文件名最大长度
     */
    public static final int MAX_FILENAME_LENGTH = 255;
    /**
     * 隐私分级最大长度
     */
    public static final int MAX_PRIVACY_LEVEL_LENGTH = 32;

    private static final DateTimeFormatter UTC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);

    private LogValidationPolicy() {
    }

    /**
     * 校验并规范化调用方服务名。
     *
     * @param callerService 调用方服务名
     * @return 规范化后的服务名
     */
    public static String validateCallerService(String callerService) {
        if (callerService == null) {
            throw new LogValidationException(LogValidationException.Failure.INVALID_CALLER, "caller service is missing");
        }
        String normalized = callerService.strip();
        if (!SERVICE_NAME_PATTERN.matcher(normalized).matches()) {
            throw new LogValidationException(LogValidationException.Failure.INVALID_CALLER,
                    "invalid caller service name");
        }
        return normalized;
    }

    /**
     * 校验业务类型，本期仅接受 OTA_UPGRADE_LOG。
     *
     * @param businessType 业务类型
     * @return 业务类型
     */
    public static LogBusinessType validateBusinessType(String businessType) {
        return LogBusinessType.fromValue(businessType)
                .orElseThrow(() -> new LogValidationException(LogValidationException.Failure.INVALID_BUSINESS_TYPE,
                        "unsupported business type"));
    }

    /**
     * 校验必填业务标识：去首尾空白后 1～128 字符且无控制字符。
     *
     * @param field 字段名
     * @param value 字段值
     * @return 规范化后的值
     */
    public static String validateBusinessId(String field, String value) {
        return validateToken(field, value, MAX_BUSINESS_ID_LENGTH, LogValidationException.Failure.INVALID_TOKEN);
    }

    /**
     * 校验选填业务子标识：为空返回 null，否则按业务标识规则校验。
     *
     * @param field 字段名
     * @param value 字段值
     * @return 规范化后的值或 null
     */
    public static String validateOptionalBusinessSubId(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return validateBusinessId(field, value);
    }

    /**
     * 校验隐私分级：去首尾空白后 1～32 字符且无控制字符。
     *
     * @param privacyLevel 隐私分级
     * @return 规范化后的值
     */
    public static String validatePrivacyLevel(String privacyLevel) {
        return validateToken("privacyLevel", privacyLevel, MAX_PRIVACY_LEVEL_LENGTH,
                LogValidationException.Failure.INVALID_PRIVACY_LEVEL);
    }

    /**
     * 校验原始文件名：1～255 字符，禁止路径分隔符、NUL 与控制字符。
     *
     * @param fileName 原始文件名
     * @return 规范化后的文件名
     */
    public static String validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.length() > MAX_FILENAME_LENGTH) {
            throw new LogValidationException(LogValidationException.Failure.INVALID_FILENAME,
                    "invalid log file name");
        }
        for (int index = 0; index < fileName.length(); index++) {
            char current = fileName.charAt(index);
            if (current == '/' || current == '\\' || current < 0x20 || current == 0x7F) {
                throw new LogValidationException(LogValidationException.Failure.INVALID_FILENAME,
                        "invalid log file name");
            }
        }
        return fileName;
    }

    /**
     * 解析声明的日志 Content-Type。
     *
     * @param contentType 声明值
     * @return Content-Type
     */
    public static LogContentType validateContentType(String contentType) {
        return LogContentType.fromValue(contentType)
                .orElseThrow(() -> new LogValidationException(
                        LogValidationException.Failure.UNSUPPORTED_CONTENT_TYPE,
                        "unsupported log content type"));
    }

    /**
     * 校验声明大小：大于 0 且不超过配置上限。
     *
     * @param sizeBytes    声明字节数
     * @param maxSizeBytes 配置上限
     * @return 声明字节数
     */
    public static long validateDeclaredSize(Long sizeBytes, long maxSizeBytes) {
        if (sizeBytes == null || sizeBytes <= 0 || sizeBytes > maxSizeBytes) {
            throw new LogValidationException(LogValidationException.Failure.INVALID_SIZE,
                    "declared log size is out of range");
        }
        return sizeBytes;
    }

    /**
     * 校验 SHA-256：64 位小写十六进制。
     *
     * @param sha256Hex 摘要
     * @return 规范化后的摘要
     */
    public static String validateSha256(String sha256Hex) {
        if (sha256Hex == null) {
            throw new LogValidationException(LogValidationException.Failure.INVALID_SHA256, "sha256 is required");
        }
        String normalized = sha256Hex.trim().toLowerCase(Locale.ROOT);
        if (!SHA256_PATTERN.matcher(normalized).matches()) {
            throw new LogValidationException(LogValidationException.Failure.INVALID_SHA256,
                    "sha256 must be 64 lowercase hexadecimal characters");
        }
        return normalized;
    }

    /**
     * 解析上传授权 TTL：缺省使用默认值，否则必须大于 0 且不超过配置上限。
     *
     * @param ttlSeconds         调用方请求 TTL
     * @param defaultTtlSeconds  默认 TTL
     * @param maxTtlSeconds      最大 TTL
     * @return 生效 TTL 秒数
     */
    public static int resolveTtlSeconds(Integer ttlSeconds, long defaultTtlSeconds, long maxTtlSeconds) {
        long effective = ttlSeconds == null ? defaultTtlSeconds : ttlSeconds;
        if (effective <= 0 || effective > maxTtlSeconds) {
            throw new LogValidationException(LogValidationException.Failure.INVALID_TTL, "invalid ttlSeconds");
        }
        return (int) effective;
    }

    /**
     * 生成对象键：logs/{callerService}/{businessType}/{yyyyMMdd}/{objectId}。
     *
     * @param callerService 调用方服务名
     * @param businessType  业务类型
     * @param authorizedAt  授权时间
     * @param objectId      对象标识
     * @return 对象键
     */
    public static String buildObjectKey(String callerService, LogBusinessType businessType, Instant authorizedAt,
                                        String objectId) {
        validateCallerService(callerService);
        if (businessType == null || objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("businessType and objectId are required to build log object key");
        }
        return OBJECT_KEY_PREFIX + callerService + "/" + businessType.name() + "/"
                + UTC_DATE_FORMATTER.format(authorizedAt) + "/" + objectId;
    }

    private static String validateToken(String field, String value, int maxLength,
                                        LogValidationException.Failure failure) {
        if (value == null) {
            throw new LogValidationException(failure, field + " is required");
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new LogValidationException(failure, field + " length must be between 1 and " + maxLength);
        }
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current < 0x20 || current == 0x7F) {
                throw new LogValidationException(failure, field + " must not contain control characters");
            }
        }
        return normalized;
    }

}
