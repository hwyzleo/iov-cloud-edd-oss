package net.hwyz.iov.cloud.edd.oss.service.application;

import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteImageReplacementCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateDefaultAssetCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateImageReplacementCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.UpdateDefaultAssetCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageReplacementUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectStorageGateway;
import net.hwyz.iov.cloud.edd.oss.service.application.service.DefaultAssetAppService;
import net.hwyz.iov.cloud.edd.oss.service.application.service.ImageAppService;
import net.hwyz.iov.cloud.edd.oss.service.application.service.ImageReplacementTxService;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageReplacementStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ObjectAccessPermission;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageReplacementRepository;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.CosProperties;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.ImageProperties;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos.CosObjectMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 默认素材应用服务单元测试（EDD-OSS-DSN-CR-002 §15.7.2）。
 *
 * @author hwyz_leo
 */
class DefaultAssetAppServiceTest {

    private static final String BUCKET = "iov-public-1253442587";
    private static final String REGION = "ap-shanghai";
    private static final String OBJECT_ID = "2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3";
    private static final String REPLACEMENT_ID = "fb62a661-946f-49b1-8268-a65991d44184";
    private static final String ORIGINAL_KEY = "images/edd-oss/" + OBJECT_ID + ".png";
    private static final String STAGING_KEY = "images/edd-oss/replacements/" + OBJECT_ID + "/"
            + REPLACEMENT_ID + ".png";
    private static final String BACKUP_KEY = "images/edd-oss/replacement-backups/" + OBJECT_ID + "/"
            + REPLACEMENT_ID + ".png";
    private static final String OPERATOR = "1001";
    private static final Pattern DEFAULT_ASSET_KEY_PATTERN = Pattern.compile(
            "images/edd-oss/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");

    private ImageObjectRepository imageObjectRepository;
    private ImageReplacementRepository imageReplacementRepository;
    private ObjectStorageGateway gateway;
    private ImageReplacementTxService txService;
    private ImageAppService imageAppService;
    private DefaultAssetAppService service;

    @BeforeEach
    void setUp() {
        imageObjectRepository = mock(ImageObjectRepository.class);
        imageReplacementRepository = mock(ImageReplacementRepository.class);
        gateway = mock(ObjectStorageGateway.class);
        txService = mock(ImageReplacementTxService.class);
        imageAppService = mock(ImageAppService.class);
        CosProperties cosProperties = new CosProperties();
        cosProperties.setRegion(REGION);
        cosProperties.setBucket(BUCKET);
        cosProperties.setSecretId("test-secret-id");
        cosProperties.setSecretKey("test-secret-key");
        ImageProperties imageProperties = new ImageProperties();
        service = new DefaultAssetAppService(imageObjectRepository, imageReplacementRepository, gateway, txService,
                imageAppService, cosProperties, imageProperties);
        when(gateway.generateUploadUrl(anyString(), anyString(), anyString(), any())).thenReturn("https://signed-url");
        when(imageAppService.toResult(any())).thenAnswer(invocation -> {
            ImageObject imageObject = invocation.getArgument(0);
            return ImageObjectResult.builder()
                    .objectId(imageObject.getObjectId())
                    .serviceName(imageObject.getServiceName())
                    .assetName(imageObject.getAssetName())
                    .description(imageObject.getDescription())
                    .originalFilename(imageObject.getOriginalFilename())
                    .objectKey(imageObject.getObjectKey())
                    .actualContentType(imageObject.getActualContentType())
                    .actualSize(imageObject.getActualSize())
                    .status(imageObject.getStatus())
                    .build();
        });
    }

    // ==================== 默认素材上传 ====================

