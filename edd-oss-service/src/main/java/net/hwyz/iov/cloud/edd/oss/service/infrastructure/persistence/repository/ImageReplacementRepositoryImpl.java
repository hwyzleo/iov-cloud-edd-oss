package net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageReplacementStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageReplacementRepository;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.converter.ImageReplacementPoConverter;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.mapper.ImageReplacementMapper;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.po.ImageReplacementPo;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 图片替换任务仓储实现（EDD-OSS-DSN-CR-002 §5.3、§6.1.2、§12）。
 * <p>
 * 所有状态更新必须同时校验状态与 row_version；终态清空 active_object_id；不物理删除任务。
 *
 * @author hwyz_leo
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ImageReplacementRepositoryImpl implements ImageReplacementRepository {

    private final ImageReplacementMapper imageReplacementMapper;

    @Override
    public ImageReplacement insert(ImageReplacement replacement) {
        ImageReplacementPo po = ImageReplacementPoConverter.fromDomain(replacement);
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
        imageReplacementMapper.insert(po);
        replacement.assignId(po.getId());
        return replacement;
    }

    @Override
    public Optional<ImageReplacement> findByReplacementId(String replacementId) {
        ImageReplacementPo po = imageReplacementMapper.selectOne(new LambdaQueryWrapper<ImageReplacementPo>()
                .eq(ImageReplacementPo::getReplacementId, replacementId)
                .last("LIMIT 1"));
        return Optional.ofNullable(ImageReplacementPoConverter.toDomain(po));
    }

    @Override
    public Optional<ImageReplacement> findActiveByObjectId(String objectId) {
        List<ImageReplacementPo> poList = imageReplacementMapper.selectList(
                new LambdaQueryWrapper<ImageReplacementPo>()
                        .eq(ImageReplacementPo::getObjectId, objectId)
                        .in(ImageReplacementPo::getStatus, ImageReplacementStatus.PENDING.name(),
                                ImageReplacementStatus.PROMOTING.name())
                        .orderByDesc(ImageReplacementPo::getId)
                        .last("LIMIT 1"));
        if (poList.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ImageReplacementPoConverter.toDomain(poList.get(0)));
    }

    @Override
    public boolean updatePendingToPromoting(ImageReplacement replacement) {
        Timestamp now = nowOf(replacement);
        LambdaUpdateWrapper<ImageReplacementPo> wrapper = new LambdaUpdateWrapper<ImageReplacementPo>()
                .set(ImageReplacementPo::getActualContentType,
                        ImageReplacementPoConverter.toMimeValue(replacement.getActualContentType()))
                .set(ImageReplacementPo::getActualSize, replacement.getActualSize())
                .set(ImageReplacementPo::getEtag, replacement.getEtag())
                .set(ImageReplacementPo::getStatus, ImageReplacementStatus.PROMOTING.name())
                .set(ImageReplacementPo::getModifyBy, replacement.getModifyBy())
                .set(ImageReplacementPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageReplacementPo::getReplacementId, replacement.getReplacementId())
                .eq(ImageReplacementPo::getStatus, ImageReplacementStatus.PENDING.name())
                .eq(ImageReplacementPo::getRowVersion, replacement.getRowVersion());
        return apply(replacement, wrapper);
    }

    @Override
    public boolean updatePromotingToCompleted(ImageReplacement replacement) {
        Timestamp now = nowOf(replacement);
        LambdaUpdateWrapper<ImageReplacementPo> wrapper = new LambdaUpdateWrapper<ImageReplacementPo>()
                .set(ImageReplacementPo::getStatus, ImageReplacementStatus.COMPLETED.name())
                .set(ImageReplacementPo::getActiveObjectId, null)
                .set(ImageReplacementPo::getDescription, null)
                .set(ImageReplacementPo::getModifyBy, replacement.getModifyBy())
                .set(ImageReplacementPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageReplacementPo::getReplacementId, replacement.getReplacementId())
                .eq(ImageReplacementPo::getStatus, ImageReplacementStatus.PROMOTING.name())
                .eq(ImageReplacementPo::getRowVersion, replacement.getRowVersion());
        return apply(replacement, wrapper);
    }

    @Override
    public boolean updatePromotingToFailed(ImageReplacement replacement) {
        Timestamp now = nowOf(replacement);
        LambdaUpdateWrapper<ImageReplacementPo> wrapper = new LambdaUpdateWrapper<ImageReplacementPo>()
                .set(ImageReplacementPo::getStatus, ImageReplacementStatus.FAILED.name())
                .set(ImageReplacementPo::getActiveObjectId, null)
                .set(ImageReplacementPo::getDescription, replacement.getDescription())
                .set(ImageReplacementPo::getModifyBy, replacement.getModifyBy())
                .set(ImageReplacementPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageReplacementPo::getReplacementId, replacement.getReplacementId())
                .eq(ImageReplacementPo::getStatus, ImageReplacementStatus.PROMOTING.name())
                .eq(ImageReplacementPo::getRowVersion, replacement.getRowVersion());
        return apply(replacement, wrapper);
    }

    @Override
    public boolean updatePendingToCancelled(ImageReplacement replacement) {
        Timestamp now = nowOf(replacement);
        LambdaUpdateWrapper<ImageReplacementPo> wrapper = new LambdaUpdateWrapper<ImageReplacementPo>()
                .set(ImageReplacementPo::getStatus, ImageReplacementStatus.CANCELLED.name())
                .set(ImageReplacementPo::getActiveObjectId, null)
                .set(ImageReplacementPo::getDescription, replacement.getDescription())
                .set(ImageReplacementPo::getModifyBy, replacement.getModifyBy())
                .set(ImageReplacementPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageReplacementPo::getReplacementId, replacement.getReplacementId())
                .eq(ImageReplacementPo::getStatus, ImageReplacementStatus.PENDING.name())
                .eq(ImageReplacementPo::getRowVersion, replacement.getRowVersion());
        return apply(replacement, wrapper);
    }

    @Override
    public boolean updateFaultDescription(ImageReplacement replacement) {
        Timestamp now = nowOf(replacement);
        LambdaUpdateWrapper<ImageReplacementPo> wrapper = new LambdaUpdateWrapper<ImageReplacementPo>()
                .set(ImageReplacementPo::getDescription, replacement.getDescription())
                .set(ImageReplacementPo::getModifyBy, replacement.getModifyBy())
                .set(ImageReplacementPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(ImageReplacementPo::getReplacementId, replacement.getReplacementId())
                .eq(ImageReplacementPo::getStatus, ImageReplacementStatus.PROMOTING.name())
                .eq(ImageReplacementPo::getRowVersion, replacement.getRowVersion());
        return apply(replacement, wrapper);
    }

    private boolean apply(ImageReplacement replacement, LambdaUpdateWrapper<ImageReplacementPo> wrapper) {
        int rows = imageReplacementMapper.update(null, wrapper);
        if (rows > 0) {
            replacement.increaseRowVersion(replacement.getModifyBy(),
                    replacement.getModifyTime() == null ? Instant.now() : replacement.getModifyTime());
        }
        return rows > 0;
    }

    private Timestamp nowOf(ImageReplacement replacement) {
        return replacement.getModifyTime() == null ? new Timestamp(System.currentTimeMillis())
                : Timestamp.from(replacement.getModifyTime());
    }

}
