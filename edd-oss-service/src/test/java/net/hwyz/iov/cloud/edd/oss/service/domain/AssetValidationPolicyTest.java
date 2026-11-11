package net.hwyz.iov.cloud.edd.oss.service.domain;

import net.hwyz.iov.cloud.edd.oss.service.domain.exception.ImageValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.service.ImageValidationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 素材字段与替换校验单元测试（EDD-OSS-DSN-CR-002 §15.7.1）。
 *
 * @author hwyz_leo
 */
class AssetValidationPolicyTest {

    private static final long MAX_SIZE = 10485760L;

    @Test
    @DisplayName("素材名称去首尾空白后 1～100 字符通过")
    void assetNameBoundaries() {
        assertEquals("头像", ImageValidationPolicy.validateAssetName("  头像  "));
        assertEquals("a", ImageValidationPolicy.validateAssetName("a"));
        String hundred = "a".repeat(100);
        assertEquals(hundred, ImageValidationPolicy.validateAssetName(hundred));
    }

    @Test
    @DisplayName("素材名称为空或超过 100 字符拒绝")
    void assetNameRejected() {
        assertInvalidAsset(() -> ImageValidationPolicy.validateAssetName(null));
        assertInvalidAsset(() -> ImageValidationPolicy.validateAssetName("   "));
        assertInvalidAsset(() -> ImageValidationPolicy.validateAssetName("a".repeat(101)));
    }

    @Test
    @DisplayName("备注为空保存为 null，最长 255")
    void descriptionNormalization() {
        assertNull(ImageValidationPolicy.validateDescription(null));
        assertNull(ImageValidationPolicy.validateDescription("   "));
        assertEquals("备注", ImageValidationPolicy.validateDescription("  备注 "));
        String max = "a".repeat(255);
        assertEquals(max, ImageValidationPolicy.validateDescription(max));
    }

    @Test
    @DisplayName("备注超过 255 字符拒绝")
    void descriptionRejected() {
        assertInvalidAsset(() -> ImageValidationPolicy.validateDescription("a".repeat(256)));
    }

    @Test
    @DisplayName("替换图片声明 MIME 必须与原图片实际 MIME 一致")
    void replacementRequiresSameMime() {
        assertEquals(ImageMimeType.IMAGE_PNG,
                ImageValidationPolicy.validateReplacement(ImageMimeType.IMAGE_PNG, "v2.png", "image/png",
                        204800L, MAX_SIZE));
        ImageValidationException mismatch = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateReplacement(ImageMimeType.IMAGE_PNG, "v2.jpg", "image/jpeg",
                        204800L, MAX_SIZE));
        assertEquals(ImageValidationException.Failure.REPLACEMENT_MIME_MISMATCH, mismatch.getFailure());
        ImageValidationException unknownOriginal = assertThrows(ImageValidationException.class,
                () -> ImageValidationPolicy.validateReplacement(null, "v2.png", "image/png", 204800L, MAX_SIZE));
        assertEquals(ImageValidationException.Failure.REPLACEMENT_MIME_MISMATCH, unknownOriginal.getFailure());
    }

    @Test
    @DisplayName("替换图片沿用扩展名、MIME 与大小约束")
    void replacementKeepsImageConstraints() {
        assertEquals(ImageValidationException.Failure.UNSUPPORTED_MIME,
                assertThrows(ImageValidationException.class, () -> ImageValidationPolicy.validateReplacement(
                        ImageMimeType.IMAGE_PNG, "v2.gif", "image/gif", 100L, MAX_SIZE)).getFailure());
        assertEquals(ImageValidationException.Failure.MIME_EXTENSION_MISMATCH,
                assertThrows(ImageValidationException.class, () -> ImageValidationPolicy.validateReplacement(
                        ImageMimeType.IMAGE_PNG, "v2.jpg", "image/png", 100L, MAX_SIZE)).getFailure());
        assertEquals(ImageValidationException.Failure.INVALID_DECLARED_SIZE,
                assertThrows(ImageValidationException.class, () -> ImageValidationPolicy.validateReplacement(
                        ImageMimeType.IMAGE_PNG, "v2.png", "image/png", 0L, MAX_SIZE)).getFailure());
    }

    @Test
    @DisplayName("替换对象键使用 UUID 且不含原始文件名")
    void replacementKeys() {
        String objectId = "2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3";
        String replacementId = "fb62a661-946f-49b1-8268-a65991d44184";
        assertEquals("images/edd-oss/replacements/" + objectId + "/" + replacementId + ".png",
                ImageValidationPolicy.buildReplacementObjectKey(objectId, replacementId, ImageMimeType.IMAGE_PNG));
        assertEquals("images/edd-oss/replacement-backups/" + objectId + "/" + replacementId + ".jpg",
                ImageValidationPolicy.buildReplacementBackupKey(objectId, replacementId, ImageMimeType.IMAGE_JPEG));
        assertEquals("images/edd-oss/replacement-backups/" + objectId + "/" + replacementId + ".webp",
                ImageValidationPolicy.buildReplacementBackupKey(objectId, replacementId, ImageMimeType.IMAGE_WEBP));
    }

    private void assertInvalidAsset(org.junit.jupiter.api.function.Executable executable) {
        ImageValidationException exception = assertThrows(ImageValidationException.class, executable);
        assertEquals(ImageValidationException.Failure.INVALID_ASSET, exception.getFailure());
    }

}
