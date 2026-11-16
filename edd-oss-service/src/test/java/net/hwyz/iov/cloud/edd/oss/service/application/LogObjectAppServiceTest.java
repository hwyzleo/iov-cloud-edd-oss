package net.hwyz.iov.cloud.edd.oss.service.application;

import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteLogObjectUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateLogObjectUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.query.LogObjectQuery;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectDownloadUrlResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectHeadMetadata;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectStorageGateway;
import net.hwyz.iov.cloud.edd.oss.service.application.service.LogObjectAppService;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.LogObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogContentType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogFailureReason;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogUploadState;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.query.LogObjectSearchCriteria;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.LogObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.CosProperties;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.LogProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 日志对象应用服务单元测试（EDD-OSS-DSN-CR-003 §8、§9）。
 *
 * @author hwyz_leo
 */
class LogObjectAppServiceTest {

    private static final String CALLER = "tsp-ota";
    private static final String OTHER_CALLER = "tsp-other";
    private static final String BUCKET = "iov-private-log-1253442587";
    private static final String REGION = "ap-shanghai";
    private static final String SHA256 = "a".repeat(64);
    private static final String OBJECT_KEY =
            "logs/tsp-ota/OTA_UPGRADE_LOG/20261115/2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3";

    private LogObjectRepository repository;
    private ObjectStorageGateway gateway;
    private LogObjectAppService logObjectAppService;

