package net.hwyz.iov.cloud.edd.oss.service.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteImageReplacementCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateDefaultAssetCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateImageReplacementCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.UpdateDefaultAssetCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageReplacementUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectStorageGateway;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.domain.exception.ImageValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ObjectAccessPermission;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageReplacementRepository;
import net.hwyz.iov.cloud.edd.oss.service.domain.service.ImageReplacementPolicy;
import net.hwyz.iov.cloud.edd.oss.service.domain.service.ImageValidationPolicy;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.CosProperties;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.ImageProperties;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos.CosObjectMetadata;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 后台默认素材应用服务（EDD-OSS-DSN-CR-002 §8、§9）。
 * <p>
 * 负责默认素材上传、素材信息编辑、替换任务创建与替换完成编排。
 * COS 网络调用期间不开启数据库长事务；最终数据库写入由 {@link ImageReplacementTxService} 本地事务完成。
 *
 * @author hwyz_leo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAssetAppService {

    private static final String HTTP_METHOD_PUT = "PUT";
    private static final String DEFAULT_ASSET_SERVICE_NAME = ImageObject.DEFAULT_ASSET_SERVICE_NAME;

    private final ImageObjectRepository imageObjectRepository;
    private final ImageReplacementRepository imageReplacementRepository;
    private final ObjectStorageGateway objectStorageGateway;
    private final ImageReplacementTxService imageReplacementTxService;
    private final ImageAppService imageAppService;
    private final CosProperties cosProperties;
    private final ImageProperties imageProperties;

    /**
     * 申请默认素材上传：校验素材与图片参数 → 固定 edd-oss → 签名 → 插入 PENDING（§8.1）。
     *
     * @param cmd      申请命令
     * @param operator 管理操作人
     * @return 上传参数
     */
    public ImageUploadResult generate(GenerateDefaultAssetCmd cmd, String operator) {
        String assetName;
        String description;
        ImageMimeType declaredMimeType;
        try {
            assetName = ImageValidationPolicy.validateAssetName(cmd.getAssetName());
            description = ImageValidationPolicy.validateDescription(cmd.getDescription());
            declaredMimeType = ImageValidationPolicy.validateGenerate(DEFAULT_ASSET_SERVICE_NAME,
                    cmd.getOriginalFilename(), cmd.getContentType(), cmd.getSize(),
                    imageProperties.getMaxSizeBytes());
        } catch (ImageValidationException exception) {
            throw mapValidationFailure(exception);
        }
        Instant now = Instant.now();
        Instant expireTime = now.plusSeconds(imageProperties.getUploadExpirationSeconds());
        ImageObject imageObject = null;
        String uploadUrl = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String objectId = UUID.randomUUID().toString();
            String objectKey = ImageValidationPolicy.buildObjectKey(DEFAULT_ASSET_SERVICE_NAME, objectId,
                    declaredMimeType);
            uploadUrl = objectStorageGateway.generateUploadUrl(cosProperties.getBucket(), objectKey,
                    declaredMimeType.getValue(), expireTime);
            imageObject = ImageObject.pending(objectId, StorageProvider.TENCENT_COS, cosProperties.getRegion(),
                    cosProperties.getBucket(), objectKey, DEFAULT_ASSET_SERVICE_NAME, assetName, description,
                    cmd.getOriginalFilename(), declaredMimeType, cmd.getSize(),
                    ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE, expireTime, operator, now);
            try {
                imageObjectRepository.insert(imageObject);
                log.info("创建默认素材上传任务 objectId[{}] assetName[{}] operator[{}]", objectId, assetName,
                        operator);
                break;
            } catch (DuplicateKeyException duplicateKeyException) {
                imageObject = null;
                log.warn("默认素材上传任务唯一键冲突，重试生成 objectId[{}]", objectId);
            }
        }
        if (imageObject == null) {
            throw new OssException(OssErrorCode.INTERNAL_ERROR, "failed to create default asset upload task");
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
     * 完成默认素材上传：复用既有完成确认（HEAD、实际元数据校验、非法对象删除、幂等）。
     *
     * @param objectId 对象标识
     * @param operator 管理操作人
     * @return 图片详情
     */
    public ImageObjectResult complete(String objectId, String operator) {
        return imageAppService.complete(CompleteImageUploadCmd.builder()
                .objectId(objectId)
                .callerServiceName(DEFAULT_ASSET_SERVICE_NAME)
                .operator(operator)
                .build());
    }

    /**
     * 编辑默认素材名称与备注：不调用 COS，不修改对象键、图片元数据或 URL（§8.2）。
     *
     * @param cmd      编辑命令
     * @param operator 管理操作人
     * @return 更新后的图片详情
     */
    public ImageObjectResult updateAsset(UpdateDefaultAssetCmd cmd, String operator) {
        ImageObject imageObject = loadObject(cmd.getObjectId());
        if (!ImageReplacementPolicy.isAssetEditable(imageObject)) {
            throw new OssException(OssErrorCode.INVALID_STATE,
                    "only uploaded default assets can be edited");
        }
        ImageReplacement active = imageReplacementRepository.findActiveByObjectId(cmd.getObjectId()).orElse(null);
        if (active != null && active.isPromoting()) {
            throw new OssException(OssErrorCode.REPLACEMENT_CONFLICT,
                    "image replacement is promoting, editing is not allowed");
        }
        String assetName;
        String description;
        try {
            assetName = ImageValidationPolicy.validateAssetName(cmd.getAssetName());
            description = ImageValidationPolicy.validateDescription(cmd.getDescription());
        } catch (ImageValidationException exception) {
            throw mapValidationFailure(exception);
        }
        imageObject.modifyAsset(assetName, description, operator, Instant.now());
        if (!imageObjectRepository.updateUploadedAsset(imageObject)) {
            throw new OssException(OssErrorCode.CONCURRENT_CONFLICT, "concurrent asset update");
        }
        log.info("默认素材编辑成功 objectId[{}] operator[{}]", cmd.getObjectId(), operator);
        return imageAppService.toResult(imageObject);
    }

    /**
     * 创建替换任务：独立临时对象键 + 15 分钟预签名上传，原图片记录与 URL 不变（§8.3）。
     *
     * @param cmd      替换申请命令
     * @param operator 管理操作人
     * @return 替换上传参数
     */
    public ImageReplacementUploadResult generateReplacement(GenerateImageReplacementCmd cmd, String operator) {
        ImageObject imageObject = loadObject(cmd.getObjectId());
        if (!ImageReplacementPolicy.isReplaceable(imageObject)) {
            throw new OssException(OssErrorCode.INVALID_STATE,
                    "only uploaded default assets can be replaced");
        }
        if (imageReplacementRepository.findActiveByObjectId(cmd.getObjectId()).isPresent()) {
            throw new OssException(OssErrorCode.REPLACEMENT_CONFLICT,
                    "an active replacement task already exists for this image");
        }
        ImageMimeType originalMimeType = imageObject.getActualContentType() == null
                ? imageObject.getDeclaredContentType() : imageObject.getActualContentType();
        ImageMimeType declaredMimeType;
        try {
            declaredMimeType = ImageValidationPolicy.validateReplacement(originalMimeType,
                    cmd.getOriginalFilename(), cmd.getContentType(), cmd.getSize(),
                    imageProperties.getMaxSizeBytes());
        } catch (ImageValidationException exception) {
            throw mapValidationFailure(exception);
        }
        Instant now = Instant.now();
        Instant expireTime = now.plusSeconds(imageProperties.getUploadExpirationSeconds());
        String replacementId = UUID.randomUUID().toString();
        String stagingObjectKey = ImageValidationPolicy.buildReplacementObjectKey(cmd.getObjectId(), replacementId,
                declaredMimeType);
        String backupObjectKey = ImageValidationPolicy.buildReplacementBackupKey(cmd.getObjectId(), replacementId,
                declaredMimeType);
        String uploadUrl = objectStorageGateway.generateUploadUrl(cosProperties.getBucket(), stagingObjectKey,
                declaredMimeType.getValue(), expireTime);
        ImageReplacement replacement = ImageReplacement.pending(replacementId, cmd.getObjectId(), stagingObjectKey,
                backupObjectKey, cmd.getOriginalFilename(), declaredMimeType, cmd.getSize(), expireTime, operator,
                now);
        try {
            imageReplacementRepository.insert(replacement);
        } catch (DuplicateKeyException duplicateKeyException) {
            throw new OssException(OssErrorCode.REPLACEMENT_CONFLICT,
                    "an active replacement task already exists for this image", duplicateKeyException);
        }
        log.info("创建替换任务 objectId[{}] replacementId[{}] operator[{}]", cmd.getObjectId(), replacementId,
                operator);
        return ImageReplacementUploadResult.builder()
                .replacementId(replacementId)
                .objectId(imageObject.getObjectId())
                .objectKey(imageObject.getObjectKey())
                .uploadUrl(uploadUrl)
                .httpMethod(HTTP_METHOD_PUT)
                .contentType(declaredMimeType.getValue())
                .expiresAt(expireTime)
                .build();
    }

    /**
     * 完成或恢复替换：HEAD 临时对象 → PENDING→PROMOTING → 备份 → 提升 → 复核 → 本地事务 → 清理（§8.4～8.5）。
     *
     * @param cmd 完成命令
     * @return 更新后的图片详情
     */
    public ImageObjectResult completeReplacement(CompleteImageReplacementCmd cmd) {
        ImageReplacement replacement = loadReplacement(cmd.getObjectId(), cmd.getReplacementId());
        ImageObject imageObject = loadObject(cmd.getObjectId());
        String operator = cmd.getOperator();
        if (replacement.isCompleted()) {
            cleanup(replacement);
            return imageAppService.toResult(imageObject);
        }
        if (replacement.isCancelled() || replacement.isFailed()) {
            throw new OssException(OssErrorCode.INVALID_STATE,
                    "replacement task is " + replacement.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (!imageObject.isUploaded()) {
            throw new OssException(OssErrorCode.INVALID_STATE, "image is not UPLOADED");
        }
        if (replacement.isPending()) {
            promoteFromPending(imageObject, replacement, operator);
        }
        return promoteAndCommit(imageObject, replacement, operator);
    }

    private void promoteFromPending(ImageObject imageObject, ImageReplacement replacement, String operator) {
        Instant now = Instant.now();
        if (replacement.isExpired(now)) {
            throw new OssException(OssErrorCode.UPLOAD_EXPIRED, "replacement upload task has expired");
        }
        CosObjectMetadata metadata = objectStorageGateway.headObject(imageObject.getBucketName(),
                replacement.getStagingObjectKey());
        if (metadata == null) {
            log.warn("替换临时对象尚未上传 objectId[{}] replacementId[{}] key[{}]", imageObject.getObjectId(),
                    replacement.getReplacementId(), replacement.getStagingObjectKey());
            throw new OssException(OssErrorCode.IMAGE_NOT_UPLOADED, "replacement object has not been uploaded");
        }
        ImageMimeType actualMimeType;
        try {
            actualMimeType = ImageReplacementPolicy.validateStagedMetadata(replacement, metadata.getContentType(),
                    metadata.getContentLength(), imageProperties.getMaxSizeBytes());
        } catch (ImageValidationException exception) {
            deleteInvalidStaging(replacement);
            throw mapValidationFailure(exception);
        }
        replacement.startPromoting(actualMimeType, metadata.getContentLength(), metadata.getEtag(), operator, now);
        if (!imageReplacementRepository.updatePendingToPromoting(replacement)) {
            throw new OssException(OssErrorCode.REPLACEMENT_CONFLICT,
                    "replacement task state changed before promotion");
        }
    }

    private ImageObjectResult promoteAndCommit(ImageObject imageObject, ImageReplacement replacement,
                                               String operator) {
        String bucket = imageObject.getBucketName();
        CosObjectMetadata current = objectStorageGateway.headObject(bucket, imageObject.getObjectKey());
        if (!isNewContent(current, replacement)) {
            try {
                objectStorageGateway.copyObject(bucket, imageObject.getObjectKey(), bucket,
                        replacement.getBackupObjectKey(), imageObject.getEtag());
                objectStorageGateway.copyObject(bucket, replacement.getStagingObjectKey(), bucket,
                        imageObject.getObjectKey(), replacement.getEtag());
                CosObjectMetadata promoted = objectStorageGateway.headObject(bucket, imageObject.getObjectKey());
                if (!isNewContent(promoted, replacement)) {
                    return restoreAndFail(imageObject, replacement, operator,
                            "promoted object metadata mismatch");
                }
            } catch (RuntimeException exception) {
                log.error("替换提升失败 objectId[{}] replacementId[{}]", imageObject.getObjectId(),
                        replacement.getReplacementId(), exception);
                return restoreAndFail(imageObject, replacement, operator, "failed to promote replacement image");
            }
        }
        Instant now = Instant.now();
        imageObject.markReplaced(replacement.getOriginalFilename(), replacement.getDeclaredSize(),
                replacement.getActualContentType(), replacement.getActualSize(), replacement.getEtag(), operator, now);
        replacement.markCompleted(operator, now);
        try {
            imageReplacementTxService.commit(imageObject, replacement);
        } catch (RuntimeException exception) {
            log.error("替换最终提交失败 objectId[{}] replacementId[{}]", imageObject.getObjectId(),
                    replacement.getReplacementId(), exception);
            ImageObject storedImage = loadObject(imageObject.getObjectId());
            ImageReplacement storedReplacement = loadReplacement(imageObject.getObjectId(),
                    replacement.getReplacementId());
            return restoreAndFail(storedImage, storedReplacement, operator, "failed to commit replacement metadata");
        }
        cleanup(replacement);
        return imageAppService.toResult(imageObject);
    }

    /**
     * 恢复旧对象并判定失败；恢复未确认时保留 PROMOTING、活动标识与故障说明。
     */
    private ImageObjectResult restoreAndFail(ImageObject imageObject, ImageReplacement replacement, String operator,
                                             String reason) {
        Instant now = Instant.now();
        String bucket = imageObject.getBucketName();
        try {
            objectStorageGateway.copyObject(bucket, replacement.getBackupObjectKey(), bucket,
                    imageObject.getObjectKey(), null);
            CosObjectMetadata restored = objectStorageGateway.headObject(bucket, imageObject.getObjectKey());
            if (!isOldContent(restored, imageObject)) {
                throw new OssException(OssErrorCode.STORAGE_ERROR, "replacement recovery is not confirmed");
            }
        } catch (RuntimeException exception) {
            log.error("替换恢复失败 objectId[{}] replacementId[{}]", imageObject.getObjectId(),
                    replacement.getReplacementId(), exception);
            recordFault(replacement, operator, reason + ": " + exception.getMessage(), now);
            if (exception instanceof OssException ossException) {
                throw ossException;
            }
            throw new OssException(OssErrorCode.STORAGE_ERROR, "replacement recovery failed", exception);
        }
        replacement.markFailed(reason, operator, now);
        if (!imageReplacementRepository.updatePromotingToFailed(replacement)) {
            throw new OssException(OssErrorCode.REPLACEMENT_CONFLICT,
                    "replacement task state changed during recovery");
        }
        cleanup(replacement);
        throw new OssException(OssErrorCode.STORAGE_ERROR, "replacement failed and original image restored");
    }

    private void recordFault(ImageReplacement replacement, String operator, String reason, Instant now) {
        try {
            replacement.recordFault(truncate(reason), operator, now);
            imageReplacementRepository.updateFaultDescription(replacement);
        } catch (RuntimeException exception) {
            log.error("记录替换故障说明失败 replacementId[{}]", replacement.getReplacementId(), exception);
        }
    }

    private void deleteInvalidStaging(ImageReplacement replacement) {
        try {
            objectStorageGateway.deleteObject(cosProperties.getBucket(), replacement.getStagingObjectKey());
        } catch (RuntimeException exception) {
            log.error("非法替换对象删除失败 replacementId[{}]", replacement.getReplacementId(), exception);
            throw new OssException(OssErrorCode.STORAGE_ERROR,
                    "failed to delete invalid replacement object", exception);
        }
    }

    /**
     * 事务提交后清理临时与备份对象；清理失败不影响已提交结果，也不提供备份恢复接口。
     */
    private void cleanup(ImageReplacement replacement) {
        deleteQuietly(replacement.getStagingObjectKey());
        deleteQuietly(replacement.getBackupObjectKey());
    }

    private void deleteQuietly(String objectKey) {
        try {
            objectStorageGateway.deleteObject(cosProperties.getBucket(), objectKey);
        } catch (RuntimeException exception) {
            log.warn("清理替换对象失败 key[{}]", objectKey, exception);
        }
    }

    private boolean isNewContent(CosObjectMetadata metadata, ImageReplacement replacement) {
        return metadata != null
                && replacement.getActualSize() != null
                && metadata.getContentLength() == replacement.getActualSize()
                && replacement.getActualContentType() != null
                && replacement.getActualContentType().getValue().equals(normalize(metadata.getContentType()));
    }

    private boolean isOldContent(CosObjectMetadata metadata, ImageObject imageObject) {
        return metadata != null
                && imageObject.getActualSize() != null
                && metadata.getContentLength() == imageObject.getActualSize()
                && imageObject.getActualContentType() != null
                && imageObject.getActualContentType().getValue().equals(normalize(metadata.getContentType()));
    }

    private String normalize(String contentType) {
        return contentType == null ? null : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String reason) {
        return reason.length() <= ImageObject.MAX_DESCRIPTION_LENGTH ? reason
                : reason.substring(0, ImageObject.MAX_DESCRIPTION_LENGTH);
    }

    private ImageObject loadObject(String objectId) {
        return imageObjectRepository.findByObjectId(objectId)
                .orElseThrow(() -> new OssException(OssErrorCode.OBJECT_NOT_FOUND));
    }

    private ImageReplacement loadReplacement(String objectId, String replacementId) {
        ImageReplacement replacement = imageReplacementRepository.findByReplacementId(replacementId)
                .orElseThrow(() -> new OssException(OssErrorCode.REPLACEMENT_NOT_FOUND));
        if (!Objects.equals(replacement.getObjectId(), objectId)) {
            throw new OssException(OssErrorCode.REPLACEMENT_NOT_FOUND);
        }
        return replacement;
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

}
