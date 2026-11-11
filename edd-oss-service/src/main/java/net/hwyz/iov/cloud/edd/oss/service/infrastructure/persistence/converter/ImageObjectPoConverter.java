package net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.converter;

import com.github.pagehelper.Page;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ObjectAccessPermission;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.po.ImageObjectPo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 图片对象 PO ↔ Domain 转换器（EDD-OSS-DSN-CR-001 §4）。
 * <p>
 * 时间契约：Domain 使用 {@link Instant}，PO 使用 {@link Date}。
 *
 * @author hwyz_leo
 */
public final class ImageObjectPoConverter {

    private ImageObjectPoConverter() {
    }

    /**
     * PO 转领域对象。
     *
     * @param po 持久化对象
     * @return 领域对象；入参为 null 时返回 null
     */
    public static ImageObject toDomain(ImageObjectPo po) {
        if (po == null) {
            return null;
        }
        return ImageObject.restore(po.getId(), po.getObjectId(),
                toProvider(po.getProvider()), po.getRegion(), po.getBucketName(), po.getObjectKey(),
                po.getServiceName(), po.getOriginalFilename(), toMimeType(po.getDeclaredContentType()),
                po.getDeclaredSize() == null ? 0L : po.getDeclaredSize(), toMimeType(po.getActualContentType()),
                po.getActualSize(), po.getEtag(),
                po.getPermission() == null ? null : ObjectAccessPermission.valueOf(po.getPermission()),
                ImageStatus.valueOf(po.getStatus()), toInstant(po.getExpireTime()), toInstant(po.getUploadTime()),
                toInstant(po.getDeleteTime()), po.getDeleteBy(), po.getRowVersion(), po.getCreateBy(),
                toInstant(po.getCreateTime()), po.getModifyBy(), toInstant(po.getModifyTime()));
    }

    /**
     * PO 列表转领域对象列表，并保留平台分页元数据。
     *
     * @param poList 持久化对象列表
     * @return 领域对象列表
     */
    public static List<ImageObject> toDomainList(List<ImageObjectPo> poList) {
        List<ImageObject> domainList = new ArrayList<>(poList.size());
        for (ImageObjectPo po : poList) {
            domainList.add(toDomain(po));
        }
        if (poList instanceof Page<?> sourcePage) {
            Page<ImageObject> targetPage = new Page<>(sourcePage.getPageNum(), sourcePage.getPageSize(),
                    sourcePage.isCount());
            targetPage.setTotal(sourcePage.getTotal());
            targetPage.addAll(domainList);
            return targetPage;
        }
        return domainList;
    }

    /**
     * 领域对象转 PO。
     *
     * @param imageObject 领域对象
     * @return 持久化对象
     */
    public static ImageObjectPo fromDomain(ImageObject imageObject) {
        ImageObjectPo po = ImageObjectPo.builder()
                .id(imageObject.getId())
                .objectId(imageObject.getObjectId())
                .provider(imageObject.getProvider().name())
                .region(imageObject.getRegion())
                .bucketName(imageObject.getBucketName())
                .objectKey(imageObject.getObjectKey())
                .serviceName(imageObject.getServiceName())
                .originalFilename(imageObject.getOriginalFilename())
                .declaredContentType(imageObject.getDeclaredContentType().getValue())
                .declaredSize(imageObject.getDeclaredSize())
                .actualContentType(imageObject.getActualContentType() == null ? null
                        : imageObject.getActualContentType().getValue())
                .actualSize(imageObject.getActualSize())
                .etag(imageObject.getEtag())
                .permission(imageObject.getPermission() == null ? null : imageObject.getPermission().name())
                .status(imageObject.getStatus().name())
                .expireTime(toDate(imageObject.getExpireTime()))
                .uploadTime(toDate(imageObject.getUploadTime()))
                .deleteTime(toDate(imageObject.getDeleteTime()))
                .deleteBy(imageObject.getDeleteBy())
                .build();
        po.setCreateBy(imageObject.getCreateBy());
        po.setCreateTime(toDate(imageObject.getCreateTime()));
        po.setModifyBy(imageObject.getModifyBy());
        po.setModifyTime(toDate(imageObject.getModifyTime()));
        po.setRowVersion(imageObject.getRowVersion());
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

    private static StorageProvider toProvider(String provider) {
        return provider == null ? null : StorageProvider.valueOf(provider);
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
        if (instant == null) {
            return null;
        }
        return Timestamp.from(instant);
    }

}