    @Test
    @DisplayName("后台上传固定归属 edd-oss，保存素材字段与操作人")
    void generateFixesDefaultServiceAndOperator() {
        when(imageObjectRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ImageUploadResult result = service.generate(generateCmd("  默认用户头像  ", "  未设置头像时使用 "),
                OPERATOR);
        assertNotNull(result.getObjectId());
        assertTrue(DEFAULT_ASSET_KEY_PATTERN.matcher(result.getObjectKey()).matches());
        assertEquals("image/png", result.getContentType());
        assertEquals("https://signed-url", result.getUploadUrl());
        assertEquals("PUT", result.getHttpMethod());

        ArgumentCaptor<ImageObject> captor = ArgumentCaptor.forClass(ImageObject.class);
        verify(imageObjectRepository).insert(captor.capture());
        ImageObject inserted = captor.getValue();
        assertEquals(ImageObject.DEFAULT_ASSET_SERVICE_NAME, inserted.getServiceName());
        assertEquals("默认用户头像", inserted.getAssetName());
        assertEquals("未设置头像时使用", inserted.getDescription());
        assertEquals(OPERATOR, inserted.getCreateBy());
        assertEquals(ImageStatus.PENDING, inserted.getStatus());
        assertEquals(ImageMimeType.IMAGE_PNG, inserted.getDeclaredContentType());
    }

    @Test
    @DisplayName("素材名称为空或超过 100 字符返回 OSS-400104，且不签名、不落库")
    void generateRejectsInvalidAssetName() {
        assertGenerateFailure(OssErrorCode.ASSET_INVALID, generateCmd("   ", null));
        assertGenerateFailure(OssErrorCode.ASSET_INVALID, generateCmd("a".repeat(101), null));
        verify(gateway, never()).generateUploadUrl(anyString(), anyString(), anyString(), any());
        verify(imageObjectRepository, never()).insert(any());
    }

    @Test
    @DisplayName("备注超过 255 字符返回 OSS-400104")
    void generateRejectsInvalidDescription() {
        assertGenerateFailure(OssErrorCode.ASSET_INVALID, generateCmd("素材", "a".repeat(256)));
    }

    @Test
    @DisplayName("空白备注保存为 null")
    void generateNormalizesBlankDescription() {
        when(imageObjectRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.generate(generateCmd("素材", "   "), OPERATOR);
        ArgumentCaptor<ImageObject> captor = ArgumentCaptor.forClass(ImageObject.class);
        verify(imageObjectRepository).insert(captor.capture());
        assertNull(captor.getValue().getDescription());
    }

    @Test
    @DisplayName("签名失败不插入记录")
    void generateSignatureFailureDoesNotInsert() {
        when(gateway.generateUploadUrl(anyString(), anyString(), anyString(), any()))
                .thenThrow(new OssException(OssErrorCode.STORAGE_ERROR));
        assertThrows(OssException.class, () -> service.generate(generateCmd("素材", null), OPERATOR));
        verify(imageObjectRepository, never()).insert(any());
    }

    @Test
    @DisplayName("唯一键冲突重试一次，连续冲突按内部错误处理")
    void generateRetriesOnceThenFails() {
        when(imageObjectRepository.insert(any()))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.generate(generateCmd("素材", null), OPERATOR);
        verify(imageObjectRepository, times(2)).insert(any());

        when(imageObjectRepository.insert(any())).thenThrow(new DuplicateKeyException("dup"));
        OssException exception = assertThrows(OssException.class,
                () -> service.generate(generateCmd("素材", null), OPERATOR));
        assertEquals(OssErrorCode.INTERNAL_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("完成确认复用既有流程并固定 edd-oss、传入管理操作人")
    void completeDelegatesWithDefaultService() {
        when(imageAppService.complete(any())).thenReturn(ImageObjectResult.builder().build());
        service.complete(OBJECT_ID, OPERATOR);
        ArgumentCaptor<CompleteImageUploadCmd> captor = ArgumentCaptor.forClass(CompleteImageUploadCmd.class);
        verify(imageAppService).complete(captor.capture());
        assertEquals(OBJECT_ID, captor.getValue().getObjectId());
        assertEquals(ImageObject.DEFAULT_ASSET_SERVICE_NAME, captor.getValue().getCallerServiceName());
        assertEquals(OPERATOR, captor.getValue().getOperator());
    }

    // ==================== 素材编辑 ====================

    @Test
    @DisplayName("编辑默认素材成功且不调用 COS")
    void updateAssetSuccess() {
        ImageObject imageObject = uploadedDefaultAsset();
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(imageObject));
        when(imageObjectRepository.updateUploadedAsset(imageObject)).thenReturn(true);
        ImageObjectResult result = service.updateAsset(UpdateDefaultAssetCmd.builder()
                .objectId(OBJECT_ID).assetName(" 新名称 ").description(" 新备注 ").build(), OPERATOR);
        assertEquals("新名称", result.getAssetName());
        assertEquals("新备注", result.getDescription());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("非默认素材或非 UPLOADED 拒绝编辑")
    void updateAssetRejectsNonDefaultOrNotUploaded() {
        when(imageObjectRepository.findByObjectId("business")).thenReturn(Optional.of(businessUploaded()));
        assertEquals(OssErrorCode.INVALID_STATE, assertUpdateFailure("business"));

        when(imageObjectRepository.findByObjectId("pending")).thenReturn(Optional.of(defaultPending()));
        assertEquals(OssErrorCode.INVALID_STATE, assertUpdateFailure("pending"));
        verify(imageObjectRepository, never()).updateUploadedAsset(any());
    }

    @Test
    @DisplayName("存在 PROMOTING 替换任务时编辑返回 OSS-409004")
    void updateAssetRejectsPromotingReplacement() {
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(uploadedDefaultAsset()));
        when(imageReplacementRepository.findActiveByObjectId(OBJECT_ID))
                .thenReturn(Optional.of(promotingReplacement()));
        assertEquals(OssErrorCode.REPLACEMENT_CONFLICT, assertUpdateFailure(OBJECT_ID));
        verify(imageObjectRepository, never()).updateUploadedAsset(any());
    }

    @Test
    @DisplayName("PENDING 替换任务允许编辑；条件更新失败返回并发冲突")
    void updateAssetReportsConcurrentConflict() {
        ImageObject imageObject = uploadedDefaultAsset();
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(imageObject));
        when(imageReplacementRepository.findActiveByObjectId(OBJECT_ID))
                .thenReturn(Optional.of(pendingReplacement()));
        when(imageObjectRepository.updateUploadedAsset(imageObject)).thenReturn(false);
        assertEquals(OssErrorCode.CONCURRENT_CONFLICT, assertUpdateFailure(OBJECT_ID));
    }

    @Test
    @DisplayName("编辑素材名称非法返回 OSS-400104")
    void updateAssetRejectsInvalidName() {
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(uploadedDefaultAsset()));
        OssException exception = assertThrows(OssException.class, () -> service.updateAsset(
                UpdateDefaultAssetCmd.builder().objectId(OBJECT_ID).assetName("   ").build(), OPERATOR));
        assertEquals(OssErrorCode.ASSET_INVALID, exception.getErrorCode());
    }

    // ==================== 创建替换任务 ====================

    @Test
    @DisplayName("创建替换任务使用独立临时键，返回原稳定对象键与临时上传地址")
    void generateReplacementSuccess() {
        ImageObject imageObject = uploadedDefaultAsset();
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(imageObject));
        when(imageReplacementRepository.findActiveByObjectId(OBJECT_ID)).thenReturn(Optional.empty());
        when(imageReplacementRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ImageReplacementUploadResult result = service.generateReplacement(GenerateImageReplacementCmd.builder()
                .objectId(OBJECT_ID).originalFilename("avatar-v2.png").contentType("image/png").size(307200L).build(),
                OPERATOR);
        assertEquals(ORIGINAL_KEY, result.getObjectKey());
        assertEquals("https://signed-url", result.getUploadUrl());
        assertNotNull(result.getReplacementId());

        ArgumentCaptor<ImageReplacement> captor = ArgumentCaptor.forClass(ImageReplacement.class);
        verify(imageReplacementRepository).insert(captor.capture());
        ImageReplacement inserted = captor.getValue();
        assertEquals(ImageReplacementStatus.PENDING, inserted.getStatus());
        assertEquals(OBJECT_ID, inserted.getActiveObjectId());
        assertTrue(inserted.getStagingObjectKey().startsWith("images/edd-oss/replacements/" + OBJECT_ID + "/"));
        assertTrue(inserted.getBackupObjectKey().startsWith("images/edd-oss/replacement-backups/" + OBJECT_ID + "/"));
        assertEquals("avatar-v2.png", inserted.getOriginalFilename());
        assertEquals(OPERATOR, inserted.getCreateBy());
    }

    @Test
    @DisplayName("非默认素材、非 UPLOADED 拒绝创建替换任务")
    void generateReplacementRejectsInvalidTarget() {
        when(imageObjectRepository.findByObjectId("business")).thenReturn(Optional.of(businessUploaded()));
        assertEquals(OssErrorCode.INVALID_STATE, assertReplacementFailure("business"));
        when(imageObjectRepository.findByObjectId("pending")).thenReturn(Optional.of(defaultPending()));
        assertEquals(OssErrorCode.INVALID_STATE, assertReplacementFailure("pending"));
        verify(imageReplacementRepository, never()).insert(any());
    }

    @Test
    @DisplayName("已存在活动替换任务时返回 OSS-409004，不签名、不插入")
    void generateReplacementRejectsActiveTask() {
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(uploadedDefaultAsset()));
        when(imageReplacementRepository.findActiveByObjectId(OBJECT_ID))
                .thenReturn(Optional.of(pendingReplacement()));
        assertEquals(OssErrorCode.REPLACEMENT_CONFLICT, assertReplacementFailure(OBJECT_ID));
        verify(imageObjectRepository, never()).insert(any());
    }

