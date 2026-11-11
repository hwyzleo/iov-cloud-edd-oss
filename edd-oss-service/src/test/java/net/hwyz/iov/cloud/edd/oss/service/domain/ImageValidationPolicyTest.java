package net.hwyz.iov.cloud.edd.oss.service.domain;

import net.hwyz.iov.cloud.edd.oss.service.domain.exception.ImageValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.service.ImageValidationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片校验策略单元测试（EDD-OSS-DSN-CR-001 §15.1）。
 *
 * @author hwyz_leo
 */
class ImageValidationPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "avatar.jpg,image/jpeg,IMAGE_JPEG",
            "avatar.JPEG,image/jpeg,IMAGE_JPEG",
            "avatar.jpeg, IMAGE/JPEG ,IMAGE_JPEG",
            "avatar.png,image/png,IMAGE_PNG",
            "avatar.PNG,image/png,IMAGE_PNG",
            "avatar.webp,image/webp,IMAGE_WEBP"
    })
    @DisplayName("允许的类型与扩展名正例")
    void acceptsSupportedImages(String filename, String contentType, ImageMimeType expected) {
        assertEquals(expected, ImageValidationPolicy.validateGenerate("tsp-account", filename, contentType, 1L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"avatar", "avatar.", ".jpg"})
    @DisplayName("无扩展名被拒绝（OSS-400101 语义）")
    void rejectsMissingExtension(String filename) {
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateGenerate("tsp-account", filename, "image/jpeg", 1L));
        assertEquals(ImageValidationException.Failure.UNSUPPORTED_MIME, exception.getFailure());
    }

    @ParameterizedTest
    @ValueSource(strings = {"avatar.svg", "avatar.gif", "avatar.bmp", "avatar.heic"})
    @DisplayName("未声明的扩展名被拒绝（扩展名与声明 MIME 不匹配语义）")
    void rejectsUnsupportedExtensions(String filename) {
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateGenerate("tsp-account", filename, "image/jpeg", 1L));
        assertEquals(ImageValidationException.Failure.MIME_EXTENSION_MISMATCH, exception.getFailure());
    }

    @ParameterizedTest
    @ValueSource(strings = {"avatar.svg", "avatar.gif", "avatar.bmp", "avatar.heic"})
    @DisplayName("未声明的 MIME 即使扩展名一致也被拒绝（OSS-400101 语义）")
    void rejectsUnsupportedMimeWithMatchingExtension(String filename) {
        String contentType = "image/" + filename.substring(filename.indexOf('.') + 1);
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateGenerate("tsp-account", filename, contentType, 1L));
        assertEquals(ImageValidationException.Failure.UNSUPPORTED_MIME, exception.getFailure());
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/svg+xml", "image/gif", "image/bmp", "image/heic", "text/plain", ""})
    @DisplayName("未声明的 MIME 被拒绝")
    void rejectsUnsupportedMime(String contentType) {
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateGenerate("tsp-account", "avatar.jpg", contentType, 1L));
        assertEquals(ImageValidationException.Failure.UNSUPPORTED_MIME, exception.getFailure());
    }

    @ParameterizedTest
    @CsvSource({
            "avatar.jpg,image/png",
            "avatar.png,image/jpeg",
            "avatar.webp,image/jpeg",
            "avatar.jpeg,image/webp"
    })
    @DisplayName("扩展名与声明 MIME 不匹配被拒绝")
    void rejectsMismatchedExtension(String filename, String contentType) {
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateGenerate("tsp-account", filename, contentType, 1L));
        assertEquals(ImageValidationException.Failure.MIME_EXTENSION_MISMATCH, exception.getFailure());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, 10485761L, Long.MAX_VALUE})
    @DisplayName("声明大小边界：0、负数、超限被拒绝")
    void rejectsInvalidDeclaredSize(long size) {
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateGenerate("tsp-account", "avatar.jpg", "image/jpeg", size));
        assertEquals(ImageValidationException.Failure.INVALID_DECLARED_SIZE, exception.getFailure());
    }

    @Test
    @DisplayName("声明大小边界：1 与 10485760 合法")
    void acceptsSizeBoundaries() {
        assertEquals(ImageMimeType.IMAGE_JPEG,
                ImageValidationPolicy.validateGenerate("tsp-account", "avatar.jpg", "image/jpeg", 1L));
        assertEquals(ImageMimeType.IMAGE_JPEG,
                ImageValidationPolicy.validateGenerate("tsp-account", "avatar.jpg", "image/jpeg", 10485760L));
    }

    @Test
    @DisplayName("可配置上限生效")
    void respectsConfiguredMaxSize() {
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateGenerate("tsp-account", "avatar.jpg", "image/jpeg", 1024L,
                        1023L));
        assertEquals(ImageValidationException.Failure.INVALID_DECLARED_SIZE, exception.getFailure());
    }

    @Test
    @DisplayName("文件名为空、超长、包含路径分隔符或控制字符被拒绝")
    void rejectsInvalidFilename() {
        assertFailure(ImageValidationException.Failure.INVALID_FILENAME, "", 1L);
        assertFailure(ImageValidationException.Failure.INVALID_FILENAME, "a".repeat(256), 1L);
        assertFailure(ImageValidationException.Failure.INVALID_FILENAME, "dir/avatar.jpg", 1L);
        assertFailure(ImageValidationException.Failure.INVALID_FILENAME, "dir\\avatar.jpg", 1L);
        assertFailure(ImageValidationException.Failure.INVALID_FILENAME, "avatar\u0000.jpg", 1L);
        assertFailure(ImageValidationException.Failure.INVALID_FILENAME, "avatar\t.jpg", 1L);
    }

    @Test
    @DisplayName("非法 serviceName 被拒绝")
    void rejectsInvalidServiceName() {
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateServiceName("TSP-ACCOUNT"));
        assertEquals(ImageValidationException.Failure.INVALID_SERVICE_NAME, exception.getFailure());
        assertThrows(ImageValidationException.class, () -> ImageValidationPolicy.validateServiceName("a".repeat(65)));
        assertThrows(ImageValidationException.class, () -> ImageValidationPolicy.validateServiceName("-abc"));
        ImageValidationPolicy.validateServiceName("tsp-account");
        ImageValidationPolicy.validateServiceName("a");
    }

    @Test
    @DisplayName("对象键规则：包含服务名与 UUID，不包含原始文件名")
    void buildsObjectKeyWithoutOriginalFilename() {
        String objectId = "2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3";
        String key = ImageValidationPolicy.buildObjectKey("tsp-account", objectId, ImageMimeType.IMAGE_JPEG);
        assertEquals("images/tsp-account/" + objectId + ".jpg", key);
        assertFalse(key.contains("avatar"));
        String another = ImageValidationPolicy.buildObjectKey("tsp-account",
                java.util.UUID.randomUUID().toString(), ImageMimeType.IMAGE_PNG);
        assertNotEquals(key, another);
        assertTrue(another.endsWith(".png"));
    }

    @Test
    @DisplayName("实际元数据校验：大小与 MIME")
    void validatesActualMetadata() {
        assertEquals(ImageMimeType.IMAGE_JPEG, ImageValidationPolicy.validateActual(ImageMimeType.IMAGE_JPEG,
                "image/jpeg", 100L));
        assertActualFailure(ImageValidationException.Failure.ACTUAL_SIZE_INVALID, 0L, "image/jpeg");
        assertActualFailure(ImageValidationException.Failure.ACTUAL_SIZE_INVALID, 10485761L, "image/jpeg");
        assertActualFailure(ImageValidationException.Failure.ACTUAL_MIME_INVALID, 100L, "image/gif");
        assertActualFailure(ImageValidationException.Failure.ACTUAL_MIME_INVALID, 100L, null);
        assertActualFailure(ImageValidationException.Failure.ACTUAL_MIME_INVALID, 100L, "image/png");
    }

    private void assertFailure(ImageValidationException.Failure failure, String filename, long size) {
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateGenerate("tsp-account", filename, "image/jpeg", size));
        assertEquals(failure, exception.getFailure());
    }

    private void assertActualFailure(ImageValidationException.Failure failure, long size, String contentType) {
        ImageValidationException exception = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateActual(ImageMimeType.IMAGE_JPEG, contentType, size));
        assertEquals(failure, exception.getFailure());
    }

}
