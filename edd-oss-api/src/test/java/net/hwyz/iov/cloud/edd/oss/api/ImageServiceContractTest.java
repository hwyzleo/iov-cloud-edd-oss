package net.hwyz.iov.cloud.edd.oss.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.hwyz.iov.cloud.edd.oss.api.fallback.ImageServiceFallbackFactory;
import net.hwyz.iov.cloud.edd.oss.api.service.ImageService;
import net.hwyz.iov.cloud.edd.oss.api.vo.request.GenerateImageUploadRequest;
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
 * API 契约测试（EDD-OSS-DSN-CR-001 §10.1、§15.2）。
 *
 * @author hwyz_leo
 */
class ImageServiceContractTest {

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
    @DisplayName("Feign 客户端契约：服务名、路径和降级工厂固定")
    void feignClientContract() {
        org.springframework.cloud.openfeign.FeignClient feignClient =
                ImageService.class.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class);
        assertNotNull(feignClient);
        assertEquals("oss-service", feignClient.name());
        assertEquals("imageService", feignClient.contextId());
        assertEquals("/api/service/image/v1", feignClient.path());
        assertEquals(ImageServiceFallbackFactory.class, feignClient.fallbackFactory());
    }

    @Test
    @DisplayName("降级必须 fail-closed：不得返回伪造 objectId 或 uploadUrl")
    void fallbackMustFailClosed() {
        ImageService fallback = new ImageServiceFallbackFactory().create(new IllegalStateException("boom"));
        assertThrows(IllegalStateException.class, () -> fallback.generateUpload(validRequest()));
        assertThrows(IllegalStateException.class, () -> fallback.completeUpload("object-id"));
    }

    @Test
    @DisplayName("合法请求通过 Bean Validation")
    void validRequestPassesValidation() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    @DisplayName("originalFilename、contentType 不得为空")
    void blankFieldsAreRejected() {
        GenerateImageUploadRequest request = validRequest();
        request.setOriginalFilename(null);
        request.setContentType("");
        Set<ConstraintViolation<GenerateImageUploadRequest>> violations = validator.validate(request);
        assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("size 边界：1 与 10485760 合法，0 与 10485761 非法")
    void sizeBoundaries() {
        GenerateImageUploadRequest request = validRequest();
        request.setSize(1L);
        assertTrue(validator.validate(request).isEmpty());
        request.setSize(10485760L);
        assertTrue(validator.validate(request).isEmpty());
        request.setSize(0L);
        assertFalse(validator.validate(request).isEmpty());
        request.setSize(10485761L);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("originalFilename 超过 255 字符被拒绝")
    void filenameLength() {
        GenerateImageUploadRequest request = validRequest();
        request.setOriginalFilename("a".repeat(256) + ".jpg");
        assertFalse(validator.validate(request).isEmpty());
    }

    private GenerateImageUploadRequest validRequest() {
        return GenerateImageUploadRequest.builder()
                .originalFilename("avatar.jpg")
                .contentType("image/jpeg")
                .size(204800L)
                .build();
    }

}
