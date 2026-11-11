package net.hwyz.iov.cloud.edd.oss.service.domain.service;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;

/**
 * 图片替换策略（EDD-OSS-DSN-CR-002 §5.3～§5.6）。
 * <p>
 * 只表达领域规则；并发冲突与错误码映射由应用层完成。
 *
 * @author hwyz_leo
 */
public final class ImageReplacementPolicy {

    private ImageReplacementPolicy() {
    }

    /**
     * 目标图片是否可创建替换任务：必须为 UPLOADED 默认素材。
     *
     * @param imageObject 目标图片
     * @return 是否可替换
     */
    public static boolean isReplaceable(ImageObject imageObject) {
        return imageObject != null && imageObject.isUploaded() && imageObject.isDefaultAsset();
    }

    /**
     * 目标图片是否可编辑素材信息：必须为 UPLOADED 默认素材。
     *
     * @param imageObject 目标图片
     * @return 是否可编辑
     */
    public static boolean isAssetEditable(ImageObject imageObject) {
        return isReplaceable(imageObject);
    }

    /**
     * 校验临时对象实际元数据：沿用图片实际元数据校验，并要求与替换声明一致。
     *
     * @param replacement     替换任务
     * @param actualMimeType  临时对象实际 MIME
     * @param actualSize      临时对象实际大小
     * @param maxSizeBytes    单张图片最大字节数
     * @return 校验通过的实际 MIME
     */
    public static ImageMimeType validateStagedMetadata(ImageReplacement replacement, String actualMimeType,
                                                       long actualSize, long maxSizeBytes) {
        return ImageValidationPolicy.validateActual(replacement.getDeclaredContentType(), actualMimeType,
                actualSize, maxSizeBytes);
    }

}