    @BeforeEach
    void setUp() {
        repository = mock(LogObjectRepository.class);
        gateway = mock(ObjectStorageGateway.class);
        CosProperties cosProperties = new CosProperties();
        cosProperties.setRegion(REGION);
        cosProperties.setBucket("iov-public-1253442587");
        cosProperties.setSecretId("test-secret-id");
        cosProperties.setSecretKey("test-secret-key");
        LogProperties logProperties = new LogProperties();
        logProperties.setBucket(BUCKET);
        logObjectAppService = new LogObjectAppService(repository, gateway, cosProperties, logProperties);
        when(gateway.generateUploadUrlWithMetadata(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("https://signed.example.com/put");
        when(repository.insert(any(LogObject.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ==================== 申请授权 ====================

    @Test
    @DisplayName("申请授权返回 objectId/objectKey/PUT URL/两个强制头/原 expiresAt/maxSizeBytes")
    void generateAuthorizedUpload() {
        when(repository.findByRequest(CALLER, LogBusinessType.OTA_UPGRADE_LOG, "request-1"))
                .thenReturn(Optional.empty());
        LogObjectUploadResult result = logObjectAppService.generate(validCmd(), CALLER);
        assertNotNull(result.getObjectId());
        assertTrue(result.getObjectKey().matches("logs/tsp-ota/OTA_UPGRADE_LOG/\\d{8}/"
                + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertEquals("https://signed.example.com/put", result.getUploadUrl());
        assertEquals("application/gzip", result.getHeaders().get(LogObjectAppService.HEADER_CONTENT_TYPE));
        assertEquals(SHA256, result.getHeaders().get(LogObjectAppService.HEADER_SHA256));
        assertEquals(536870912L, result.getMaxSizeBytes());
        long effectiveTtl = Duration.between(Instant.now(), result.getExpiresAt()).getSeconds();
        assertTrue(effectiveTtl >= 895 && effectiveTtl <= 900);
        ArgumentCaptor<LogObject> captor = ArgumentCaptor.forClass(LogObject.class);
        verify(repository).insert(captor.capture());
        assertEquals(LogUploadState.AUTHORIZED, captor.getValue().getUploadState());
        assertEquals(BUCKET, captor.getValue().getBucketName());
        assertEquals(CALLER, captor.getValue().getCallerService());
    }

    @Test
    @DisplayName("同 requestId 同参数复用 objectId/objectKey/expiresAt，不延期不换 key")
    void reuseIdempotentAuthorization() {
        LogObject existing = logObject(Instant.now());
        when(repository.findByRequest(CALLER, LogBusinessType.OTA_UPGRADE_LOG, "request-1"))
                .thenReturn(Optional.of(existing));
        LogObjectUploadResult result = logObjectAppService.generate(validCmd(), CALLER);
        assertEquals(existing.getObjectId(), result.getObjectId());
        assertEquals(existing.getObjectKey(), result.getObjectKey());
        assertEquals(existing.getExpiresAt(), result.getExpiresAt());
        verify(gateway).generateUploadUrlWithMetadata(eq(BUCKET), eq(existing.getObjectKey()),
                eq("application/gzip"), eq(SHA256), eq(existing.getExpiresAt()));
        verify(repository, never()).insert(any(LogObject.class));
    }

    @Test
    @DisplayName("同 requestId 参数不一致返回 OSS-811023，且不签名、不插入")
    void rejectIdempotencyConflict() {
        LogObject existing = logObject(Instant.now());
        when(repository.findByRequest(CALLER, LogBusinessType.OTA_UPGRADE_LOG, "request-1"))
                .thenReturn(Optional.of(existing));
        GenerateLogObjectUploadCmd cmd = validCmd();
        cmd.setSha256Hex("b".repeat(64));
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.generate(cmd, CALLER));
        assertEquals(OssErrorCode.LOG_IDEMPOTENCY_CONFLICT, exception.getErrorCode());
        verifyNoInteractions(gateway);
        verify(repository, never()).insert(any(LogObject.class));
    }

    @Test
    @DisplayName("终态对象不得重新发放 PUT 凭证，返回 OSS-811022")
    void rejectTerminalStateAuthorization() {
        LogObject uploaded = logObject(Instant.now());
        uploaded.markUploaded(1024L, SHA256, "\"etag\"", "application/gzip", Instant.now());
        when(repository.findByRequest(CALLER, LogBusinessType.OTA_UPGRADE_LOG, "request-1"))
                .thenReturn(Optional.of(uploaded));
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.generate(validCmd(), CALLER));
        assertEquals(OssErrorCode.LOG_STATE_CONFLICT, exception.getErrorCode());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("同 requestId 对应已过期 AUTHORIZED：惰性转 EXPIRED 并返回 OSS-811022")
    void rejectExpiredAuthorization() {
        LogObject expired = logObject(Instant.now().minusSeconds(1000));
        when(repository.findByRequest(CALLER, LogBusinessType.OTA_UPGRADE_LOG, "request-1"))
                .thenReturn(Optional.of(expired));
        when(repository.updateAuthorizedToExpired(expired)).thenReturn(true);
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.generate(validCmd(), CALLER));
        assertEquals(OssErrorCode.LOG_STATE_CONFLICT, exception.getErrorCode());
        assertEquals(LogUploadState.EXPIRED, expired.getUploadState());
        verify(repository).updateAuthorizedToExpired(expired);
    }

    @Test
    @DisplayName("并发 UK 冲突后重读同参记录并复用原授权")
    void duplicateKeyReloadsExistingRequest() {
        LogObject existing = logObject(Instant.now());
        when(repository.findByRequest(CALLER, LogBusinessType.OTA_UPGRADE_LOG, "request-1"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(repository.insert(any(LogObject.class))).thenThrow(new DuplicateKeyException("uk_log_request"));
        LogObjectUploadResult result = logObjectAppService.generate(validCmd(), CALLER);
        assertEquals(existing.getObjectId(), result.getObjectId());
        assertEquals(existing.getExpiresAt(), result.getExpiresAt());
    }

    // ==================== complete ====================

    @Test
    @DisplayName("HEAD 全部一致才置为 UPLOADED，并保存实际值")
    void completeUploadedWhenAllMetadataMatches() {
        LogObject logObject = logObject(Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        when(repository.recordCompleteReceived(any(LogObject.class))).thenReturn(true);
        when(repository.updateAuthorizedToUploaded(logObject)).thenReturn(true);
        when(gateway.headObjectWithMetadata(BUCKET, OBJECT_KEY)).thenReturn(metadata(1024L, "application/gzip",
                SHA256));
        LogObjectResult result = logObjectAppService.complete(completeCmd(CALLER));
        assertEquals(LogUploadState.UPLOADED.name(), result.getUploadState());
        assertEquals(1024L, result.getActualSizeBytes());
        assertEquals(SHA256, result.getActualSha256Hex());
        assertNotNull(result.getUploadedAt());
        verify(gateway).headObjectWithMetadata(BUCKET, OBJECT_KEY);
    }

    @Test
    @DisplayName("物理对象不存在：置 FAILED 并返回 OSS-811019")
    void completeObjectMissing() {
        LogObject logObject = logObject(Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        when(repository.updateAuthorizedToFailed(logObject)).thenReturn(true);
        when(gateway.headObjectWithMetadata(BUCKET, OBJECT_KEY)).thenReturn(null);
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.complete(completeCmd(CALLER)));
        assertEquals(OssErrorCode.LOG_OBJECT_MISSING, exception.getErrorCode());
        assertEquals(LogUploadState.FAILED, logObject.getUploadState());
        assertEquals(LogFailureReason.OBJECT_NOT_FOUND, logObject.getFailureReason());
    }

    @Test
    @DisplayName("大小不符：置 FAILED、保存观察值并返回 OSS-811020")
    void completeSizeMismatch() {
        LogObject logObject = logObject(Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        when(repository.updateAuthorizedToFailed(logObject)).thenReturn(true);
        when(gateway.headObjectWithMetadata(BUCKET, OBJECT_KEY)).thenReturn(metadata(2048L,
                "application/gzip", SHA256));
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.complete(completeCmd(CALLER)));
        assertEquals(OssErrorCode.LOG_SIZE_MISMATCH, exception.getErrorCode());
        assertEquals(LogFailureReason.SIZE_MISMATCH, logObject.getFailureReason());
        assertEquals(2048L, logObject.getActualSize());
    }

    @Test
    @DisplayName("Content-Type 不符：置 FAILED 并返回 OSS-811025")
    void completeContentTypeMismatch() {
        LogObject logObject = logObject(Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        when(repository.updateAuthorizedToFailed(logObject)).thenReturn(true);
        when(gateway.headObjectWithMetadata(BUCKET, OBJECT_KEY)).thenReturn(metadata(1024L,
                "application/zip", SHA256));
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.complete(completeCmd(CALLER)));
        assertEquals(OssErrorCode.LOG_CONTENT_TYPE_MISMATCH, exception.getErrorCode());
        assertEquals(LogFailureReason.CONTENT_TYPE_MISMATCH, logObject.getFailureReason());
    }

    @Test
    @DisplayName("摘要元数据缺失或不符：置 FAILED 并返回 OSS-811021")
    void completeDigestMismatch() {
        LogObject logObject = logObject(Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        when(repository.updateAuthorizedToFailed(logObject)).thenReturn(true);
        when(gateway.headObjectWithMetadata(BUCKET, OBJECT_KEY)).thenReturn(metadata(1024L,
                "application/gzip", null));
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.complete(completeCmd(CALLER)));
        assertEquals(OssErrorCode.LOG_DIGEST_MISMATCH, exception.getErrorCode());
        assertEquals(LogFailureReason.DIGEST_MISMATCH, logObject.getFailureReason());
    }

    @Test
    @DisplayName("HEAD 临时故障返回 OSS-811017，不置 UPLOADED/FAILED，可用原 objectId 重试")
    void completeStorageFailureKeepsNonTerminalState() {
        LogObject logObject = logObject(Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        when(gateway.headObjectWithMetadata(BUCKET, OBJECT_KEY))
                .thenThrow(new OssException(OssErrorCode.STORAGE_ERROR, "timeout"));
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.complete(completeCmd(CALLER)));
        assertEquals(OssErrorCode.STORAGE_ERROR, exception.getErrorCode());
        assertEquals(LogUploadState.AUTHORIZED, logObject.getUploadState());
        verify(repository, never()).updateAuthorizedToUploaded(any(LogObject.class));
        verify(repository, never()).updateAuthorizedToFailed(any(LogObject.class));
    }

    @Test
    @DisplayName("已 UPLOADED 重复 complete 幂等返回，不再 HEAD")
    void completeUploadedIsIdempotent() {
        LogObject logObject = logObject(Instant.now());
        logObject.markUploaded(1024L, SHA256, "\"etag\"", "application/gzip", Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        LogObjectResult result = logObjectAppService.complete(completeCmd(CALLER));
        assertEquals(LogUploadState.UPLOADED.name(), result.getUploadState());
        verify(gateway, never()).headObjectWithMetadata(anyString(), anyString());
    }

    @Test
    @DisplayName("已 FAILED 重复 complete 返回原确定性失败码，不再 HEAD")
    void completeFailedIsIdempotent() {
        LogObject logObject = logObject(Instant.now());
        logObject.markFailed(LogFailureReason.SIZE_MISMATCH, 2048L, SHA256, null, Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.complete(completeCmd(CALLER)));
        assertEquals(OssErrorCode.LOG_SIZE_MISMATCH, exception.getErrorCode());
        verify(gateway, never()).headObjectWithMetadata(anyString(), anyString());
    }

    @Test
    @DisplayName("已过期 AUTHORIZED 收到 complete：不 HEAD，转 EXPIRED 并返回 OSS-811022")
    void completeExpiredAuthorization() {
        LogObject logObject = logObject(Instant.now().minusSeconds(1000));
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        when(repository.updateAuthorizedToExpired(logObject)).thenReturn(true);
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.complete(completeCmd(CALLER)));
        assertEquals(OssErrorCode.LOG_STATE_CONFLICT, exception.getErrorCode());
        assertEquals(LogUploadState.EXPIRED, logObject.getUploadState());
        verify(gateway, never()).headObjectWithMetadata(anyString(), anyString());
    }

    @Test
    @DisplayName("跨调用方 complete 返回 OSS-811024 且不 HEAD、不泄露元数据")
    void completeRejectsOtherCaller() {
        LogObject logObject = logObject(Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.complete(completeCmd(OTHER_CALLER)));
        assertEquals(OssErrorCode.LOG_NOT_OWNER, exception.getErrorCode());
        verify(gateway, never()).headObjectWithMetadata(anyString(), anyString());
    }

    @Test
    @DisplayName("登记不存在返回 OSS-811018，不执行存储操作")
    void completeRejectsMissingRegistration() {
        when(repository.findByObjectId("object-id")).thenReturn(Optional.empty());
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.complete(completeCmd(CALLER)));
        assertEquals(OssErrorCode.LOG_OBJECT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(gateway);
    }

    // ==================== 查询与下载 ====================

    @Test
    @DisplayName("只有本人 UPLOADED 对象可签发短期下载地址")
    void downloadUrlOnlyForUploaded() {
        LogObject logObject = logObject(Instant.now());
        logObject.markUploaded(1024L, SHA256, "\"etag\"", "application/gzip", Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        when(gateway.generateDownloadUrl(eq(BUCKET), eq(OBJECT_KEY), any()))
                .thenReturn("https://signed.example.com/get");
        LogObjectDownloadUrlResult result = logObjectAppService.downloadUrl("object-id", CALLER);
        assertEquals("https://signed.example.com/get", result.getDownloadUrl());
        long downloadTtl = Duration.between(Instant.now(), result.getExpiresAt()).getSeconds();
        assertTrue(downloadTtl >= 295 && downloadTtl <= 300);
    }

    @Test
    @DisplayName("非 UPLOADED 对象拒绝下载地址申请")
    void downloadUrlRejectsNonUploaded() {
        LogObject logObject = logObject(Instant.now());
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        OssException exception = assertThrows(OssException.class,
                () -> logObjectAppService.downloadUrl("object-id", CALLER));
        assertEquals(OssErrorCode.LOG_STATE_CONFLICT, exception.getErrorCode());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("详情访问过期的 AUTHORIZED 记录时先惰性转 EXPIRED")
    void detailLazilyExpiresAuthorization() {
        LogObject logObject = logObject(Instant.now().minusSeconds(1000));
        when(repository.findByObjectId("object-id")).thenReturn(Optional.of(logObject));
        when(repository.updateAuthorizedToExpired(logObject)).thenReturn(true);
        LogObjectResult result = logObjectAppService.detail("object-id", CALLER);
        assertEquals(LogUploadState.EXPIRED.name(), result.getUploadState());
    }

    @Test
    @DisplayName("分页查询强制注入调用方并传递过滤条件")
    void searchEnforcesCallerAndFilters() {
        LogObject logObject = logObject(Instant.now());
        when(repository.search(any(LogObjectSearchCriteria.class))).thenReturn(List.of(logObject));
        Instant begin = Instant.parse("2026-11-01T00:00:00Z");
        Instant end = Instant.parse("2026-12-01T00:00:00Z");
        LogObjectQuery query = LogObjectQuery.builder()
                .callerService(CALLER)
                .businessType("OTA_UPGRADE_LOG")
                .businessId("task-1")
                .state("AUTHORIZED")
                .beginTime(begin)
                .endTime(end)
                .build();
        List<LogObjectResult> results = logObjectAppService.search(query);
        assertEquals(1, results.size());
        ArgumentCaptor<LogObjectSearchCriteria> captor = ArgumentCaptor.forClass(LogObjectSearchCriteria.class);
        verify(repository).search(captor.capture());
        assertEquals(CALLER, captor.getValue().getCallerService());
        assertEquals(LogBusinessType.OTA_UPGRADE_LOG, captor.getValue().getBusinessType());
        assertEquals(LogUploadState.AUTHORIZED, captor.getValue().getState());
        assertEquals(begin, captor.getValue().getBeginTime());
        assertEquals(end, captor.getValue().getEndTime());
    }

    @Test
    @DisplayName("非法状态或时间范围返回 OSS-811001，不查询数据库")
    void searchRejectsInvalidFilters() {
        LogObjectQuery invalidState = LogObjectQuery.builder().callerService(CALLER).state("UNKNOWN").build();
        assertEquals(OssErrorCode.PARAMETER_INVALID,
                assertThrows(OssException.class, () -> logObjectAppService.search(invalidState)).getErrorCode());
        LogObjectQuery invalidRange = LogObjectQuery.builder().callerService(CALLER)
                .beginTime(Instant.parse("2026-12-01T00:00:00Z"))
                .endTime(Instant.parse("2026-11-01T00:00:00Z")).build();
        assertEquals(OssErrorCode.PARAMETER_INVALID,
                assertThrows(OssException.class, () -> logObjectAppService.search(invalidRange)).getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("过期扫描使用配置批量上限")
    void expireAuthorizedBatch() {
        when(repository.expireAuthorizedBatch(any(Instant.class), anyInt())).thenReturn(3);
        assertEquals(3, logObjectAppService.expireAuthorizedBatch(Instant.now()));
        verify(repository).expireAuthorizedBatch(any(Instant.class), eq(500));
    }

    private GenerateLogObjectUploadCmd validCmd() {
        return GenerateLogObjectUploadCmd.builder()
                .businessType("OTA_UPGRADE_LOG")
                .businessId("task-1")
                .businessSubId("execution-1")
                .requestId("request-1")
                .fileName("upgrade.log.gz")
                .contentType("application/gzip")
                .sizeBytes(1024L)
                .sha256Hex(SHA256)
                .privacyLevel("INTERNAL")
                .ttlSeconds(900)
                .build();
    }

    private CompleteLogObjectUploadCmd completeCmd(String caller) {
        return CompleteLogObjectUploadCmd.builder().objectId("object-id").callerService(caller).build();
    }

    private ObjectHeadMetadata metadata(long size, String contentType, String sha256) {
        return ObjectHeadMetadata.builder()
                .contentLength(size)
                .contentType(contentType)
                .etag("\"etag\"")
                .sha256Hex(sha256)
                .build();
    }

    private LogObject logObject(Instant authorizedAt) {
        return LogObject.authorized("object-id", StorageProvider.TENCENT_COS, REGION, BUCKET, CALLER,
                LogBusinessType.OTA_UPGRADE_LOG, "task-1", "execution-1", "request-1", "upgrade.log.gz",
                OBJECT_KEY, LogContentType.GZIP, 1024L, SHA256, "INTERNAL", authorizedAt.plusSeconds(900),
                authorizedAt);
    }

}
