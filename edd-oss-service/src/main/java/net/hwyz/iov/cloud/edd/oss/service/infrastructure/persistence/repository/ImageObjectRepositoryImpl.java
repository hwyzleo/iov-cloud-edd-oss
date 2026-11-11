package net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.query.ImageObjectSearchCriteria;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.converter.ImageObjectPoConverter;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.mapper.ImageObjectMapper;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.po.ImageObjectPo;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 图片对象仓储实现（EDD-OSS-DSN-CR-001 §12）。
 * <p>
 * 条件更新同时校验状态与 row_version；不使用物理删除；排序固定 id DESC。
 *
 * @author hwyz_leo
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ImageObjectRepositoryImpl implements ImageObjectRepository {

    private static final String LIKE_ESCAPE = "\\";

    private final ImageObjectMapper imageObjectMapper;

    @Override
    public Optional<ImageObject> findByObjectId(String objectId) {
        ImageObjectPo po = imageObjectMapper.selectOne(new LambdaQueryWrapper<ImageObjectPo>()
                .eq(ImageObjectPo::getObjectId, objectId)
                .last("LIMIT 1"));
        return Optional.ofNullable(ImageObjectPoConverter.toDomain(po));
    }

    @Override
    public ImageObject insert(ImageObject imageObject) {
        ImageObjectPo po = ImageObjectPoConverter.fromDomain(imageObject);
        if (po.getCreateTime() == null) {
            po.setCreateTime(new Date());
        }
        if (po.getModifyTime() == null) {
            po.setModifyTime(new Date());
        }
        if (po.getRowVersion() == null) {
            po.setRowVersion(1);
        }
        if (po.getRowValid() == null) {
            po.setRowValid(Boolean.TRUE);
        }
        imageObjectMapper.insert(po);
        imageObject.assignId(po.getId());
        return imageObject;
    }

    @Override
    public boolean updatePendingToUploaded(ImageObject imageObject) {
        String modifier = modifierOf(imageObject);
        Timestamp now = imageObject.getModifyTime() == null ? new Timestamp(System.currentTimeMillis())
                : Timestamp.from(imageObject.getModifyTime());
        LambdaUpdateWrapper<ImageObjectPo> wrapper = new LambdaUpdateWrapper<ImageObjectPo>()
                .set(ImageObjectPo::getActualContentType,
                        ImageObjectPoConverter.toMimeValue(imageObject.getActualContentType()))
                .set(ImageObjectPo::getActualSize, imageObject.getActualSize())
                .set(ImageObjectPo::getEtag, imageObject.getEtag())
                .set(ImageObjectPo::getStatus, ImageStatus.UPLOADED.name())
                .set(ImageObjectPo::getUploadTime, now)
                .set(ImageObjectPo::getModifyBy, modifier)
                .set(ImageObjectPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageObjectPo::getObjectId, imageObject.getObjectId())
                .eq(ImageObjectPo::getStatus, ImageStatus.PENDING.name())
                .eq(ImageObjectPo::getRowVersion, imageObject.getRowVersion());
        int rows = imageObjectMapper.update(null, wrapper);
        if (rows > 0) {
            imageObject.increaseRowVersion(modifier,
                    imageObject.getModifyTime() == null ? Instant.now() : imageObject.getModifyTime());
        }
        return rows > 0;
    }

    @Override
    public boolean updateUploadedToDeleted(ImageObject imageObject) {
        Timestamp now = imageObject.getDeleteTime() == null ? new Timestamp(System.currentTimeMillis())
                : Timestamp.from(imageObject.getDeleteTime());
        LambdaUpdateWrapper<ImageObjectPo> wrapper = new LambdaUpdateWrapper<ImageObjectPo>()
                .set(ImageObjectPo::getStatus, ImageStatus.DELETED.name())
                .set(ImageObjectPo::getDeleteTime, now)
                .set(ImageObjectPo::getDeleteBy, imageObject.getDeleteBy())
                .set(ImageObjectPo::getModifyBy, imageObject.getDeleteBy())
                .set(ImageObjectPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageObjectPo::getObjectId, imageObject.getObjectId())
                .eq(ImageObjectPo::getStatus, ImageStatus.UPLOADED.name())
                .eq(ImageObjectPo::getRowVersion, imageObject.getRowVersion());
        int rows = imageObjectMapper.update(null, wrapper);
        if (rows > 0) {
            imageObject.increaseRowVersion(imageObject.getDeleteBy(),
                    imageObject.getDeleteTime() == null ? Instant.now() : imageObject.getDeleteTime());
        }
        return rows > 0;
    }

    @Override
    public boolean updatePendingToDeleted(ImageObject imageObject) {
        Timestamp now = imageObject.getDeleteTime() == null ? new Timestamp(System.currentTimeMillis())
                : Timestamp.from(imageObject.getDeleteTime());
        LambdaUpdateWrapper<ImageObjectPo> wrapper = new LambdaUpdateWrapper<ImageObjectPo>()
                .set(ImageObjectPo::getStatus, ImageStatus.DELETED.name())
                .set(ImageObjectPo::getDeleteTime, now)
                .set(ImageObjectPo::getDeleteBy, imageObject.getDeleteBy())
                .set(ImageObjectPo::getModifyBy, imageObject.getDeleteBy())
                .set(ImageObjectPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageObjectPo::getObjectId, imageObject.getObjectId())
                .eq(ImageObjectPo::getStatus, ImageStatus.PENDING.name())
                .eq(ImageObjectPo::getRowVersion, imageObject.getRowVersion());
        int rows = imageObjectMapper.update(null, wrapper);
        if (rows > 0) {
            imageObject.increaseRowVersion(imageObject.getDeleteBy(),
                    imageObject.getDeleteTime() == null ? Instant.now() : imageObject.getDeleteTime());
        }
        return rows > 0;
    }

    @Override
    public boolean updateUploadedAsset(ImageObject imageObject) {
        String modifier = modifierOf(imageObject);
        Timestamp now = imageObject.getModifyTime() == null ? new Timestamp(System.currentTimeMillis())
                : Timestamp.from(imageObject.getModifyTime());
        LambdaUpdateWrapper<ImageObjectPo> wrapper = new LambdaUpdateWrapper<ImageObjectPo>()
                .set(ImageObjectPo::getAssetName, imageObject.getAssetName())
                .set(ImageObjectPo::getDescription, imageObject.getDescription())
                .set(ImageObjectPo::getModifyBy, modifier)
                .set(ImageObjectPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageObjectPo::getObjectId, imageObject.getObjectId())
                .eq(ImageObjectPo::getStatus, ImageStatus.UPLOADED.name())
                .eq(ImageObjectPo::getRowVersion, imageObject.getRowVersion());
        int rows = imageObjectMapper.update(null, wrapper);
        if (rows > 0) {
            imageObject.increaseRowVersion(modifier,
                    imageObject.getModifyTime() == null ? Instant.now() : imageObject.getModifyTime());
        }
        return rows > 0;
    }

    @Override
    public boolean updateUploadedToReplaced(ImageObject imageObject) {
        String modifier = modifierOf(imageObject);
        Timestamp now = imageObject.getModifyTime() == null ? new Timestamp(System.currentTimeMillis())
                : Timestamp.from(imageObject.getModifyTime());
        Timestamp uploadTime = imageObject.getUploadTime() == null ? now : Timestamp.from(imageObject.getUploadTime());
        LambdaUpdateWrapper<ImageObjectPo> wrapper = new LambdaUpdateWrapper<ImageObjectPo>()
                .set(ImageObjectPo::getOriginalFilename, imageObject.getOriginalFilename())
                .set(ImageObjectPo::getDeclaredSize, imageObject.getDeclaredSize())
                .set(ImageObjectPo::getActualContentType,
                        ImageObjectPoConverter.toMimeValue(imageObject.getActualContentType()))
                .set(ImageObjectPo::getActualSize, imageObject.getActualSize())
                .set(ImageObjectPo::getEtag, imageObject.getEtag())
                .set(ImageObjectPo::getUploadTime, uploadTime)
                .set(ImageObjectPo::getModifyBy, modifier)
                .set(ImageObjectPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageObjectPo::getObjectId, imageObject.getObjectId())
                .eq(ImageObjectPo::getStatus, ImageStatus.UPLOADED.name())
                .eq(ImageObjectPo::getRowVersion, imageObject.getRowVersion());
        int rows = imageObjectMapper.update(null, wrapper);
        if (rows > 0) {
            imageObject.increaseRowVersion(modifier,
                    imageObject.getModifyTime() == null ? Instant.now() : imageObject.getModifyTime());
        }
        return rows > 0;
    }

    @Override
    public List<ImageObject> search(ImageObjectSearchCriteria criteria) {
        LambdaQueryWrapper<ImageObjectPo> wrapper = new LambdaQueryWrapper<>();
        if (criteria.getServiceName() != null && !criteria.getServiceName().isBlank()) {
            wrapper.eq(ImageObjectPo::getServiceName, criteria.getServiceName());
        }
        if (criteria.getStatus() != null) {
            wrapper.eq(ImageObjectPo::getStatus, criteria.getStatus().name());
        } else {
            wrapper.in(ImageObjectPo::getStatus, ImageStatus.PENDING.name(), ImageStatus.UPLOADED.name());
        }
        String contentType = criteria.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String normalizedContentType = contentType.trim().toLowerCase(java.util.Locale.ROOT);
            wrapper.and(condition -> condition.eq(ImageObjectPo::getActualContentType, normalizedContentType)
                    .or(nested -> nested.isNull(ImageObjectPo::getActualContentType)
                            .eq(ImageObjectPo::getDeclaredContentType, normalizedContentType)));
        }
        if (criteria.getObjectKey() != null && !criteria.getObjectKey().isBlank()) {
            wrapper.eq(ImageObjectPo::getObjectKey, criteria.getObjectKey());
        }
        if (criteria.getOriginalFilenamePrefix() != null && !criteria.getOriginalFilenamePrefix().isBlank()) {
            wrapper.likeRight(ImageObjectPo::getOriginalFilename,
                    escapeLike(criteria.getOriginalFilenamePrefix()));
        }
        if (criteria.getUploadBeginTime() != null) {
            wrapper.ge(ImageObjectPo::getUploadTime, Timestamp.from(criteria.getUploadBeginTime()));
        }
        if (criteria.getUploadEndTime() != null) {
            wrapper.le(ImageObjectPo::getUploadTime, Timestamp.from(criteria.getUploadEndTime()));
        }
        wrapper.orderByDesc(ImageObjectPo::getId);
        return ImageObjectPoConverter.toDomainList(imageObjectMapper.selectList(wrapper));
    }

    /**
     * 取修改人：领域方法已显式记录操作人时优先使用，否则回退为服务名。
     *
     * @param imageObject 图片对象
     * @return 修改人
     */
    private static String modifierOf(ImageObject imageObject) {
        String modifyBy = imageObject.getModifyBy();
        return modifyBy == null || modifyBy.isBlank() ? imageObject.getServiceName() : modifyBy;
    }

    /**
     * 转义 MySQL LIKE 通配符与转义符，避免前缀匹配被注入通配语义。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    static String escapeLike(String value) {
        return value.replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

}