    @Test
    @DisplayName("新图片格式与原图片不一致拒绝替换")
    void generateReplacementRejectsMimeChange() {
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(uploadedDefaultAsset()));
        when(imageReplacementRepository.findActiveByObjectId(OBJECT_ID)).thenReturn(Optional.empty());
        OssException exception = assertThrows(OssException.class, () -> service.generateReplacement(
                GenerateImageReplacementCmd.builder().objectId(OBJECT_ID).originalFilename("v2.jpg")
                        .contentType("image/jpeg").size(204800L).build(), OPERATOR));
        assertEquals(OssErrorCode.PARAMETER_INVALID, exception.getErrorCode());
        verify(imageReplacementRepository, never()).insert(any());
    }

    @Test
    @DisplayName("活动唯一约束冲突返回 OSS-409004")
    void generateReplacementReportsActiveConflict() {
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(uploadedDefaultAsset()));
        when(imageReplacementRepository.findActiveByObjectId(OBJECT_ID)).thenReturn(Optional.empty());
        when(imageReplacementRepository.insert(any())).thenThrow(new DuplicateKeyException("active"));
        assertEquals(OssErrorCode.REPLACEMENT_CONFLICT, assertReplacementFailure(OBJECT_ID));
    }

    // ==================== 完成替换 ====================

    @Test
    @DisplayName("替换成功：保持 objectId/objectKey，更新新图片元数据并清理临时与备份对象")
    void completeReplacementSuccess() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement replacement = pendingReplacement();
        mockPromotion(imageObject, replacement);
        when(imageReplacementRepository.updatePromotingToCompleted(any())).thenReturn(true);
        ImageObjectResult result = service.completeReplacement(completeCmd());
        assertEquals(OBJECT_ID, result.getObjectId());
        assertEquals(ORIGINAL_KEY, result.getObjectKey());
        assertEquals("avatar-v2.png", imageObject.getOriginalFilename());
        assertEquals(300000L, imageObject.getActualSize());
        assertEquals("new-etag", imageObject.getEtag());
        verify(gateway).copyObject(BUCKET, ORIGINAL_KEY, BUCKET, BACKUP_KEY, "old-etag");
        verify(gateway).copyObject(BUCKET, STAGING_KEY, BUCKET, ORIGINAL_KEY, "new-etag");
        verify(txService).commit(imageObject, replacement);
        verify(gateway).deleteObject(BUCKET, STAGING_KEY);
        verify(gateway).deleteObject(BUCKET, BACKUP_KEY);
    }

    @Test
    @DisplayName("replacementId 不存在或不属于 objectId 返回 OSS-404002")
    void completeReplacementNotFound() {
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID)).thenReturn(Optional.empty());
        assertEquals(OssErrorCode.REPLACEMENT_NOT_FOUND, assertCompleteReplacementFailure());

        ImageReplacement other = ImageReplacement.pending(REPLACEMENT_ID, "other-object-id", STAGING_KEY, BACKUP_KEY,
                "avatar-v2.png", ImageMimeType.IMAGE_PNG, 307200L, Instant.now().plusSeconds(900), OPERATOR,
                Instant.now());
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID)).thenReturn(Optional.of(other));
        assertEquals(OssErrorCode.REPLACEMENT_NOT_FOUND, assertCompleteReplacementFailure());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("已完成任务幂等返回当前图片，不再次 HEAD 或覆盖对象")
    void completeReplacementIsIdempotentWhenCompleted() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement completed = completedReplacement();
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID)).thenReturn(Optional.of(completed));
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(imageObject));
        ImageObjectResult result = service.completeReplacement(completeCmd());
        assertEquals(OBJECT_ID, result.getObjectId());
        verify(gateway, never()).headObject(anyString(), anyString());
        verify(gateway, never()).copyObject(anyString(), anyString(), anyString(), anyString(), any());
        verify(txService, never()).commit(any(), any());
        verify(gateway).deleteObject(BUCKET, STAGING_KEY);
    }

    @Test
    @DisplayName("已取消或已失败任务拒绝完成")
    void completeReplacementRejectsTerminalFailure() {
        ImageReplacement cancelled = ImageReplacement.restore(1L, REPLACEMENT_ID, OBJECT_ID, null, STAGING_KEY,
                BACKUP_KEY, "avatar-v2.png", ImageMimeType.IMAGE_PNG, 307200L, null, null, null,
                ImageReplacementStatus.CANCELLED, Instant.now(), "cancelled", 2, OPERATOR, Instant.now(), OPERATOR,
                Instant.now());
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID)).thenReturn(Optional.of(cancelled));
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(uploadedDefaultAsset()));
        assertEquals(OssErrorCode.INVALID_STATE, assertCompleteReplacementFailure());
    }

    @Test
    @DisplayName("PENDING 替换任务过期返回 OSS-409001，不上传不改原对象")
    void completeReplacementRejectsExpired() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement expired = ImageReplacement.pending(REPLACEMENT_ID, OBJECT_ID, STAGING_KEY, BACKUP_KEY,
                "avatar-v2.png", ImageMimeType.IMAGE_PNG, 307200L, Instant.now().minusSeconds(1), OPERATOR,
                Instant.now().minusSeconds(900));
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID)).thenReturn(Optional.of(expired));
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(imageObject));
        assertEquals(OssErrorCode.UPLOAD_EXPIRED, assertCompleteReplacementFailure());
        verify(gateway, never()).copyObject(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("临时对象缺失保持 PENDING，原对象不变")
    void completeReplacementKeepsPendingWhenStagingMissing() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement replacement = pendingReplacement();
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID)).thenReturn(Optional.of(replacement));
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(imageObject));
        when(gateway.headObject(BUCKET, STAGING_KEY)).thenReturn(null);
        assertEquals(OssErrorCode.IMAGE_NOT_UPLOADED, assertCompleteReplacementFailure());
        verify(imageReplacementRepository, never()).updatePendingToPromoting(any());
        verify(gateway, never()).copyObject(anyString(), anyString(), anyString(), anyString(), any());
        assertTrue(replacement.isPending());
    }

    @Test
    @DisplayName("临时对象大小非法时删除临时对象并保持 PENDING")
    void completeReplacementDeletesInvalidStaging() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement replacement = pendingReplacement();
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID)).thenReturn(Optional.of(replacement));
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(imageObject));
        when(gateway.headObject(BUCKET, STAGING_KEY)).thenReturn(metadata("image/png", 0L, "new-etag"));
        assertEquals(OssErrorCode.ACTUAL_SIZE_INVALID, assertCompleteReplacementFailure());
        verify(gateway).deleteObject(BUCKET, STAGING_KEY);
        verify(imageReplacementRepository, never()).updatePendingToPromoting(any());
        assertTrue(replacement.isPending());
    }

    @Test
    @DisplayName("备份失败时不覆盖原对象，恢复未确认保留 PROMOTING 与故障说明")
    void completeReplacementKeepsPromotingWhenBackupFails() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement replacement = pendingReplacement();
        mockStagingVerification(imageObject, replacement);
        doThrow(new OssException(OssErrorCode.STORAGE_ERROR)).when(gateway)
                .copyObject(BUCKET, ORIGINAL_KEY, BUCKET, BACKUP_KEY, "old-etag");
        doThrow(new OssException(OssErrorCode.STORAGE_ERROR)).when(gateway)
                .copyObject(BUCKET, BACKUP_KEY, BUCKET, ORIGINAL_KEY, null);
        assertEquals(OssErrorCode.STORAGE_ERROR, assertCompleteReplacementFailure());
        verify(gateway, never()).copyObject(eq(BUCKET), eq(STAGING_KEY), eq(BUCKET), eq(ORIGINAL_KEY), any());
        verify(imageReplacementRepository).updateFaultDescription(replacement);
        verify(imageReplacementRepository, never()).updatePromotingToFailed(any());
        verify(txService, never()).commit(any(), any());
        assertTrue(replacement.isPromoting());
    }

    @Test
    @DisplayName("提升失败但恢复确认后任务 FAILED 并清理对象")
    void completeReplacementRestoresAndFailsWhenPromotionFails() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement replacement = pendingReplacement();
        mockStagingVerification(imageObject, replacement);
        doThrow(new OssException(OssErrorCode.STORAGE_ERROR)).when(gateway)
                .copyObject(BUCKET, STAGING_KEY, BUCKET, ORIGINAL_KEY, "new-etag");
        when(gateway.headObject(BUCKET, ORIGINAL_KEY)).thenReturn(metadata("image/png", 204800L, "old-etag"),
                metadata("image/png", 204800L, "old-etag"));
        when(imageReplacementRepository.updatePromotingToFailed(replacement)).thenReturn(true);
        assertEquals(OssErrorCode.STORAGE_ERROR, assertCompleteReplacementFailure());
        verify(imageReplacementRepository).updatePromotingToFailed(replacement);
        verify(txService, never()).commit(any(), any());
        verify(gateway).deleteObject(BUCKET, BACKUP_KEY);
        assertTrue(replacement.isFailed());
    }

    @Test
    @DisplayName("PROMOTING 重试：原对象已是新内容时直接重试最终提交")
    void completeReplacementRetryCommitsWhenObjectAlreadyPromoted() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement replacement = promotingReplacement();
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID)).thenReturn(Optional.of(replacement));
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(imageObject));
        when(gateway.headObject(BUCKET, ORIGINAL_KEY)).thenReturn(metadata("image/png", 300000L, "new-etag"));
        when(imageReplacementRepository.updatePromotingToCompleted(any())).thenReturn(true);
        service.completeReplacement(completeCmd());
        verify(gateway, never()).copyObject(anyString(), anyString(), anyString(), anyString(), any());
        verify(txService).commit(imageObject, replacement);
    }

    @Test
    @DisplayName("数据库提交失败时恢复旧对象并置任务 FAILED")
    void completeReplacementRestoresWhenCommitFails() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageObject storedImage = uploadedDefaultAsset();
        ImageReplacement replacement = pendingReplacement();
        ImageReplacement storedReplacement = promotingReplacement();
        mockStagingVerification(imageObject, replacement);
        when(imageObjectRepository.findByObjectId(OBJECT_ID))
                .thenReturn(Optional.of(imageObject), Optional.of(storedImage));
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID))
                .thenReturn(Optional.of(replacement), Optional.of(storedReplacement));
        doThrow(new OssException(OssErrorCode.CONCURRENT_CONFLICT)).when(txService).commit(any(), any());
        when(gateway.headObject(BUCKET, ORIGINAL_KEY)).thenReturn(metadata("image/png", 204800L, "old-etag"),
                metadata("image/png", 300000L, "new-etag"), metadata("image/png", 204800L, "old-etag"));
        when(imageReplacementRepository.updatePromotingToFailed(storedReplacement)).thenReturn(true);
        assertEquals(OssErrorCode.STORAGE_ERROR, assertCompleteReplacementFailure());
        verify(imageReplacementRepository).updatePromotingToFailed(storedReplacement);
        verify(gateway).copyObject(BUCKET, BACKUP_KEY, BUCKET, ORIGINAL_KEY, null);
    }

    @Test
    @DisplayName("清理失败不改变已提交结果")
    void completeReplacementIgnoresCleanupFailure() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement replacement = pendingReplacement();
        mockPromotion(imageObject, replacement);
        when(imageReplacementRepository.updatePromotingToCompleted(any())).thenReturn(true);
        doThrow(new RuntimeException("cleanup failure")).when(gateway).deleteObject(BUCKET, STAGING_KEY);
        ImageObjectResult result = service.completeReplacement(completeCmd());
        assertNotNull(result);
        verify(txService).commit(imageObject, replacement);
        verify(gateway).deleteObject(BUCKET, STAGING_KEY);
    }

    // ==================== helpers ====================

    private void mockPromotion(ImageObject imageObject, ImageReplacement replacement) {
        mockStagingVerification(imageObject, replacement);
        when(gateway.headObject(BUCKET, ORIGINAL_KEY)).thenReturn(metadata("image/png", 204800L, "old-etag"),
                metadata("image/png", 300000L, "new-etag"));
    }

    private void mockStagingVerification(ImageObject imageObject, ImageReplacement replacement) {
        when(imageReplacementRepository.findByReplacementId(REPLACEMENT_ID)).thenReturn(Optional.of(replacement));
        when(imageObjectRepository.findByObjectId(OBJECT_ID)).thenReturn(Optional.of(imageObject));
        when(gateway.headObject(BUCKET, STAGING_KEY)).thenReturn(metadata("image/png", 300000L, "new-etag"));
        when(imageReplacementRepository.updatePendingToPromoting(replacement)).thenReturn(true);
    }

    private GenerateDefaultAssetCmd generateCmd(String assetName, String description) {
        return GenerateDefaultAssetCmd.builder()
                .assetName(assetName)
                .description(description)
                .originalFilename("avatar.png")
                .contentType("image/png")
                .size(204800L)
                .build();
    }

    private CompleteImageReplacementCmd completeCmd() {
        return CompleteImageReplacementCmd.builder()
                .objectId(OBJECT_ID)
                .replacementId(REPLACEMENT_ID)
                .operator(OPERATOR)
                .build();
    }

    private ImageObject defaultPending() {
        return ImageObject.pending(OBJECT_ID, StorageProvider.TENCENT_COS, REGION, BUCKET, ORIGINAL_KEY,
                ImageObject.DEFAULT_ASSET_SERVICE_NAME, "默认用户头像", "未设置头像时使用", "avatar.png",
                ImageMimeType.IMAGE_PNG, 204800L, ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE,
                Instant.now().plusSeconds(900), OPERATOR, Instant.now());
    }

    private ImageObject uploadedDefaultAsset() {
        ImageObject imageObject = defaultPending();
        imageObject.markUploaded(ImageMimeType.IMAGE_PNG, 204800L, "old-etag", Instant.now());
        return imageObject;
    }

    private ImageObject businessUploaded() {
        ImageObject imageObject = ImageObject.pending("business", StorageProvider.TENCENT_COS, REGION, BUCKET,
                "images/sec-ciam/business.png", "sec-ciam", "avatar.png", ImageMimeType.IMAGE_PNG, 204800L,
                ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE, Instant.now().plusSeconds(900), "sec-ciam",
                Instant.now());
        imageObject.markUploaded(ImageMimeType.IMAGE_PNG, 204800L, "etag", Instant.now());
        return imageObject;
    }

    private ImageReplacement pendingReplacement() {
        return ImageReplacement.pending(REPLACEMENT_ID, OBJECT_ID, STAGING_KEY, BACKUP_KEY, "avatar-v2.png",
                ImageMimeType.IMAGE_PNG, 307200L, Instant.now().plusSeconds(900), OPERATOR, Instant.now());
    }

    private ImageReplacement promotingReplacement() {
        ImageReplacement replacement = pendingReplacement();
        replacement.startPromoting(ImageMimeType.IMAGE_PNG, 300000L, "new-etag", OPERATOR, Instant.now());
        return replacement;
    }

    private ImageReplacement completedReplacement() {
        ImageReplacement replacement = promotingReplacement();
        replacement.markCompleted(OPERATOR, Instant.now());
        return replacement;
    }

    private CosObjectMetadata metadata(String contentType, long size, String etag) {
        return CosObjectMetadata.builder().contentType(contentType).contentLength(size).etag(etag).build();
    }

    private void assertGenerateFailure(OssErrorCode expected, GenerateDefaultAssetCmd cmd) {
        OssException exception = assertThrows(OssException.class, () -> service.generate(cmd, OPERATOR));
        assertEquals(expected, exception.getErrorCode());
    }

    private OssErrorCode assertUpdateFailure(String objectId) {
        OssException exception = assertThrows(OssException.class, () -> service.updateAsset(
                UpdateDefaultAssetCmd.builder().objectId(objectId).assetName("名称").build(), OPERATOR));
        return exception.getErrorCode();
    }

    private OssErrorCode assertReplacementFailure(String objectId) {
        OssException exception = assertThrows(OssException.class, () -> service.generateReplacement(
                GenerateImageReplacementCmd.builder().objectId(objectId).originalFilename("v2.png")
                        .contentType("image/png").size(307200L).build(), OPERATOR));
        return exception.getErrorCode();
    }

    private OssErrorCode assertCompleteReplacementFailure() {
        OssException exception = assertThrows(OssException.class, () -> service.completeReplacement(completeCmd()));
        return exception.getErrorCode();
    }

}
