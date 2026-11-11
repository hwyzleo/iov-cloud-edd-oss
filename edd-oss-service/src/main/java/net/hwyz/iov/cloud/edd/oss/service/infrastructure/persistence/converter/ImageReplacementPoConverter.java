package net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.converter;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageReplacementStatus;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.po.ImageReplacementPo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

/**
 * 图片替换任务 PO ↔ Domain 转换器（EDD-OSS-DSN-CR-002 §4.1）。
 *
 * @author hwyz_leo
 */
public final class ImageReplacementPoConverter {

    private ImageReplacementPoConverter() {
    }

    /**
     * PO 转领域对象。
     *
     * @param po 持久化对象
     * @return 领域对象；入参为 null 时返回 null
     */
    public static ImageReplacement toDomain(ImageReplacementPo po) {
        if (po == null) {
            return null;
        }
        return ImageReplacement.restore(po.getId(), po.getReplacementId(), po.getObjectId(), po.getActiveObjectId(),
                po.getStagingObjectKey(), po.getBackupObjectKey(), po.getOriginalFilename(),
                toMimeType(po.getDeclaredContentType()), po.getDeclaredSize() == null ? 0L : po.getDeclaredSize(),
                toMimeType(po.getActualContentType()), po.getActualSize(), po.getEtag(),
                ImageReplacementStatus.valueOf(po.getStatus()), toInstant(po.getExpireTime()), po.getDescription(),
                po.getRowVersion(), po.getCreateBy(), toInstant(po.getCreateTime()), po.getModifyBy(),
                toInstant(po.getModifyTime()));
    }

    /**
     * 领域对象转 PO。
     *
     * @param replacement 领域对象
     * @return 持久化对象
     */
    public static ImageReplacementPo fromDomain(ImageReplacement replacement) {
        ImageReplacementPo po = ImageReplacementPo.builder()
                .id(replacement.getId())
                .replacementId(replacement.getReplacementId())
                .objectId(replacement.getObjectId())
                .activeObjectId(replacement.getActiveObjectId())
                .stagingObjectKey(replacement.getStagingObjectKey())
                .backupObjectKey(replacement.getBackupObjectKey())
                .originalFilename(replacement.getOriginalFilename())
                .declaredContentType(replacement.getDeclaredContentType().getValue())
                .declaredSize(replacement.getDeclaredSize())
                .actualContentType(replacement.getActualContentType() == null ? null
                        : replacement.getActualContentType().getValue())
                .actualSize(replacement.getActualSize())
                .etag(replacement.getEtag())
                .status(replacement.getStatus().name())
                .expireTime(toDate(replacement.getExpireTime()))
                .build();
        po.setDescription(replacement.getDescription());
        po.setCreateBy(replacement.getCreateBy());
        po.setCreateTime(toDate(replacement.getCreateTime()));
        po.setModifyBy(replacement.getModifyBy());
        po.setModifyTime(toDate(replacement.getModifyTime()));
        po.setRowVersion(replacement.getRowVersion());
        po.setRowValid(Boolean.TRUE);
        return po;
    }

    /**
     * 将 MIME 实体值转字符串，供条件更新使用。
     *
     * @param mimeType MIME
     * @return 字符串值
     */
    public static String toMimeValue(ImageMimeType mimeType) {
        return mimeType == null ? null : mimeType.getValue();
    }

    private static ImageMimeType toMimeType(String value) {
        if (value == null) {
            return null;
        }
        return ImageMimeType.fromDeclared(value.toLowerCase(Locale.ROOT)).orElse(null);
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private static Date toDate(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

}
