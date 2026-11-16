package net.hwyz.iov.cloud.edd.oss.service.domain;

import net.hwyz.iov.cloud.edd.oss.service.domain.exception.LogValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogContentType;
import net.hwyz.iov.cloud.edd.oss.service.domain.service.LogValidationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志参数与格式策略单元测试（EDD-OSS-DSN-CR-003 §4.3、§4.4）。
 *
 * @author hwyz_leo
 */
class LogValidationPolicyTest {

    @Test
    @DisplayName("调用方服务名仅接受 [a-z0-9][a-z0-9-]{0,63}")
    void callerServiceName() {
        assertEquals("tsp-ota", LogValidationPolicy.validateCallerService(" tsp-ota "));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateCallerService("TSP-OTA"));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateCallerService("-tsp"));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateCallerService(null));
    }

    @Test
    @DisplayName("businessType 精确匹配 OTA_UPGRADE_LOG")
    void businessType() {
        assertEquals(LogBusinessType.OTA_UPGRADE_LOG,
                LogValidationPolicy.validateBusinessType(" OTA_UPGRADE_LOG "));
        assertThrows(LogValidationException.class,
                () -> LogValidationPolicy.validateBusinessType("ota_upgrade_log"));
        assertThrows(LogValidationException.class,
                () -> LogValidationPolicy.validateBusinessType("OTHER"));
    }

    @Test
    @DisplayName("businessId 去首尾空白、长度 1～128 且禁止控制字符")
    void businessId() {
        assertEquals("task-1", LogValidationPolicy.validateBusinessId("businessId", " task-1 "));
        assertThrows(LogValidationException.class,
                () -> LogValidationPolicy.validateBusinessId("businessId", " "));
        assertThrows(LogValidationException.class,
                () -> LogValidationPolicy.validateBusinessId("businessId", "a".repeat(129)));
        assertThrows(LogValidationException.class,
                () -> LogValidationPolicy.validateBusinessId("businessId", "task\u0001"));
    }

    @Test
    @DisplayName("businessSubId 为空返回 null")
    void optionalBusinessSubId() {
        assertNull(LogValidationPolicy.validateOptionalBusinessSubId("businessSubId", " "));
        assertNull(LogValidationPolicy.validateOptionalBusinessSubId("businessSubId", null));
        assertEquals("execution-1",
                LogValidationPolicy.validateOptionalBusinessSubId("businessSubId", "execution-1"));
    }

    @Test
    @DisplayName("文件名禁止路径分隔符与控制字符，长度不超过 255")
    void fileName() {
        assertEquals("upgrade.log.gz", LogValidationPolicy.validateFileName("upgrade.log.gz"));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateFileName("a/b.log"));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateFileName("a\\b.log"));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateFileName("a\u0000b.log"));
        assertThrows(LogValidationException.class,
                () -> LogValidationPolicy.validateFileName("a".repeat(256)));
    }

    @Test
    @DisplayName("Content-Type 仅接受日志白名单，不接受图片 MIME")
    void contentType() {
        assertEquals(LogContentType.GZIP, LogValidationPolicy.validateContentType("application/gzip"));
        assertEquals(LogContentType.ZIP, LogValidationPolicy.validateContentType("APPLICATION/ZIP"));
        assertEquals(LogContentType.ZSTD, LogValidationPolicy.validateContentType("application/zstd"));
        assertEquals(LogContentType.TAR, LogValidationPolicy.validateContentType("application/x-tar"));
        assertEquals(LogContentType.OCTET_STREAM,
                LogValidationPolicy.validateContentType("application/octet-stream"));
        assertThrows(LogValidationException.class,
                () -> LogValidationPolicy.validateContentType("image/jpeg"));
    }

    @Test
    @DisplayName("声明大小必须大于 0 且不超过上限")
    void declaredSize() {
        assertEquals(1L, LogValidationPolicy.validateDeclaredSize(1L, 536870912L));
        assertEquals(536870912L, LogValidationPolicy.validateDeclaredSize(536870912L, 536870912L));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateDeclaredSize(0L, 536870912L));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateDeclaredSize(-1L, 536870912L));
        assertThrows(LogValidationException.class,
                () -> LogValidationPolicy.validateDeclaredSize(536870913L, 536870912L));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateDeclaredSize(null, 536870912L));
    }

    @Test
    @DisplayName("SHA-256 必须为 64 位十六进制")
    void sha256() {
        String valid = "0123456789abcdef".repeat(4);
        assertEquals(valid, LogValidationPolicy.validateSha256(valid));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateSha256("a".repeat(63)));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateSha256("g".repeat(64)));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.validateSha256(null));
    }

    @Test
    @DisplayName("TTL 缺省使用默认值，0 与超上限被拒绝")
    void ttl() {
        assertEquals(900, LogValidationPolicy.resolveTtlSeconds(null, 900L, 3600L));
        assertEquals(60, LogValidationPolicy.resolveTtlSeconds(60, 900L, 3600L));
        assertEquals(3600, LogValidationPolicy.resolveTtlSeconds(3600, 900L, 3600L));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.resolveTtlSeconds(0, 900L, 3600L));
        assertThrows(LogValidationException.class, () -> LogValidationPolicy.resolveTtlSeconds(3601, 900L, 3600L));
    }

    @Test
    @DisplayName("对象键只使用调用方、业务类型、UTC 日期与 objectId")
    void objectKey() {
        Instant authorizedAt = Instant.parse("2026-11-15T23:30:00Z");
        String objectKey = LogValidationPolicy.buildObjectKey("tsp-ota", LogBusinessType.OTA_UPGRADE_LOG,
                authorizedAt, "2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3");
        assertEquals("logs/tsp-ota/OTA_UPGRADE_LOG/20261115/2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3", objectKey);
        assertTrue(!objectKey.contains(".."));
        assertTrue(!objectKey.contains("upgrade.log"));
    }

}
