package net.hwyz.iov.cloud.edd.oss.service.application;

import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.query.ImageObjectQuery;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectStorageGateway;
import net.hwyz.iov.cloud.edd.oss.service.application.service.ImageAppService;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
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
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 图片应用服务单元测试（EDD-OSS-DSN-CR-001 §15.2）。
 *
 * @author hwyz_leo
 */
class ImageAppServiceTest {

    private static final String SERVICE_NAME = "tsp-account";
    private static final String OTHER_SERVICE = "tsp-other";
    private static final String BUCKET = "iov-public-1253442587";
    private static final String REGION = "ap-shanghai";
    private static final String PUBLIC_URL_PREFIX = "https://" + BUCKET + ".cos." + REGION + ".myqcloud.com/";
    private static final Pattern OBJECT_KEY_PATTERN = Pattern.compile(
            "images/tsp-account/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");

    private ImageObjectRepository repository;
    private ImageReplacementRepository replacementRepository;
    private ObjectStorageGateway gateway;
    private ImageAppService imageAppService;

    @BeforeEach
    void setUp() {
        repository = mock(ImageObjectRepository.class);
        replacementRepository = mock(ImageReplacementRepository.class);
        gateway = mock(ObjectStorageGateway.class);
        CosProperties cosProperties = new CosProperties();
        cosProperties.setRegion(REGION);
        cosProperties.setBucket(BUCKET);
        cosProperties.setSecretId("test-secret-id");
        cosProperties.setSecretKey("test-secret-key");
        ImageProperties imageProperties = new ImageProperties();
        imageAppService = new ImageAppService(repository, replacementRepository, gateway, cosProperties,
                imageProperties);
        when(gateway.generateUploadUrl(anyString(), anyString(), anyString(), any())).thenReturn("https://signed-url");
        when(gateway.buildPublicUrl(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> PUBLIC_URL_PREFIX + invocation.getArgument(2));
    }

    // ==================== 申请上传 ====================

    @Test
    @DisplayName("调用方服务名缺失或格式非法时拒绝，且不签名、不落库")
    void rejectsInvalidCallerServiceName() {
        OssException missing = assertThrows(OssException.class,
                () -> imageAppService.generate(generateCmd(), " "));
        assertEquals(OssErrorCode.PARAMETER_INVALID, missing.getErrorCode());
        OssException invalid = assertThrows(OssException.class,
                () -> imageAppService.generate(generateCmd(), "TSP_ACCOUNT"));
        assertEquals(OssErrorCode.PARAMETER_INVALID, invalid.getErrorCode());
        OssException completeInvalid = assertThrows(OssException.class,
                () -> imageAppService.complete(CompleteImageUploadCmd.builder()
                        .objectId("object").callerServiceName("bad/name").build()));
        assertEquals(OssErrorCode.PARAMETER_INVALID, completeInvalid.getErrorCode());
        verifyNoInteractions(gateway, repository);
    }

    @Test
    @DisplayName("签名失败时不插入数据库")
    void signatureFailureDoesNotInsert() {
        when(gateway.generateUploadUrl(anyString(), anyString(), anyString(), any()))
                .thenThrow(new OssException(OssErrorCode.STORAGE_ERROR));
        assertThrows(OssException.class, () -> imageAppService.generate(generateCmd(), SERVICE_NAME));
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("数据库失败时不返回上传地址")
    void databaseFailureDoesNotReturnUrl() {
        when(repository.insert(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> imageAppService.generate(generateCmd(), SERVICE_NAME));
    }

    @Test
    @DisplayName("申请成功保存 PENDING、900 秒过期、唯一对象键，且不持久化上传地址")
    void createsPendingTask() {
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Instant before = Instant.now();
        ImageUploadResult result = imageAppService.generate(generateCmd(), SERVICE_NAME);
        assertNotNull(result.getObjectId());
        assertTrue(OBJECT_KEY_PATTERN.matcher(result.getObjectKey()).matches());
        assertTrue(result.getObjectKey().contains(result.getObjectId()));
        assertEquals("PUT", result.getHttpMethod());
        assertEquals("image/jpeg", result.getContentType());
        assertEquals("https://signed-url", result.getUploadUrl());
        assertTrue(result.getExpiresAt().isAfter(before.plusSeconds(890)));
        assertTrue(result.getExpiresAt().isBefore(before.plusSeconds(910)));

        org.mockito.ArgumentCaptor<ImageObject> captor = org.mockito.ArgumentCaptor.forClass(ImageObject.class);
        verify(repository).insert(captor.capture());
        ImageObject inserted = captor.getValue();
        assertEquals(ImageStatus.PENDING, inserted.getStatus());
        assertEquals(SERVICE_NAME, inserted.getServiceName());
        assertEquals(BUCKET, inserted.getBucketName());
        assertEquals(REGION, inserted.getRegion());
        assertEquals(ImageMimeType.IMAGE_JPEG, inserted.getDeclaredContentType());
        assertEquals(204800L, inserted.getDeclaredSize());
        assertNull(inserted.getActualContentType());
        assertNull(inserted.getActualSize());
        assertEquals(result.getExpiresAt(), inserted.getExpireTime());
    }

    @Test
    @DisplayName("唯一键冲突时重新生成一次 objectId")
    void retriesOnceOnDuplicateKey() {
        when(repository.insert(any()))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ImageUploadResult result = imageAppService.generate(generateCmd(), SERVICE_NAME);
        assertNotNull(result.getObjectId());
        verify(repository, times(2)).insert(any());
        verify(gateway, times(2)).generateUploadUrl(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("连续两次唯一键冲突按内部错误处理，不覆盖旧记录")
    void failsOnRepeatedDuplicateKey() {
        when(repository.insert(any())).thenThrow(new DuplicateKeyException("dup"));
        OssException exception = assertThrows(OssException.class,
                () -> imageAppService.generate(generateCmd(), SERVICE_NAME));
        assertEquals(OssErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        verify(repository, times(2)).insert(any());
    }

    @Test
    @DisplayName("不支持的 MIME、扩展名不匹配、非法大小与非法文件名映射到对应错误码")
    void mapsValidationFailures() {
        assertGenerateFailure(OssErrorCode.UNSUPPORTED_MIME,
                GenerateImageUploadCmd.builder().originalFilename("a.svg")
                        .contentType("image/svg+xml").size(1L).build());
        assertGenerateFailure(OssErrorCode.MIME_EXTENSION_MISMATCH,
                GenerateImageUploadCmd.builder().originalFilename("a.png")
                        .contentType("image/jpeg").size(1L).build());
        assertGenerateFailure(OssErrorCode.INVALID_DECLARED_SIZE,
                GenerateImageUploadCmd.builder().originalFilename("a.jpg")
                        .contentType("image/jpeg").size(0L).build());
        assertGenerateFailure(OssErrorCode.INVALID_DECLARED_SIZE,
                GenerateImageUploadCmd.builder().originalFilename("a.jpg")
                        .contentType("image/jpeg").size(10485761L).build());
        assertGenerateFailure(OssErrorCode.PARAMETER_INVALID,
                GenerateImageUploadCmd.builder().originalFilename("dir/a.jpg")
                        .contentType("image/jpeg").size(1L).build());
    }

    // ==================== 完成确认 ====================

    @Test
    @DisplayName("记录不存在与其他调用方记录统一返回同一个 404")
    void completeHidesOtherCallerObjects() {
        when(repository.findByObjectId("missing")).thenReturn(Optional.empty());
        assertCompleteFailure("missing", SERVICE_NAME, OssErrorCode.OBJECT_NOT_FOUND);
        verifyNoInteractions(gateway);

        when(repository.findByObjectId("object")).thenReturn(Optional.of(pending(Instant.now().plusSeconds(900))));
        assertCompleteFailure("object", OTHER_SERVICE, OssErrorCode.OBJECT_NOT_FOUND);
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("UPLOADED 重复完成不再次 HEAD、不再次更新")
    void completeIsIdempotentWhenUploaded() {
        when(repository.findByObjectId("object")).thenReturn(Optional.of(uploaded()));
        ImageObjectResult result = imageAppService.complete(completeCmd());
        assertEquals(ImageStatus.UPLOADED, result.getStatus());
        assertNotNull(result.getAccessUrl());
        verify(gateway, never()).headObject(anyString(), anyString());
        verify(repository, never()).updatePendingToUploaded(any());
    }

    @Test
    @DisplayName("DELETED 完成拒绝")
    void completeRejectsDeleted() {
        when(repository.findByObjectId("object")).thenReturn(Optional.of(deleted()));
        assertCompleteFailure("object", SERVICE_NAME, OssErrorCode.INVALID_STATE);
        verify(gateway, never()).headObject(anyString(), anyString());
    }

    @Test
    @DisplayName("PENDING 过期拒绝且不超过有效期时不 HEAD")
    void completeRejectsExpired() {
        when(repository.findByObjectId("object")).thenReturn(Optional.of(pending(Instant.now().minusSeconds(1))));
        assertCompleteFailure("object", SERVICE_NAME, OssErrorCode.UPLOAD_EXPIRED);
        verify(gateway, never()).headObject(anyString(), anyString());
    }

    @Test
    @DisplayName("COS 对象不存在时保持 PENDING")
    void completeKeepsPendingWhenObjectMissing() {
        when(repository.findByObjectId("object")).thenReturn(Optional.of(pending(Instant.now().plusSeconds(900))));
        when(gateway.headObject(BUCKET, objectKey())).thenReturn(null);
        assertCompleteFailure("object", SERVICE_NAME, OssErrorCode.IMAGE_NOT_UPLOADED);
        verify(repository, never()).updatePendingToUploaded(any());
    }

    @Test
    @DisplayName("实际大小为 0 或超限时删除非法对象并保持 PENDING")
    void completeDeletesInvalidSizeObject() {
        assertInvalidObjectDeletion(metadata("image/jpeg", 0L), OssErrorCode.ACTUAL_SIZE_INVALID);
        assertInvalidObjectDeletion(metadata("image/jpeg", 10485761L), OssErrorCode.ACTUAL_SIZE_INVALID);
    }

    @Test
    @DisplayName("实际 MIME 不支持或不一致时删除非法对象并保持 PENDING")
    void completeDeletesInvalidMimeObject() {
        assertInvalidObjectDeletion(metadata("image/gif", 100L), OssErrorCode.ACTUAL_MIME_INVALID);
        assertInvalidObjectDeletion(metadata("image/png", 100L), OssErrorCode.ACTUAL_MIME_INVALID);
    }

    @Test
    @DisplayName("非法对象删除失败返回存储错误，不宣称清理成功")
    void invalidObjectDeletionFailure() {
        when(repository.findByObjectId("object")).thenReturn(Optional.of(pending(Instant.now().plusSeconds(900))));
        when(gateway.headObject(BUCKET, objectKey())).thenReturn(metadata("image/jpeg", 0L));
        org.mockito.Mockito.doThrow(new OssException(OssErrorCode.STORAGE_ERROR))
                .when(gateway).deleteObject(BUCKET, objectKey());
        assertCompleteFailure("object", SERVICE_NAME, OssErrorCode.STORAGE_ERROR);
        verify(repository, never()).updatePendingToUploaded(any());
    }

    @Test
    @DisplayName("合法 HEAD 写入实际元数据并更新为 UPLOADED")
    void completeMarksUploaded() {
        ImageObject imageObject = pending(Instant.now().plusSeconds(900));
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject));
        when(gateway.headObject(BUCKET, objectKey())).thenReturn(metadata("image/jpeg", 204800L));
        when(repository.updatePendingToUploaded(imageObject)).thenAnswer(invocation -> {
            ImageObject target = invocation.getArgument(0);
            target.increaseRowVersion(target.getServiceName(), target.getModifyTime());
            return true;
        });
        ImageObjectResult result = imageAppService.complete(completeCmd());
        assertEquals(ImageStatus.UPLOADED, result.getStatus());
        assertEquals(204800L, result.getActualSize());
        assertEquals(ImageMimeType.IMAGE_JPEG, result.getActualContentType());
        assertEquals("etag-value", result.getEtag());
        assertNotNull(result.getAccessUrl());
        assertNotNull(result.getUploadTime());
        assertEquals(2, imageObject.getRowVersion());
    }

    @Test
    @DisplayName("乐观锁冲突后重读，若已 UPLOADED 则幂等返回")
    void completeHandlesConcurrentUpdate() {
        ImageObject pendingObject = pending(Instant.now().plusSeconds(900));
        ImageObject uploadedObject = uploaded();
        when(repository.findByObjectId("object")).thenReturn(Optional.of(pendingObject), Optional.of(uploadedObject));
        when(gateway.headObject(BUCKET, objectKey())).thenReturn(metadata("image/jpeg", 204800L));
        when(repository.updatePendingToUploaded(pendingObject)).thenReturn(false);
        ImageObjectResult result = imageAppService.complete(completeCmd());
        assertEquals(ImageStatus.UPLOADED, result.getStatus());
    }

    @Test
    @DisplayName("乐观锁冲突且重读仍非 UPLOADED 时返回并发冲突")
    void completeReportsConcurrentConflict() {
        ImageObject pendingObject = pending(Instant.now().plusSeconds(900));
        ImageObject stillPending = pending(Instant.now().plusSeconds(900));
        when(repository.findByObjectId("object")).thenReturn(Optional.of(pendingObject), Optional.of(stillPending));
        when(gateway.headObject(BUCKET, objectKey())).thenReturn(metadata("image/jpeg", 204800L));
        when(repository.updatePendingToUploaded(pendingObject)).thenReturn(false);
        assertCompleteFailure("object", SERVICE_NAME, OssErrorCode.CONCURRENT_CONFLICT);
    }

    // ==================== 后台删除 ====================

    @Test
    @DisplayName("记录不存在返回 404")
    void deleteNotFound() {
        when(repository.findByObjectId("missing")).thenReturn(Optional.empty());
        OssException exception = assertThrows(OssException.class, () -> imageAppService.delete("missing", "1"));
        assertEquals(OssErrorCode.OBJECT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("DELETED 幂等成功，不删除 COS、不更新数据库")
    void deleteIsIdempotentWhenDeleted() {
        when(repository.findByObjectId("object")).thenReturn(Optional.of(deleted()));
        imageAppService.delete("object", "1");
        verify(gateway, never()).deleteObject(anyString(), anyString());
        verify(repository, never()).updateUploadedToDeleted(any());
    }

    @Test
    @DisplayName("PENDING 记录可由管理人员清理：COS 幂等删除后置为 DELETED（BUGFIX-001）")
    void deleteCleansPendingRecord() {
        ImageObject imageObject = pending(Instant.now().plusSeconds(900));
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject));
        when(repository.updatePendingToDeleted(imageObject)).thenReturn(true);
        imageAppService.delete("object", "1001");
        verify(gateway).deleteObject(BUCKET, objectKey());
        verify(repository).updatePendingToDeleted(imageObject);
        verify(repository, never()).updateUploadedToDeleted(any());
        assertTrue(imageObject.isDeleted());
        assertEquals("1001", imageObject.getDeleteBy());
    }

    @Test
    @DisplayName("PENDING 清理时 COS 删除失败则保持 PENDING")
    void deletePendingKeepsPendingOnCosFailure() {
        ImageObject imageObject = pending(Instant.now().plusSeconds(900));
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject));
        org.mockito.Mockito.doThrow(new OssException(OssErrorCode.STORAGE_ERROR))
                .when(gateway).deleteObject(BUCKET, objectKey());
        OssException exception = assertThrows(OssException.class, () -> imageAppService.delete("object", "1001"));
        assertEquals(OssErrorCode.STORAGE_ERROR, exception.getErrorCode());
        assertTrue(imageObject.isPending());
        verify(repository, never()).updatePendingToDeleted(any());
    }

    @Test
    @DisplayName("PENDING 清理数据库冲突后重读 DELETED 视为成功")
    void deletePendingRetrySucceedsWhenAlreadyDeleted() {
        ImageObject imageObject = pending(Instant.now().plusSeconds(900));
        when(repository.findByObjectId("object"))
                .thenReturn(Optional.of(imageObject), Optional.of(deleted()));
        when(repository.updatePendingToDeleted(imageObject)).thenReturn(false);
        imageAppService.delete("object", "1001");
        verify(gateway).deleteObject(BUCKET, objectKey());
    }

    @Test
    @DisplayName("PENDING 清理数据库冲突且重读仍为 PENDING 时返回并发冲突")
    void deletePendingReportsConcurrentConflict() {
        ImageObject imageObject = pending(Instant.now().plusSeconds(900));
        when(repository.findByObjectId("object"))
                .thenReturn(Optional.of(imageObject), Optional.of(pending(Instant.now().plusSeconds(900))));
        when(repository.updatePendingToDeleted(imageObject)).thenReturn(false);
        OssException exception = assertThrows(OssException.class, () -> imageAppService.delete("object", "1001"));
        assertEquals(OssErrorCode.CONCURRENT_CONFLICT, exception.getErrorCode());
    }

    @Test
    @DisplayName("COS 删除失败保持 UPLOADED")
    void deleteKeepsUploadedOnCosFailure() {
        ImageObject imageObject = uploaded();
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject));
        org.mockito.Mockito.doThrow(new OssException(OssErrorCode.STORAGE_ERROR))
                .when(gateway).deleteObject(BUCKET, objectKey());
        OssException exception = assertThrows(OssException.class, () -> imageAppService.delete("object", "1"));
        assertEquals(OssErrorCode.STORAGE_ERROR, exception.getErrorCode());
        assertTrue(imageObject.isUploaded());
        verify(repository, never()).updateUploadedToDeleted(any());
    }

    @Test
    @DisplayName("COS 成功、数据库失败返回失败并保持 UPLOADED 语义")
    void deleteFailsWhenDatabaseUpdateFails() {
        ImageObject imageObject = uploaded();
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject));
        when(repository.updateUploadedToDeleted(imageObject))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> imageAppService.delete("object", "1"));
        verify(gateway).deleteObject(BUCKET, objectKey());
    }

    @Test
    @DisplayName("COS 成功、数据库更新成功时写入 DELETED 与审计人员")
    void deleteMarksDeleted() {
        ImageObject imageObject = uploaded();
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject));
        when(repository.updateUploadedToDeleted(imageObject)).thenAnswer(invocation -> {
            ImageObject target = invocation.getArgument(0);
            target.increaseRowVersion(target.getDeleteBy(), target.getDeleteTime());
            return true;
        });
        imageAppService.delete("object", "1001");
        verify(gateway).deleteObject(BUCKET, objectKey());
        assertTrue(imageObject.isDeleted());
        assertEquals("1001", imageObject.getDeleteBy());
        assertEquals(2, imageObject.getRowVersion());
    }

    @Test
    @DisplayName("删除重试时数据库冲突后重读 DELETED 视为成功")
    void deleteRetrySucceedsWhenAlreadyDeleted() {
        ImageObject imageObject = uploaded();
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject), Optional.of(deleted()));
        when(repository.updateUploadedToDeleted(imageObject)).thenReturn(false);
        imageAppService.delete("object", "1001");
        verify(gateway).deleteObject(BUCKET, objectKey());
    }

    @Test
    @DisplayName("删除遇到 PENDING 替换任务时先取消任务再删除图片")
    void deleteCancelsPendingReplacement() {
        ImageObject imageObject = uploaded();
        ImageReplacement replacement = activeReplacement(true);
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject));
        when(replacementRepository.findActiveByObjectId("object")).thenReturn(Optional.of(replacement));
        when(replacementRepository.updatePendingToCancelled(replacement)).thenReturn(true);
        when(repository.updateUploadedToDeleted(imageObject)).thenReturn(true);
        imageAppService.delete("object", "1001");
        verify(replacementRepository).updatePendingToCancelled(replacement);
        verify(gateway).deleteObject(BUCKET, objectKey());
        assertTrue(replacement.isCancelled());
    }

    @Test
    @DisplayName("删除遇到 PROMOTING 替换任务返回 OSS-409004 且不删 COS")
    void deleteRejectsPromotingReplacement() {
        when(repository.findByObjectId("object")).thenReturn(Optional.of(uploaded()));
        when(replacementRepository.findActiveByObjectId("object"))
                .thenReturn(Optional.of(activeReplacement(false)));
        OssException exception = assertThrows(OssException.class, () -> imageAppService.delete("object", "1001"));
        assertEquals(OssErrorCode.REPLACEMENT_CONFLICT, exception.getErrorCode());
        verify(gateway, never()).deleteObject(anyString(), anyString());
        verify(repository, never()).updateUploadedToDeleted(any());
    }

    @Test
    @DisplayName("取消 PENDING 替换任务条件更新失败返回 OSS-409004")
    void deleteReportsReplacementCancelConflict() {
        when(repository.findByObjectId("object")).thenReturn(Optional.of(uploaded()));
        ImageReplacement replacement = activeReplacement(true);
        when(replacementRepository.findActiveByObjectId("object")).thenReturn(Optional.of(replacement));
        when(replacementRepository.updatePendingToCancelled(replacement)).thenReturn(false);
        OssException exception = assertThrows(OssException.class, () -> imageAppService.delete("object", "1001"));
        assertEquals(OssErrorCode.REPLACEMENT_CONFLICT, exception.getErrorCode());
        verify(gateway, never()).deleteObject(anyString(), anyString());
    }

    @Test
    @DisplayName("删除数据库冲突且重读仍为 UPLOADED 时返回并发冲突")
    void deleteReportsConcurrentConflict() {
        ImageObject imageObject = uploaded();
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject), Optional.of(uploaded()));
        when(repository.updateUploadedToDeleted(imageObject)).thenReturn(false);
        OssException exception = assertThrows(OssException.class, () -> imageAppService.delete("object", "1001"));
        assertEquals(OssErrorCode.CONCURRENT_CONFLICT, exception.getErrorCode());
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("详情：UPLOADED 返回 accessUrl，PENDING/DELETED 不返回")
    void detailAccessUrlOnlyWhenUploaded() {
        when(repository.findByObjectId("uploaded")).thenReturn(Optional.of(uploaded()));
        when(repository.findByObjectId("pending")).thenReturn(Optional.of(pending(Instant.now().plusSeconds(900))));
        when(repository.findByObjectId("deleted")).thenReturn(Optional.of(deleted()));
        assertNotNull(imageAppService.detail("uploaded").getAccessUrl());
        assertNull(imageAppService.detail("pending").getAccessUrl());
        assertNull(imageAppService.detail("deleted").getAccessUrl());
    }

    @Test
    @DisplayName("详情：不存在返回 404")
    void detailNotFound() {
        when(repository.findByObjectId("missing")).thenReturn(Optional.empty());
        OssException exception = assertThrows(OssException.class, () -> imageAppService.detail("missing"));
        assertEquals(OssErrorCode.OBJECT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("列表：状态解析与筛选条件传递")
    void searchPassesCriteria() {
        when(repository.search(any())).thenReturn(List.of(uploaded()));
        List<ImageObjectResult> results = imageAppService.search(ImageObjectQuery.builder()
                .serviceName(SERVICE_NAME)
                .status("deleted")
                .contentType("image/jpeg")
                .objectKey(objectKey())
                .originalFilename("avatar")
                .uploadBeginTime(Instant.parse("2026-11-01T00:00:00Z"))
                .uploadEndTime(Instant.parse("2026-11-30T00:00:00Z"))
                .build());
        assertEquals(1, results.size());
        org.mockito.ArgumentCaptor<net.hwyz.iov.cloud.edd.oss.service.domain.model.query.ImageObjectSearchCriteria>
                captor = org.mockito.ArgumentCaptor.forClass(
                net.hwyz.iov.cloud.edd.oss.service.domain.model.query.ImageObjectSearchCriteria.class);
        verify(repository).search(captor.capture());
        assertEquals(ImageStatus.DELETED, captor.getValue().getStatus());
        assertEquals(SERVICE_NAME, captor.getValue().getServiceName());
        assertEquals("image/jpeg", captor.getValue().getContentType());
        assertEquals(objectKey(), captor.getValue().getObjectKey());
        assertEquals("avatar", captor.getValue().getOriginalFilenamePrefix());
        assertFalse(results.isEmpty());
    }

    @Test
    @DisplayName("列表：缺省状态传 null，由仓储默认 PENDING + UPLOADED")
    void searchDefaultStatusIsNull() {
        when(repository.search(any())).thenReturn(List.of());
        imageAppService.search(ImageObjectQuery.builder().build());
        org.mockito.ArgumentCaptor<net.hwyz.iov.cloud.edd.oss.service.domain.model.query.ImageObjectSearchCriteria>
                captor = org.mockito.ArgumentCaptor.forClass(
                net.hwyz.iov.cloud.edd.oss.service.domain.model.query.ImageObjectSearchCriteria.class);
        verify(repository).search(captor.capture());
        assertNull(captor.getValue().getStatus());
    }

    @Test
    @DisplayName("列表：非法状态返回参数错误")
    void searchRejectsInvalidStatus() {
        OssException exception = assertThrows(OssException.class,
                () -> imageAppService.search(ImageObjectQuery.builder().status("UNKNOWN").build()));
        assertEquals(OssErrorCode.PARAMETER_INVALID, exception.getErrorCode());
    }

    // ==================== helpers ====================

    private GenerateImageUploadCmd generateCmd() {
        return GenerateImageUploadCmd.builder()
                .originalFilename("avatar.jpg")
                .contentType("image/jpeg")
                .size(204800L)
                .build();
    }

    private CompleteImageUploadCmd completeCmd() {
        return CompleteImageUploadCmd.builder().objectId("object").callerServiceName(SERVICE_NAME).build();
    }

    private void assertGenerateFailure(OssErrorCode expected, GenerateImageUploadCmd cmd) {
        OssException exception = assertThrows(OssException.class, () -> imageAppService.generate(cmd, SERVICE_NAME));
        assertEquals(expected, exception.getErrorCode());
        verify(repository, never()).insert(any());
        verify(gateway, never()).generateUploadUrl(anyString(), anyString(), anyString(), any());
    }

    private void assertCompleteFailure(String objectId, String caller, OssErrorCode expected) {
        OssException exception = assertThrows(OssException.class, () -> imageAppService.complete(
                CompleteImageUploadCmd.builder().objectId(objectId).callerServiceName(caller).build()));
        assertEquals(expected, exception.getErrorCode());
    }

    private void assertInvalidObjectDeletion(CosObjectMetadata metadata, OssErrorCode expected) {
        org.mockito.Mockito.clearInvocations(gateway, repository);
        ImageObject imageObject = pending(Instant.now().plusSeconds(900));
        when(repository.findByObjectId("object")).thenReturn(Optional.of(imageObject));
        when(gateway.headObject(BUCKET, objectKey())).thenReturn(metadata);
        assertCompleteFailure("object", SERVICE_NAME, expected);
        verify(gateway).deleteObject(BUCKET, objectKey());
        verify(repository, never()).updatePendingToUploaded(any());
        assertTrue(imageObject.isPending());
    }

    private String objectKey() {
        return "images/tsp-account/2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3.jpg";
    }

    private CosObjectMetadata metadata(String contentType, long size) {
        return CosObjectMetadata.builder().contentType(contentType).contentLength(size).etag("etag-value").build();
    }

    private ImageObject pending(Instant expireTime) {
        return ImageObject.pending(UUID.randomUUID().toString(), StorageProvider.TENCENT_COS, REGION, BUCKET,
                objectKey(), SERVICE_NAME, "avatar.jpg", ImageMimeType.IMAGE_JPEG, 204800L,
                ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE, expireTime, SERVICE_NAME, Instant.now());
    }

    private ImageObject uploaded() {
        ImageObject imageObject = pending(Instant.now().plusSeconds(900));
        imageObject.markUploaded(ImageMimeType.IMAGE_JPEG, 204800L, "etag-value", Instant.now());
        return imageObject;
    }

    private ImageObject deleted() {
        ImageObject imageObject = uploaded();
        imageObject.markDeleted("1001", Instant.now());
        return imageObject;
    }

    private ImageReplacement activeReplacement(boolean pending) {
        ImageReplacement replacement = ImageReplacement.pending(UUID.randomUUID().toString(), "object",
                "images/edd-oss/replacements/object/replacement.jpg",
                "images/edd-oss/replacement-backups/object/replacement.jpg", "avatar-v2.jpg",
                ImageMimeType.IMAGE_JPEG, 102400L, Instant.now().plusSeconds(900), "1001", Instant.now());
        if (!pending) {
            replacement.startPromoting(ImageMimeType.IMAGE_JPEG, 102400L, "etag", "1001", Instant.now());
        }
        return replacement;
    }

}
