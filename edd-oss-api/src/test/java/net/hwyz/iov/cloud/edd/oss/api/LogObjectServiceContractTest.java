package net.hwyz.iov.cloud.edd.oss.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.hwyz.iov.cloud.edd.oss.api.fallback.LogObjectServiceFallbackFactory;
import net.hwyz.iov.cloud.edd.oss.api.service.LogObjectService;
import net.hwyz.iov.cloud.edd.oss.api.vo.request.GenerateLogObjectUploadRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LogObject API 契约测试（EDD-OSS-DSN-CR-003 §8、§12）。
 *
 * @author hwyz_leo
 */
class LogObjectServiceContractTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("Feign 客户端契约：服务名、路径、contextId 与降级工厂固定")
    void feignClientContract() {
        org.springframework.cloud.openfeign.FeignClient feignClient =
                org.springframework.core.annotation.AnnotatedElementUtils
                        .getMergedAnnotation(LogObjectService.class,
                                org.springframework.cloud.openfeign.FeignClient.class);
        assertNotNull(feignClient);
        assertEquals(net.hwyz.iov.cloud.framework.common.constant.ServiceNameConstants.EDD_OSS,
                feignClient.name());
        assertEquals("logObjectService", feignClient.contextId());
        assertEquals("/api/service/logobject/v1", feignClient.path());
        assertEquals(LogObjectServiceFallbackFactory.class, feignClient.fallbackFactory());
    }

    @Test
    @DisplayName("降级必须 fail-closed：不得返回伪造 objectId、URL 或 UPLOADED")
    void fallbackMustFailClosed() {
        LogObjectService fallback = new LogObjectServiceFallbackFactory().create(
                new IllegalStateException("boom"));
        assertThrows(IllegalStateException.class, () -> fallback.generateUpload(validRequest()));
        assertThrows(IllegalStateException.class, () -> fallback.getObject("object-id"));
        assertThrows(IllegalStateException.class, () -> fallback.completeUpload("object-id"));
        assertThrows(IllegalStateException.class, () -> fallback.generateDownloadUrl("object-id"));
        assertThrows(IllegalStateException.class, () -> fallback.listObjects(null, null, null, null, null,
                null, null));
    }

    @Test
    @DisplayName("合法日志申请通过 Bean Validation")
    void validRequestPassesValidation() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    @DisplayName("必填字段缺失被拒绝")
    void requiredFieldsAreRejected() {
        GenerateLogObjectUploadRequest request = validRequest();
        request.setBusinessType(null);
        request.setBusinessId("");
        request.setRequestId(null);
        request.setFileName("");
        request.setContentType(null);
        request.setPrivacyLevel("");
        Set<ConstraintViolation<GenerateLogObjectUploadRequest>> violations = validator.validate(request);
        assertEquals(6, violations.size());
    }

    @Test
    @DisplayName("sha256Hex 必须为 64 位小写十六进制")
    void sha256Format() {
        GenerateLogObjectUploadRequest request = validRequest();
        request.setSha256Hex("A".repeat(64));
        assertFalse(validator.validate(request).isEmpty());
        request.setSha256Hex("a".repeat(63));
        assertFalse(validator.validate(request).isEmpty());
        request.setSha256Hex("a".repeat(64));
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("sizeBytes 必须大于 0，ttlSeconds 必须大于 0")
    void numericBoundaries() {
        GenerateLogObjectUploadRequest request = validRequest();
        request.setSizeBytes(0L);
        assertFalse(validator.validate(request).isEmpty());
        request.setSizeBytes(1L);
        assertTrue(validator.validate(request).isEmpty());
        request.setTtlSeconds(0);
        assertFalse(validator.validate(request).isEmpty());
        request.setTtlSeconds(900);
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("businessId/requestId 长度与 fileName 长度边界")
    void lengthBoundaries() {
        GenerateLogObjectUploadRequest request = validRequest();
        request.setBusinessId("a".repeat(129));
        assertFalse(validator.validate(request).isEmpty());
        request.setBusinessId("a".repeat(128));
        assertTrue(validator.validate(request).isEmpty());
        request.setFileName("a".repeat(256));
        assertFalse(validator.validate(request).isEmpty());
        request.setFileName("a".repeat(255));
        assertTrue(validator.validate(request).isEmpty());
    }

    private GenerateLogObjectUploadRequest validRequest() {
        return GenerateLogObjectUploadRequest.builder()
                .businessType("OTA_UPGRADE_LOG")
                .businessId("task-1")
                .businessSubId("execution-1")
                .requestId("request-1")
                .fileName("upgrade.log.gz")
                .contentType("application/gzip")
                .sizeBytes(1024L)
                .sha256Hex("a".repeat(64))
                .privacyLevel("INTERNAL")
                .ttlSeconds(900)
                .build();
    }

}
