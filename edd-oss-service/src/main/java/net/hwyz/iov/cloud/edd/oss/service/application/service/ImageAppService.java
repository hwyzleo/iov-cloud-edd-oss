package net.hwyz.iov.cloud.edd.oss.service.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.query.ImageObjectQuery;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectStorageGateway;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.domain.exception.ImageValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ObjectAccessPermission;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.query.ImageObjectSearchCriteria;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageReplacementRepository;
import net.hwyz.iov.cloud.edd.oss.service.domain.service.ImageValidationPolicy;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.CosProperties;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.ImageProperties;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos.CosObjectMetadata;
import net.hwyz.iov.cloud.framework.web.util.PageUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 图片应用服务（EDD-OSS-DSN-CR-001 §9）。
 *
 * @author hwyz_leo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAppService {

    private static final String HTTP_METHOD_PUT = "PUT";

    private final ImageObjectRepository imageObjectRepository;
    private final ImageReplacementRepository imageReplacementRepository;
    private final ObjectStorageGateway objectStorageGateway;
    private final CosProperties cosProperties;
    private final ImageProperties imageProperties;

    /**
     * 申请上传：参数校验 → 生成对象键 → 签名 → 插入 PENDING（§9.1）。
     *
     * @param cmd               申请命令
     * @param callerServiceName 认证后的调用方服务名
     * @return 上传参数
     */
    public ImageUploadResult generate(GenerateImageUploadCmd cmd, String callerServiceName) {
        validateCallerServiceName(callerServiceName);
        ImageMimeType declaredMimeType;
        try {
            declaredMimeType = ImageValidationPolicy.validateGenerate(callerServiceName, cmd.getOriginalFilename(),
                    cmd.getContentType(), cmd.getSize(), imageProperties.getMaxSizeBytes());
        } catch (ImageValidationException exception) {
            throw mapValidationFailure(exception);
        }
        Instant now = Instant.now();
        Instant expireTime = now.plusSeconds(imageProperties.getUploadExpirationSeconds());
        ImageObject imageObject = null;
        String uploadUrl = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String objectId = UUID.randomUUID().toString();
            String objectKey = ImageValidationPolicy.buildObjectKey(callerServiceName, objectId, declaredMimeType);
            uploadUrl = objectStorageGateway.generateUploadUrl(cosProperties.getBucket(), objectKey,
                    declaredMimeType.getValue(), expireTime);
            imageObject = ImageObject.pending(objectId, StorageProvider.TENCENT_COS, cosProperties.getRegion(),
                    cosProperties.getBucket(), objectKey, callerServiceName, cmd.getOriginalFilename(),
                    declaredMimeType, cmd.getSize(), ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE, expireTime,
                    callerServiceName, now);
            try {
                imageObjectRepository.insert(imageObject);
                log.info("创建图片上传任务 objectId[{}] serviceName[{}]", objectId, callerServiceName);
                break;
            } catch (DuplicateKeyException duplicateKeyException) {
                imageObject = null;
                log.warn("图片上传任务唯一键冲突，重试生成 objectId[{}] serviceName[{}]", objectId, callerServiceName);
            }
        }
        if (imageObject == null) {
            throw new OssException(OssErrorCode.INTERNAL_ERROR, "failed to create image upload task");
        }
        return ImageUploadResult.builder()
                .objectId(imageObject.getObjectId())
                .objectKey(imageObject.getObjectKey())
                .uploadUrl(uploadUrl)
                .httpMethod(HTTP_METHOD_PUT)
                .contentType(declaredMimeType.getValue())
                .expiresAt(imageObject.getExpireTime())
                .build();
    }

    /**
     * 完成确认：校验归属、HEAD 核验、条件更新（§9.2）。
     *
     * @param cmd 完成命令
     * @return 已确认的图片对象
     */
    public ImageObjectResult complete(CompleteImageUploadCmd cmd) {
        validateCallerServiceName(cmd.getCallerServiceName());
        Instant now = Instant.now();
        ImageObject imageObject = loadOwnedObject(cmd.getObjectId(), cmd.getCallerServiceName());
        if (imageObject.isDeleted()) {
            throw new OssException(OssErrorCode.INVALID_STATE);
        }
        if (imageObject.isUploaded()) {
            return toResult(imageObject);
        }
        if (imageObject.isExpired(now)) {
            throw new OssException(OssErrorCode.UPLOAD_EXPIRED);
        }
        CosObjectMetadata metadata = objectStorageGateway.headObject(imageObject.getBucketName(),
                imageObject.getObjectKey());
        if (metadata == null) {
            log.warn("图片对象尚未上传 objectId[{}] bucket[{}] key[{}]", imageObject.getObjectId(),
                    imageObject.getBucketName(), imageObject.getObjectKey());
            throw new OssException(OssErrorCode.IMAGE_NOT_UPLOADED);
        }
        ImageMimeType actualMimeType;
        try {
            actualMimeType = ImageValidationPolicy.validateActual(imageObject.getDeclaredContentType(),
                    metadata.getContentType(), metadata.getContentLength(), imageProperties.getMaxSizeBytes());
        } catch (ImageValidationException exception) {
            deleteInvalidObject(imageObject);
            throw mapValidationFailure(exception);
        }
        String modifier = cmd.getOperator() == null || cmd.getOperator().isBlank()
                ? cmd.getCallerServiceName() : cmd.getOperator();
        imageObject.markUploaded(actualMimeType, metadata.getContentLength(), metadata.getEtag(), modifier, now);
        boolean updated = imageObjectRepository.updatePendingToUploaded(imageObject);
        if (updated) {
            log.info("图片上传确认成功 objectId[{}] serviceName[{}]", imageObject.getObjectId(),
                    imageObject.getServiceName());
            return toResult(imageObject);
        }
        ImageObject latest = loadOwnedObject(cmd.getObjectId(), cmd.getCallerServiceName());
        if (latest.isUploaded()) {
            return toResult(latest);
        }
        throw new OssException(OssErrorCode.CONCURRENT_CONFLICT);
    }

    /**
     * 后台删除：COS 先删，成功后条件更新为 DELETED（§9.3）。
     *
     * @param objectId 对象标识
     * @param operator 操作人员
     */
    public void delete(String objectId, String operator) {
        ImageObject imageObject = imageObjectRepository.findByObjectId(objectId)
                .orElseThrow(() -> new OssException(OssErrorCode.OBJECT_NOT_FOUND));
        if (imageObject.isDeleted()) {
            return;
        }
        if (imageObject.isPending()) {
            deletePending(objectId, imageObject, operator);
            return;
        }
        if (!imageObject.isUploaded()) {
            throw new OssException(OssErrorCode.INVALID_STATE);
        }
        cancelActiveReplacementOnDelete(objectId, operator);
        objectStorageGateway.deleteObject(imageObject.getBucketName(), imageObject.getObjectKey());
        imageObject.markDeleted(operator, Instant.now());
        boolean updated = imageObjectRepository.updateUploadedToDeleted(imageObject);
        if (updated) {
            log.info("图片对象删除成功 objectId[{}] operator[{}]", objectId, operator);
            return;
        }
        ImageObject latest = imageObjectRepository.findByObjectId(objectId)
                .orElseThrow(() -> new OssException(OssErrorCode.OBJECT_NOT_FOUND));
        if (latest.isDeleted()) {
            return;
        }
        throw new OssException(OssErrorCode.CONCURRENT_CONFLICT);
    }

    /**
     * 清理未完成确认的上传任务（CR-002 BUGFIX-001）。
     * <p>
     * COS 对象可能尚未存在，删除按幂等处理；失败时保持 PENDING，允许管理人员重试。
     *
     * @param objectId    对象标识
     * @param imageObject 待上传图片对象
     * @param operator    操作人员
     */
    private void deletePending(String objectId, ImageObject imageObject, String operator) {
        cancelActiveReplacementOnDelete(objectId, operator);
        objectStorageGateway.deleteObject(imageObject.getBucketName(), imageObject.getObjectKey());
        imageObject.markDeleted(operator, Instant.now());
        boolean updated = imageObjectRepository.updatePendingToDeleted(imageObject);
        if (updated) {
            log.info("待上传图片对象删除成功 objectId[{}] operator[{}]", objectId, operator);
            return;
        }
        ImageObject latest = imageObjectRepository.findByObjectId(objectId)
                .orElseThrow(() -> new OssException(OssErrorCode.OBJECT_NOT_FOUND));
        if (latest.isDeleted()) {
            return;
        }
        throw new OssException(OssErrorCode.CONCURRENT_CONFLICT);
    }

    /**
     * 后台分页查询（分页由平台分页插件驱动）。
     *
     * @param query 查询条件
     * @return 图片对象列表
     */
    public List<ImageObjectResult> search(ImageObjectQuery query) {
        ImageObjectSearchCriteria criteria = ImageObjectSearchCriteria.builder()
                .serviceName(query.getServiceName())
                .status(parseStatus(query.getStatus()))
                .contentType(query.getContentType())
                .objectKey(query.getObjectKey())
                .originalFilenamePrefix(query.getOriginalFilename())
                .uploadBeginTime(query.getUploadBeginTime())
                .uploadEndTime(query.getUploadEndTime())
                .build();
        List<ImageObject> imageObjects = imageObjectRepository.search(criteria);
        return PageUtil.convert(imageObjects, this::toResult);
    }

    /**
     * 查询完整详情。
     *
     * @param objectId 对象标识
     * @return 图片对象
     */
    public ImageObjectResult detail(String objectId) {
        return toResult(imageObjectRepository.findByObjectId(objectId)
                .orElseThrow(() -> new OssException(OssErrorCode.OBJECT_NOT_FOUND)));
    }

    private void validateCallerServiceName(String callerServiceName) {
        try {
            ImageValidationPolicy.validateServiceName(callerServiceName);
        } catch (ImageValidationException exception) {
            throw new OssException(OssErrorCode.PARAMETER_INVALID, "invalid caller service name");
        }
    }

    private ImageObject loadOwnedObject(String objectId, String callerServiceName) {
        ImageObject imageObject = imageObjectRepository.findByObjectId(objectId)
                .orElseThrow(() -> new OssException(OssErrorCode.OBJECT_NOT_FOUND));
        if (!imageObject.belongsTo(callerServiceName)) {
            log.warn("图片对象[{}]不属于调用方[{}]", objectId, callerServiceName);
            throw new OssException(OssErrorCode.OBJECT_NOT_FOUND);
        }
        return imageObject;
    }

    private void deleteInvalidObject(ImageObject imageObject) {
        try {
            objectStorageGateway.deleteObject(imageObject.getBucketName(), imageObject.getObjectKey());
        } catch (RuntimeException exception) {
            log.error("非法图片对象删除失败 objectId[{}]", imageObject.getObjectId(), exception);
            throw new OssException(OssErrorCode.STORAGE_ERROR, "failed to delete invalid image object", exception);
        }
    }

    /**
     * 将领域对象转换为应用层结果（默认素材用例复用）。
     *
     * @param imageObject 领域对象
     * @return 应用层结果
     */
    public ImageObjectResult toResult(ImageObject imageObject) {
        return ImageObjectResult.builder()
                .objectId(imageObject.getObjectId())
                .serviceName(imageObject.getServiceName())
                .assetName(imageObject.getAssetName())
                .description(imageObject.getDescription())
                .originalFilename(imageObject.getOriginalFilename())
                .objectKey(imageObject.getObjectKey())
                .provider(imageObject.getProvider().name())
                .region(imageObject.getRegion())
                .bucketName(imageObject.getBucketName())
                .permission(imageObject.getPermission().name())
                .declaredContentType(imageObject.getDeclaredContentType())
                .declaredSize(imageObject.getDeclaredSize())
                .actualContentType(imageObject.getActualContentType())
                .actualSize(imageObject.getActualSize())
                .etag(imageObject.getEtag())
                .accessUrl(imageObject.isUploaded()
                        ? objectStorageGateway.buildPublicUrl(imageObject.getRegion(), imageObject.getBucketName(),
                        imageObject.getObjectKey())
                        : null)
                .status(imageObject.getStatus())
                .expireTime(imageObject.getExpireTime())
                .uploadTime(imageObject.getUploadTime())
                .deleteTime(imageObject.getDeleteTime())
                .deleteBy(imageObject.getDeleteBy())
                .createBy(imageObject.getCreateBy())
                .createTime(imageObject.getCreateTime())
                .modifyBy(imageObject.getModifyBy())
                .modifyTime(imageObject.getModifyTime())
                .build();
    }

    private ImageStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ImageStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new OssException(OssErrorCode.PARAMETER_INVALID, "unsupported image status");
        }
    }

    private OssException mapValidationFailure(ImageValidationException exception) {
        return switch (exception.getFailure()) {
            case INVALID_SERVICE_NAME, INVALID_FILENAME -> new OssException(OssErrorCode.PARAMETER_INVALID,
                    exception.getMessage());
            case UNSUPPORTED_MIME -> new OssException(OssErrorCode.UNSUPPORTED_MIME, exception.getMessage());
            case MIME_EXTENSION_MISMATCH -> new OssException(OssErrorCode.MIME_EXTENSION_MISMATCH,
                    exception.getMessage());
            case INVALID_DECLARED_SIZE -> new OssException(OssErrorCode.INVALID_DECLARED_SIZE,
                    exception.getMessage());
            case ACTUAL_SIZE_INVALID -> new OssException(OssErrorCode.ACTUAL_SIZE_INVALID, exception.getMessage());
            case ACTUAL_MIME_INVALID -> new OssException(OssErrorCode.ACTUAL_MIME_INVALID, exception.getMessage());
            case INVALID_ASSET -> new OssException(OssErrorCode.ASSET_INVALID, exception.getMessage());
            case REPLACEMENT_MIME_MISMATCH -> new OssException(OssErrorCode.PARAMETER_INVALID,
                    exception.getMessage());
        };
    }

    /**
     * 删除协调（EDD-OSS-DSN-CR-002 §8.6）：PENDING 先条件取消；PROMOTING 拒绝删除。
     *
     * @param objectId 目标图片对象标识
     * @param operator 操作人
     */
    private void cancelActiveReplacementOnDelete(String objectId, String operator) {
        ImageReplacement active = imageReplacementRepository.findActiveByObjectId(objectId).orElse(null);
        if (active == null) {
            return;
        }
        if (active.isPromoting()) {
            throw new OssException(OssErrorCode.REPLACEMENT_CONFLICT,
                    "image replacement is promoting, deletion is not allowed");
        }
        active.cancel(operator, Instant.now());
        if (!imageReplacementRepository.updatePendingToCancelled(active)) {
            throw new OssException(OssErrorCode.REPLACEMENT_CONFLICT,
                    "image replacement changed during cancellation");
        }
        log.info("图片删除前取消替换任务 objectId[{}] replacementId[{}]", objectId, active.getReplacementId());
    }

}
