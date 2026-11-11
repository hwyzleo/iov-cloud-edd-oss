package net.hwyz.iov.cloud.edd.oss.service.domain.service;

import net.hwyz.iov.cloud.edd.oss.service.domain.exception.ImageValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 图片校验策略（EDD-OSS-DSN-CR-001 §5.2、§5.3）。
 * <p>
 * 仅校验元数据，不读取、不解码、不审核图片字节内容。
 *
 * @author hwyz_leo
 */
public final class ImageValidationPolicy {

    /**
     * 单张图片最大字节数（10 MiB）
     */
    public static final long MAX_SIZE_BYTES = 10485760L;
    /**
     * 原始文件名最大长度
     */
    public static final int MAX_FILENAME_LENGTH = 255;
    /**
     * 对象键前缀
     */
    public static final String OBJECT_KEY_PREFIX = "images/";
    /**
     * 替换临时对象键前缀（EDD-OSS-DSN-CR-002 §5.5）
     */
    public static final String REPLACEMENT_KEY_PREFIX = "images/edd-oss/replacements/";
    /**
     * 替换备份对象键前缀
     */
    public static final String REPLACEMENT_BACKUP_KEY_PREFIX = "images/edd-oss/replacement-backups/";

    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private ImageValidationPolicy() {
    }

    /**
     * 校验上传申请并解析声明 MIME。
     *
     * @param serviceName      调用方服务名
     * @param originalFilename 原始文件名
     * @param contentType      声明 MIME
     * @param declaredSize     声明大小
     * @return 解析后的 MIME
     */
    public static ImageMimeType validateGenerate(String serviceName, String originalFilename, String contentType,
                                                 long declaredSize) {
        return validateGenerate(serviceName, originalFilename, contentType, declaredSize, MAX_SIZE_BYTES);
    }

    /**
     * 校验上传申请并解析声明 MIME（可配置上限）。
     *
     * @param serviceName      调用方服务名
     * @param originalFilename 原始文件名
     * @param contentType      声明 MIME
     * @param declaredSize     声明大小
     * @param maxSizeBytes     单张图片最大字节数
     * @return 解析后的 MIME
     */
    public static ImageMimeType validateGenerate(String serviceName, String originalFilename, String contentType,
                                                 long declaredSize, long maxSizeBytes) {
        validateServiceName(serviceName);
        validateFilename(originalFilename);
        ImageMimeType mimeType = ImageMimeType.fromDeclared(contentType)
                .orElseThrow(() -> new ImageValidationException(ImageValidationException.Failure.UNSUPPORTED_MIME,
                        "unsupported content type"));
        String extension = extensionOf(originalFilename)
                .orElseThrow(() -> new ImageValidationException(ImageValidationException.Failure.UNSUPPORTED_MIME,
                        "file extension is missing"));
        if (!mimeType.supportsExtension(extension)) {
            throw new ImageValidationException(ImageValidationException.Failure.MIME_EXTENSION_MISMATCH,
                    "file extension does not match declared content type");
        }
        validateSize(declaredSize, maxSizeBytes);
        return mimeType;
    }

    /**
     * 校验 COS HEAD 返回的实际元数据。
     * <p>
     * 实际大小是完成确认后的权威值，不要求等于声明大小；实际 MIME 必须等于声明 MIME。
     *
     * @param declaredContentType 申请时声明的 MIME
     * @param actualContentType   实际 MIME
     * @param actualSize          实际大小
     * @return 校验通过的实际 MIME
     */
    public static ImageMimeType validateActual(ImageMimeType declaredContentType, String actualContentType,
                                               long actualSize) {
        return validateActual(declaredContentType, actualContentType, actualSize, MAX_SIZE_BYTES);
    }

    /**
     * 校验 COS HEAD 返回的实际元数据（可配置上限）。
     *
     * @param declaredContentType 申请时声明的 MIME
     * @param actualContentType   实际 MIME
     * @param actualSize          实际大小
     * @param maxSizeBytes        单张图片最大字节数
     * @return 校验通过的实际 MIME
     */
    public static ImageMimeType validateActual(ImageMimeType declaredContentType, String actualContentType,
                                               long actualSize, long maxSizeBytes) {
        if (actualSize <= 0 || actualSize > maxSizeBytes) {
            throw new ImageValidationException(ImageValidationException.Failure.ACTUAL_SIZE_INVALID,
                    "actual image size is out of range");
        }
        ImageMimeType actualMimeType = ImageMimeType.fromDeclared(actualContentType)
                .orElseThrow(() -> new ImageValidationException(ImageValidationException.Failure.ACTUAL_MIME_INVALID,
                        "actual content type is not supported"));
        if (!actualMimeType.equals(declaredContentType)) {
            throw new ImageValidationException(ImageValidationException.Failure.ACTUAL_MIME_INVALID,
                    "actual content type does not match declared content type");
        }
        return actualMimeType;
    }

