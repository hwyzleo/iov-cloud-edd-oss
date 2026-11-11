package net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate;

import lombok.Getter;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageReplacementStatus;

import java.time.Instant;

/**
 * 图片替换任务聚合根（EDD-OSS-DSN-CR-002 §5.5）。
 * <p>
 * 原图片记录始终保持 UPLOADED；替换过程通过本聚合独立持久化并由 {@code active_object_id}
 * 保证同一图片最多一个活动任务。
 *
 * @author hwyz_leo
 */
@Getter
public class ImageReplacement {

    /**
     * 数据库内部主键
     */
    private Long id;
    /**
     * 替换任务标识（UUID）
     */
    private final String replacementId;
    /**
     * 目标图片对象标识
     */
    private final String objectId;
    /**
     * 活动任务约束：活动期间等于 objectId，终态为 null
     */
    private String activeObjectId;
    /**
     * 新图片临时对象键
     */
    private final String stagingObjectKey;
    /**
     * 原图片备份对象键
     */
    private final String backupObjectKey;
    /**
     * 新图片原始文件名
     */
    private final String originalFilename;
    /**
     * 新图片声明 MIME
     */
    private final ImageMimeType declaredContentType;
    /**
     * 新图片声明大小
     */
    private final long declaredSize;
    /**
     * 临时对象实际 MIME
     */
    private ImageMimeType actualContentType;
    /**
     * 临时对象实际大小
     */
    private Long actualSize;
    /**
     * 临时对象 ETag
     */
    private String etag;
    /**
     * 当前状态
     */
    private ImageReplacementStatus status;
    /**
     * 临时上传凭证过期时间
     */
    private final Instant expireTime;
    /**
     * 故障或任务说明
     */
    private String description;
    /**
     * 乐观锁版本
     */
    private Integer rowVersion;
    /**
     * 创建人
     */
    private final String createBy;
    /**
     * 创建时间
     */
    private final Instant createTime;
    /**
     * 修改人
     */
    private String modifyBy;
    /**
     * 修改时间
     */
    private Instant modifyTime;

    private ImageReplacement(Long id, String replacementId, String objectId, String activeObjectId,
                             String stagingObjectKey, String backupObjectKey, String originalFilename,
                             ImageMimeType declaredContentType, long declaredSize, ImageMimeType actualContentType,
                             Long actualSize, String etag, ImageReplacementStatus status, Instant expireTime,
                             String description, Integer rowVersion, String createBy, Instant createTime,
                             String modifyBy, Instant modifyTime) {
        this.id = id;
        this.replacementId = replacementId;
        this.objectId = objectId;
        this.activeObjectId = activeObjectId;
        this.stagingObjectKey = stagingObjectKey;
        this.backupObjectKey = backupObjectKey;
        this.originalFilename = originalFilename;
        this.declaredContentType = declaredContentType;
        this.declaredSize = declaredSize;
        this.actualContentType = actualContentType;
        this.actualSize = actualSize;
        this.etag = etag;
        this.status = status;
        this.expireTime = expireTime;
        this.description = description;
        this.rowVersion = rowVersion;
        this.createBy = createBy;
        this.createTime = createTime;
        this.modifyBy = modifyBy;
        this.modifyTime = modifyTime;
    }

    /**
     * 创建 PENDING 替换任务，并以 {@code activeObjectId} 占用该图片的活动任务位。
     *
     * @return 新建的替换任务
     */
    public static ImageReplacement pending(String replacementId, String objectId, String stagingObjectKey,
                                           String backupObjectKey, String originalFilename,
                                           ImageMimeType declaredContentType, long declaredSize, Instant expireTime,
                                           String operator, Instant now) {
        return new ImageReplacement(null, replacementId, objectId, objectId, stagingObjectKey, backupObjectKey,
                originalFilename, declaredContentType, declaredSize, null, null, null,
                ImageReplacementStatus.PENDING, expireTime, null, 1, operator, now, operator, now);
    }

    /**
     * 从持久化状态还原聚合根（仅基础设施转换器使用）。
     *
     * @return 还原的替换任务
     */
    public static ImageReplacement restore(Long id, String replacementId, String objectId, String activeObjectId,
                                           String stagingObjectKey, String backupObjectKey, String originalFilename,
                                           ImageMimeType declaredContentType, long declaredSize,
                                           ImageMimeType actualContentType, Long actualSize, String etag,
                                           ImageReplacementStatus status, Instant expireTime, String description,
                                           Integer rowVersion, String createBy, Instant createTime, String modifyBy,
                                           Instant modifyTime) {
        return new ImageReplacement(id, replacementId, objectId, activeObjectId, stagingObjectKey, backupObjectKey,
                originalFilename, declaredContentType, declaredSize, actualContentType, actualSize, etag, status,
                expireTime, description, rowVersion, createBy, createTime, modifyBy, modifyTime);
    }

