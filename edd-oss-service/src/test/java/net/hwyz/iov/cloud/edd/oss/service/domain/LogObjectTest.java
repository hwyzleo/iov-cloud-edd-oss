package net.hwyz.iov.cloud.edd.oss.service.domain;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.LogObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogContentType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogFailureReason;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogUploadState;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志对象聚合单元测试（EDD-OSS-DSN-CR-003 §4.1、§4.2）。
 *
 * @author hwyz_leo
 */
class LogObjectTest {

    private static final String SHA256 = "a".repeat(64);
    private static final Instant AUTHORIZED_AT = Instant.parse("2026-11-15T10:00:00Z");
    private static final Instant EXPIRES_AT = AUTHORIZED_AT.plusSeconds(900);

    @Test
    @DisplayName("授权登记初始为 AUTHORIZED，且不包含上传成功事实")
    void authorizedInitialState() {
        LogObject logObject = authorized();
        assertEquals(LogUploadState.AUTHORIZED, logObject.getUploadState());
        assertNull(logObject.getUploadedAt());
        assertNull(logObject.getActualSize());
        assertNull(logObject.getActualSha256());
        assertNull(logObject.getReceivedAt());
        assertEquals(1, logObject.getRowVersion());
        assertTrue(logObject.belongsTo("tsp-ota"));
        assertFalse(logObject.belongsTo("tsp-other"));
    }

    @Test
    @DisplayName("receivedAt 仅首次写入，后续 complete 不覆盖")
    void receivedAtOnlyWrittenOnce() {
        LogObject logObject = authorized();
        Instant first = AUTHORIZED_AT.plusSeconds(10);
        Instant second = AUTHORIZED_AT.plusSeconds(20);
        assertTrue(logObject.recordCompleteReceived(first));
        assertEquals(first, logObject.getReceivedAt());
        assertFalse(logObject.recordCompleteReceived(second));
        assertEquals(first, logObject.getReceivedAt());
    }

    @Test
    @DisplayName("HEAD 全部一致才可置为 UPLOADED，并保存实际元数据")
    void markUploadedWhenMetadataMatches() {
        LogObject logObject = authorized();
        logObject.recordCompleteReceived(AUTHORIZED_AT.plusSeconds(10));
        logObject.markUploaded(1024L, SHA256, "\"etag\"", "application/gzip",
                AUTHORIZED_AT.plusSeconds(20));
        assertEquals(LogUploadState.UPLOADED, logObject.getUploadState());
        assertEquals(1024L, logObject.getActualSize());
        assertEquals(SHA256, logObject.getActualSha256());
        assertEquals("\"etag\"", logObject.getEtag());
        assertEquals(AUTHORIZED_AT.plusSeconds(20), logObject.getUploadedAt());
        assertTrue(logObject.isUploaded());
    }

    @Test
    @DisplayName("大小、摘要、Content-Type 任一不符都不得置为 UPLOADED")
    void markUploadedRejectsMismatch() {
        LogObject sizeMismatch = authorized();
        assertThrows(IllegalStateException.class, () -> sizeMismatch.markUploaded(1025L, SHA256, "etag",
                "application/gzip", AUTHORIZED_AT.plusSeconds(10)));

        LogObject digestMismatch = authorized();
        assertThrows(IllegalStateException.class, () -> digestMismatch.markUploaded(1024L, "b".repeat(64), "etag",
                "application/gzip", AUTHORIZED_AT.plusSeconds(10)));

        LogObject contentTypeMismatch = authorized();
        assertThrows(IllegalStateException.class, () -> contentTypeMismatch.markUploaded(1024L, SHA256, "etag",
                "application/zip", AUTHORIZED_AT.plusSeconds(10)));

        assertEquals(LogUploadState.AUTHORIZED, sizeMismatch.getUploadState());
        assertEquals(LogUploadState.AUTHORIZED, digestMismatch.getUploadState());
        assertEquals(LogUploadState.AUTHORIZED, contentTypeMismatch.getUploadState());
    }

