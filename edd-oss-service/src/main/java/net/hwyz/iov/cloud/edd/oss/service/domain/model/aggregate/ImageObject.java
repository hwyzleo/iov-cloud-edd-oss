package net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate;

import lombok.Getter;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ObjectAccessPermission;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;

import java.time.Instant;
import java.util.Objects;

/**
 * 图片对象聚合根（EDD-OSS-DSN-CR-001 §5.1）。
 * <p>
 * 状态流转只能通过领域方法完成：PENDING → UPLOADED → DELETED；不允许逆向或跳转。
 *
 * @author hwyz_leo
 */
@Getter
public class ImageObject {

    /**
     * 数据库内部主键
     */
    private Long id;
    /**
     * 外部上传任务与对象标识（UUID）
     */
    private final String objectId;
    /**
     * 存储提供方
     */
    private final StorageProvider provider;
    /**
     * COS 地域
     */
    private final String region;
    /**
     * COS Bucket
     */
    private final String bucketName;
    /**
     * OSS 生成的唯一对象键
     */
    private final String objectKey;
    /**
     * 经认证匹配的业务服务名
     */
    private final String serviceName;
    /**
     * 原始文件名（仅展示，不进入对象键）
     */
    private final String originalFilename;
    /**
     * 申请时声明的 MIME
     */
    private final ImageMimeType declaredContentType;
    /**
     * 申请时声明的大小
     */
    private final long declaredSize;
    /**
     * HEAD 获取的实际 MIME
     */
    private ImageMimeType actualContentType;
    /**
     * HEAD 获取的实际大小
     */
    private Long actualSize;
    /**
     * COS ETag
     */
    private String etag;
    /**
     * 对象访问权限
     */
    private final ObjectAccessPermission permission;
    /**
     * 当前状态
     */
    private ImageStatus status;
    /**
     * 上传凭证过期时间
     */
    private final Instant expireTime;
    /**
     * 完成确认时间
     */
    private Instant uploadTime;
    /**
     * 删除完成时间
     */
    private Instant deleteTime;
    /**
     * 删除操作人员
     */
    private String deleteBy;
    /**
     * 乐观锁版本
     */
    private Integer rowVersion;
    /**
     * 创建方
     */
    private final String createBy;
    /**
     * 创建时间（申请时间）
     */
    private final Instant createTime;
    /**
     * 最后修改方
     */
    private String modifyBy;
    /**
     * 最后修改时间
     */
    private Instant modifyTime;

    private ImageObject(Long id, String objectId, StorageProvider provider, String region, String bucketName,
                        String objectKey, String serviceName, String originalFilename, ImageMimeType declaredContentType,
                        long declaredSize, ImageMimeType actualContentType, Long actualSize, String etag,
                        ObjectAccessPermission permission, ImageStatus status, Instant expireTime, Instant uploadTime,
                        Instant deleteTime, String deleteBy, Integer rowVersion, String createBy, Instant createTime,
                        String modifyBy, Instant modifyTime) {
        this.id = id;
        this.objectId = objectId;
        this.provider = provider;
        this.region = region;
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.serviceName = serviceName;
        this.originalFilename = originalFilename;
        this.declaredContentType = declaredContentType;
        this.declaredSize = declaredSize;
        this.actualContentType = actualContentType;
        this.actualSize = actualSize;
        this.etag = etag;
        this.permission = permission;
        this.status = status;
        this.expireTime = expireTime;
        this.uploadTime = uploadTime;
        this.deleteTime = deleteTime;
        this.deleteBy = deleteBy;
        this.rowVersion = rowVersion;
        this.createBy = createBy;
        this.createTime = createTime;
        this.modifyBy = modifyBy;
        this.modifyTime = modifyTime;
    }

    /**
     * 创建 PENDING 上传任务。
     *
     * @return 新建的图片对象
     */
    public static ImageObject pending(String objectId, StorageProvider provider, String region, String bucketName,
                                      String objectKey, String serviceName, String originalFilename,
                                      ImageMimeType declaredContentType, long declaredSize,
                                      ObjectAccessPermission permission, Instant expireTime, String createBy,
                                      Instant now) {
        return new ImageObject(null, objectId, provider, region, bucketName, objectKey, serviceName,
                originalFilename, declaredContentType, declaredSize, null, null, null, permission,
                ImageStatus.PENDING, expireTime, null, null, null, 1, createBy, now, createBy, now);
    }

    /**
     * 从持久化状态还原聚合根（仅基础设施转换器使用，不用于业务创建）。
     *
     * @return 还原的图片对象
     */
    public static ImageObject restore(Long id, String objectId, StorageProvider provider, String region,
                                      String bucketName, String objectKey, String serviceName, String originalFilename,
                                      ImageMimeType declaredContentType, long declaredSize,
                                      ImageMimeType actualContentType, Long actualSize, String etag,
                                      ObjectAccessPermission permission, ImageStatus status, Instant expireTime,
                                      Instant uploadTime, Instant deleteTime, String deleteBy, Integer rowVersion,
                                      String createBy, Instant createTime, String modifyBy, Instant modifyTime) {
        return new ImageObject(id, objectId, provider, region, bucketName, objectKey, serviceName, originalFilename,
                declaredContentType, declaredSize, actualContentType, actualSize, etag, permission, status,
                expireTime, uploadTime, deleteTime, deleteBy, rowVersion, createBy, createTime, modifyBy, modifyTime);
    }

    /**
     * 完成确认：PENDING → UPLOADED，同时写入实际元数据。
     *
     * @param actualContentType 实际 MIME（已校验）
     * @param actualSize        实际大小（已校验）
     * @param etag              COS ETag
     * @param now               确认时间
     */
    public void markUploaded(ImageMimeType actualContentType, long actualSize, String etag, Instant now) {
        if (status != ImageStatus.PENDING) {
            throw new IllegalStateException("image object " + objectId + " is not PENDING");
        }
        this.actualContentType = actualContentType;
        this.actualSize = actualSize;
        this.etag = etag;
        this.status = ImageStatus.UPLOADED;
        this.uploadTime = now;
        this.modifyTime = now;
    }

    /**
     * 后台删除：UPLOADED → DELETED。
     *
     * @param operator 操作人员
     * @param now      删除时间
     */
    public void markDeleted(String operator, Instant now) {
        if (status != ImageStatus.UPLOADED) {
            throw new IllegalStateException("image object " + objectId + " is not UPLOADED");
        }
        this.status = ImageStatus.DELETED;
        this.deleteBy = operator;
        this.deleteTime = now;
        this.modifyTime = now;
    }

    /**
     * 条件更新成功后递增乐观锁版本。
     *
     * @param modifier 修改方
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
     * 是否已过上传有效期。
     *
     * @param now 当前时间
     * @return 是否过期
     */
    public boolean isExpired(Instant now) {
        return status == ImageStatus.PENDING && !now.isBefore(expireTime);
    }

    /**
     * 是否归属于指定调用方服务。
     *
     * @param callerServiceName 调用方服务名
     * @return 是否归属
     */
    public boolean belongsTo(String callerServiceName) {
        return Objects.equals(serviceName, callerServiceName);
    }

    /**
     * 是否已确认上传。
     *
     * @return 是否 UPLOADED
     */
    public boolean isUploaded() {
        return status == ImageStatus.UPLOADED;
    }

    /**
     * 是否已删除。
     *
     * @return 是否 DELETED
     */
    public boolean isDeleted() {
        return status == ImageStatus.DELETED;
    }

    /**
     * 是否等待上传。
     *
     * @return 是否 PENDING
     */
    public boolean isPending() {
        return status == ImageStatus.PENDING;
    }

}