    /**
     * 临时对象核验通过：PENDING → PROMOTING，写入实际元数据。
     *
     * @param actualContentType 实际 MIME（已校验）
     * @param actualSize        实际大小（已校验）
     * @param etag              临时对象 ETag
     * @param operator          操作人
     * @param now               修改时间
     */
    public void startPromoting(ImageMimeType actualContentType, long actualSize, String etag, String operator,
                               Instant now) {
        if (status != ImageReplacementStatus.PENDING) {
            throw new IllegalStateException("replacement " + replacementId + " is not PENDING");
        }
        this.actualContentType = actualContentType;
        this.actualSize = actualSize;
        this.etag = etag;
        this.status = ImageReplacementStatus.PROMOTING;
        this.modifyBy = operator;
        this.modifyTime = now;
    }

    /**
     * 图片记录与任务均已提交：PROMOTING → COMPLETED，释放活动任务位。
     *
     * @param operator 操作人
     * @param now      完成时间
     */
    public void markCompleted(String operator, Instant now) {
        if (status != ImageReplacementStatus.PROMOTING) {
            throw new IllegalStateException("replacement " + replacementId + " is not PROMOTING");
        }
        this.status = ImageReplacementStatus.COMPLETED;
        this.activeObjectId = null;
        this.description = null;
        this.modifyBy = operator;
        this.modifyTime = now;
    }

    /**
     * 提升失败且旧对象已恢复确认：PROMOTING → FAILED，释放活动任务位。
     *
     * @param reason   故障说明
     * @param operator 操作人
     * @param now      修改时间
     */
    public void markFailed(String reason, String operator, Instant now) {
        if (status != ImageReplacementStatus.PROMOTING) {
            throw new IllegalStateException("replacement " + replacementId + " is not PROMOTING");
        }
        this.status = ImageReplacementStatus.FAILED;
        this.activeObjectId = null;
        this.description = reason;
        this.modifyBy = operator;
        this.modifyTime = now;
    }

    /**
     * 删除流程取消：PENDING → CANCELLED，释放活动任务位。
     *
     * @param operator 操作人
     * @param now      修改时间
     */
    public void cancel(String operator, Instant now) {
        if (status != ImageReplacementStatus.PENDING) {
            throw new IllegalStateException("replacement " + replacementId + " is not PENDING");
        }
        this.status = ImageReplacementStatus.CANCELLED;
        this.activeObjectId = null;
        this.description = "cancelled by image deletion";
        this.modifyBy = operator;
        this.modifyTime = now;
    }

    /**
     * 记录故障说明但仍保留 PROMOTING 与活动标识（恢复未确认时使用）。
     *
     * @param reason   故障说明
     * @param operator 操作人
     * @param now      修改时间
     */
    public void recordFault(String reason, String operator, Instant now) {
        if (status != ImageReplacementStatus.PROMOTING) {
            throw new IllegalStateException("replacement " + replacementId + " is not PROMOTING");
        }
        this.description = reason;
        this.modifyBy = operator;
        this.modifyTime = now;
    }

    /**
     * 条件更新成功后递增乐观锁版本。
     *
     * @param modifier 修改人
     * @param now      修改时间
     */
    public void increaseRowVersion(String modifier, Instant now) {
        this.rowVersion = (rowVersion == null ? 1 : rowVersion) + 1;
        this.modifyBy = modifier;
        this.modifyTime = now;
    }

    /**
     * 记录数据库主键（插入成功后回填）。
     *
     * @param id 主键
     */
    public void assignId(Long id) {
        this.id = id;
    }

    /**
     * 是否已过临时上传有效期（仅 PENDING 生效）。
     *
     * @param now 当前时间
     * @return 是否过期
     */
    public boolean isExpired(Instant now) {
        return status == ImageReplacementStatus.PENDING && !now.isBefore(expireTime);
    }

    public boolean isPending() {
        return status == ImageReplacementStatus.PENDING;
    }

    public boolean isPromoting() {
        return status == ImageReplacementStatus.PROMOTING;
    }

    public boolean isCompleted() {
        return status == ImageReplacementStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == ImageReplacementStatus.FAILED;
    }

    public boolean isCancelled() {
        return status == ImageReplacementStatus.CANCELLED;
    }

}
