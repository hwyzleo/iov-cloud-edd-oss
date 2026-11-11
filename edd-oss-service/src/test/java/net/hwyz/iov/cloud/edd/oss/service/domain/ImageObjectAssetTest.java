package net.hwyz.iov.cloud.edd.oss.service.domain;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ObjectAccessPermission;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认素材领域规则单元测试（EDD-OSS-DSN-CR-002 §15.7.1）。
 *
 * @author hwyz_leo
 */
class ImageObjectAssetTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final String DEFAULT_SERVICE = "edd-oss";
    private static final String BUSINESS_SERVICE = "sec-ciam";

    @Test
    @DisplayName("仅 serviceName=edd-oss 识别为默认素材")
    void detectsDefaultAsset() {
        assertTrue(defaultAsset().isDefaultAsset());
        assertFalse(businessAsset().isDefaultAsset());
    }

    @Test
    @DisplayName("素材名称与备注通过领域方法更新，且不影响对象键与图片元数据")
    void modifyAssetKeepsIdentity() {
        ImageObject imageObject = uploaded(defaultAsset());
        String objectId = imageObject.getObjectId();
        String objectKey = imageObject.getObjectKey();
        imageObject.modifyAsset("默认用户头像（新）", "更新后的备注", "1001", NOW.plusSeconds(30));
        assertEquals("默认用户头像（新）", imageObject.getAssetName());
        assertEquals("更新后的备注", imageObject.getDescription());
        assertEquals(objectId, imageObject.getObjectId());
        assertEquals(objectKey, imageObject.getObjectKey());
        assertEquals(ImageMimeType.IMAGE_PNG, imageObject.getActualContentType());
        assertEquals("1001", imageObject.getModifyBy());
        assertEquals(NOW.plusSeconds(30), imageObject.getModifyTime());
    }

    @Test
    @DisplayName("非默认素材不得调用 modifyAsset")
    void modifyAssetRejectsBusinessImage() {
        ImageObject imageObject = uploaded(businessAsset());
        assertThrows(IllegalStateException.class,
                () -> imageObject.modifyAsset("素材", null, "1001", NOW));
    }

    @Test
    @DisplayName("非 UPLOADED 不能替换")
    void markReplacedRequiresUploaded() {
        ImageObject pending = defaultAsset();
        assertThrows(IllegalStateException.class, () -> pending.markReplaced("v2.png", 100L,
                ImageMimeType.IMAGE_PNG, 100L, "etag", "1001", NOW));
        ImageObject deleted = uploaded(defaultAsset());
        deleted.markDeleted("1001", NOW);
        assertThrows(IllegalStateException.class, () -> deleted.markReplaced("v2.png", 100L,
                ImageMimeType.IMAGE_PNG, 100L, "etag", "1001", NOW));
    }

    @Test
    @DisplayName("新旧 MIME 不一致拒绝替换")
    void markReplacedRejectsMimeChange() {
        ImageObject imageObject = uploaded(defaultAsset());
        assertThrows(IllegalStateException.class, () -> imageObject.markReplaced("v2.jpg", 100L,
                ImageMimeType.IMAGE_JPEG, 100L, "etag", "1001", NOW));
    }

    @Test
    @DisplayName("替换成功保留 objectId、objectKey、素材名称与备注，更新新图片元数据")
    void markReplacedKeepsStableIdentity() {
        ImageObject imageObject = uploaded(defaultAsset());
        imageObject.markReplaced("avatar-v2.png", 307200L, ImageMimeType.IMAGE_PNG, 300000L, "new-etag", "1001",
                NOW.plusSeconds(60));
        assertEquals("default-object-id", imageObject.getObjectId());
        assertEquals("images/edd-oss/default-object-id.png", imageObject.getObjectKey());
        assertEquals("默认用户头像", imageObject.getAssetName());
        assertEquals("未设置头像时使用", imageObject.getDescription());
        assertEquals("avatar-v2.png", imageObject.getOriginalFilename());
        assertEquals(307200L, imageObject.getDeclaredSize());
        assertEquals(ImageMimeType.IMAGE_PNG, imageObject.getActualContentType());
        assertEquals(300000L, imageObject.getActualSize());
        assertEquals("new-etag", imageObject.getEtag());
        assertEquals(NOW.plusSeconds(60), imageObject.getUploadTime());
        assertEquals("1001", imageObject.getModifyBy());
        assertTrue(imageObject.isUploaded());
    }

    private ImageObject defaultAsset() {
        return ImageObject.pending("default-object-id", StorageProvider.TENCENT_COS, "ap-shanghai",
                "iov-public-1253442587", "images/edd-oss/default-object-id.png", DEFAULT_SERVICE, "默认用户头像",
                "未设置头像时使用", "avatar.png", ImageMimeType.IMAGE_PNG, 204800L,
                ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE, NOW.plusSeconds(900), "1001", NOW);
    }

    private ImageObject businessAsset() {
        return ImageObject.pending("business-object-id", StorageProvider.TENCENT_COS, "ap-shanghai",
                "iov-public-1253442587", "images/sec-ciam/business-object-id.png", BUSINESS_SERVICE, "avatar.png",
                ImageMimeType.IMAGE_PNG, 204800L, ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE,
                NOW.plusSeconds(900), BUSINESS_SERVICE, NOW);
    }

    private ImageObject uploaded(ImageObject imageObject) {
        imageObject.markUploaded(ImageMimeType.IMAGE_PNG, 204800L, "old-etag", NOW.plusSeconds(10));
        return imageObject;
    }

}