    /**
     * 校验调用方服务名。
     *
     * @param serviceName 服务名
     */
    public static void validateServiceName(String serviceName) {
        if (serviceName == null || !SERVICE_NAME_PATTERN.matcher(serviceName).matches()) {
            throw new ImageValidationException(ImageValidationException.Failure.INVALID_SERVICE_NAME,
                    "invalid service name");
        }
    }

    /**
     * 生成对象键：images/{serviceName}/{uuid}.{ext}。
     *
     * @param serviceName 调用方服务名
     * @param objectId    对象标识（UUID）
     * @param mimeType    已验证的 MIME
     * @return 对象键
     */
    public static String buildObjectKey(String serviceName, String objectId, ImageMimeType mimeType) {
        validateServiceName(serviceName);
        return OBJECT_KEY_PREFIX + serviceName + "/" + objectId + "." + mimeType.getCanonicalExtension();
    }

    /**
     * 校验默认素材名称：去首尾空白后长度 1～100。
     *
     * @param assetName 素材名称
     * @return 规范化后的素材名称
     */
    public static String validateAssetName(String assetName) {
        if (assetName == null) {
            throw new ImageValidationException(ImageValidationException.Failure.INVALID_ASSET,
                    "asset name is required");
        }
        String normalized = assetName.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new ImageValidationException(ImageValidationException.Failure.INVALID_ASSET,
                    "asset name length must be between 1 and 100");
        }
        return normalized;
    }

    /**
     * 校验备注：去首尾空白后为空保存为 null，最大长度 255。
     *
     * @param description 备注
     * @return 规范化后的备注或 null
     */
    public static String validateDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 255) {
            throw new ImageValidationException(ImageValidationException.Failure.INVALID_ASSET,
                    "description length must not exceed 255");
        }
        return normalized;
    }

    /**
     * 校验替换图片申请：沿用图片约束，并要求新图片 MIME 与原图片实际 MIME 一致。
     *
     * @param originalMimeType 原图片实际 MIME
     * @param originalFilename 新图片原始文件名
     * @param contentType      新图片声明 MIME
     * @param declaredSize     新图片声明大小
     * @param maxSizeBytes     单张图片最大字节数
     * @return 解析后的 MIME
     */
    public static ImageMimeType validateReplacement(ImageMimeType originalMimeType, String originalFilename,
                                                   String contentType, long declaredSize, long maxSizeBytes) {
        ImageMimeType mimeType = validateGenerate(ImageObject.DEFAULT_ASSET_SERVICE_NAME, originalFilename,
                contentType, declaredSize, maxSizeBytes);
        if (originalMimeType == null || mimeType != originalMimeType) {
            throw new ImageValidationException(ImageValidationException.Failure.REPLACEMENT_MIME_MISMATCH,
                    "replacement content type must match the original image");
        }
        return mimeType;
    }

    /**
     * 生成替换临时对象键：images/edd-oss/replacements/{objectId}/{replacementId}.{ext}。
     *
     * @param objectId      目标图片对象标识
     * @param replacementId 替换任务标识
     * @param mimeType      已验证的 MIME
     * @return 临时对象键
     */
    public static String buildReplacementObjectKey(String objectId, String replacementId, ImageMimeType mimeType) {
        return REPLACEMENT_KEY_PREFIX + objectId + "/" + replacementId + "." + mimeType.getCanonicalExtension();
    }

    /**
     * 生成替换备份对象键：images/edd-oss/replacement-backups/{objectId}/{replacementId}.{ext}。
     *
     * @param objectId      目标图片对象标识
     * @param replacementId 替换任务标识
     * @param mimeType      已验证的 MIME
     * @return 备份对象键
     */
    public static String buildReplacementBackupKey(String objectId, String replacementId, ImageMimeType mimeType) {
        return REPLACEMENT_BACKUP_KEY_PREFIX + objectId + "/" + replacementId + "." + mimeType.getCanonicalExtension();
    }

    private static void validateFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()
                || originalFilename.length() > MAX_FILENAME_LENGTH) {
            throw new ImageValidationException(ImageValidationException.Failure.INVALID_FILENAME,
                    "invalid original filename");
        }
        for (int index = 0; index < originalFilename.length(); index++) {
            char current = originalFilename.charAt(index);
            if (current == '/' || current == '\\' || current < 0x20 || current == 0x7F) {
                throw new ImageValidationException(ImageValidationException.Failure.INVALID_FILENAME,
                        "invalid original filename");
            }
        }
    }

    private static void validateSize(long size, long maxSizeBytes) {
        if (size <= 0 || size > maxSizeBytes) {
            throw new ImageValidationException(ImageValidationException.Failure.INVALID_DECLARED_SIZE,
                    "declared image size is out of range");
        }
    }

    private static Optional<String> extensionOf(String originalFilename) {
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == originalFilename.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(originalFilename.substring(dotIndex + 1).toLowerCase(java.util.Locale.ROOT));
    }

}