    @Test
    @DisplayName("过期 AUTHORIZED 不允许置为 UPLOADED")
    void markUploadedRejectsExpiredAuthorization() {
        LogObject logObject = authorized();
        assertThrows(IllegalStateException.class, () -> logObject.markUploaded(1024L, SHA256, "etag",
                "application/gzip", EXPIRES_AT));
    }

    @Test
    @DisplayName("确定性失败保存观察元数据与原因码，且为终态")
    void markFailedStoresObservedMetadata() {
        LogObject logObject = authorized();
        logObject.recordCompleteReceived(AUTHORIZED_AT.plusSeconds(10));
        logObject.markFailed(LogFailureReason.SIZE_MISMATCH, 2048L, "c".repeat(64), "\"etag\"",
                AUTHORIZED_AT.plusSeconds(20));
        assertEquals(LogUploadState.FAILED, logObject.getUploadState());
        assertEquals(LogFailureReason.SIZE_MISMATCH, logObject.getFailureReason());
        assertEquals(2048L, logObject.getActualSize());
        assertEquals("c".repeat(64), logObject.getActualSha256());
        assertEquals("\"etag\"", logObject.getEtag());
        assertNull(logObject.getUploadedAt());
        assertThrows(IllegalStateException.class, () -> logObject.markUploaded(2048L, "c".repeat(64), "etag",
                "application/gzip", AUTHORIZED_AT.plusSeconds(30)));
    }

    @Test
    @DisplayName("仅到期 AUTHORIZED 可转 EXPIRED，且不改写 uploadedAt")
    void markExpiredOnlyWhenExpired() {
        LogObject logObject = authorized();
        assertThrows(IllegalStateException.class, () -> logObject.markExpired(AUTHORIZED_AT.plusSeconds(1)));
        logObject.markExpired(EXPIRES_AT);
        assertEquals(LogUploadState.EXPIRED, logObject.getUploadState());
        assertNull(logObject.getUploadedAt());
        assertTrue(logObject.isExpired());
        assertThrows(IllegalStateException.class, () -> logObject.markUploaded(1024L, SHA256, "etag",
                "application/gzip", EXPIRES_AT.plusSeconds(1)));
    }

    @Test
    @DisplayName("UPLOADED 到达原授权过期时间后仍保持 UPLOADED")
    void uploadedSurvivesOriginalExpiration() {
        LogObject logObject = authorized();
        logObject.markUploaded(1024L, SHA256, "etag", "application/gzip", AUTHORIZED_AT.plusSeconds(10));
        assertFalse(logObject.isUploadExpired(EXPIRES_AT.plusSeconds(1)));
        assertEquals(LogUploadState.UPLOADED, logObject.getUploadState());
    }

    @Test
    @DisplayName("过期判定边界为 now >= expiresAt")
    void expirationBoundary() {
        LogObject logObject = authorized();
        assertFalse(logObject.isUploadExpired(EXPIRES_AT.minusMillis(1)));
        assertTrue(logObject.isUploadExpired(EXPIRES_AT));
    }

    @Test
    @DisplayName("条件更新成功后乐观锁版本递增")
    void increaseRowVersion() {
        LogObject logObject = authorized();
        logObject.increaseRowVersion("tsp-ota", AUTHORIZED_AT.plusSeconds(5));
        assertEquals(2, logObject.getRowVersion());
    }

    private LogObject authorized() {
        return LogObject.authorized("object-id", StorageProvider.TENCENT_COS, "ap-shanghai", "log-bucket",
                "tsp-ota", LogBusinessType.OTA_UPGRADE_LOG, "task-1", "execution-1", "request-1", "upgrade.log.gz",
                "logs/tsp-ota/OTA_UPGRADE_LOG/20261115/object-id", LogContentType.GZIP, 1024L, SHA256,
                "INTERNAL", EXPIRES_AT, AUTHORIZED_AT);
    }

}
