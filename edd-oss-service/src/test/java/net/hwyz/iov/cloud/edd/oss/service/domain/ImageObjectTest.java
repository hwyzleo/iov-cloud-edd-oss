package net.hwyz.iov.cloud.edd.oss.service.domain;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ObjectAccessPermission;
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
 * 图片对象状态机单元测试（EDD-OSS-DSN-CR-001 §15.1）。
 *
 * @author hwyz_leo
 */
class ImageObjectTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    @DisplayName("创建只能得到 PENDING，且不包含实际元数据")
    void pendingCreation() {
        ImageObject imageObject = pending(NOW.plusSeconds(900));
        assertEquals(ImageStatus.PENDING, imageObject.getStatus());
        assertTrue(imageObject.isPending());
        assertNull(imageObject.getActualContentType());
        assertNull(imageObject.getActualSize());
        assertNull(imageObject.getUploadTime());
        assertEquals(1, imageObject.getRowVersion());
        assertNull(imageObject.getId());
    }

    @Test
    @DisplayName("PENDING → UPLOADED 写入实际元数据")
    void markUploaded() {
        ImageObject imageObject = pending(NOW.plusSeconds(900));
        imageObject.markUploaded(ImageMimeType.IMAGE_JPEG, 204800L, "etag-1", NOW);
        assertTrue(imageObject.isUploaded());
        assertEquals(ImageMimeType.IMAGE_JPEG, imageObject.getActualContentType());
        assertEquals(204800L, imageObject.getActualSize());
        assertEquals("etag-1", imageObject.getEtag());
        assertEquals(NOW, imageObject.getUploadTime());
    }

    @Test
    @DisplayName("PENDING 不能直接 DELETED")
    void pendingCannotBeDeleted() {
        ImageObject imageObject = pending(NOW.plusSeconds(900));
        assertThrows(IllegalStateException.class, () -> imageObject.markDeleted("1", NOW));
        assertEquals(ImageStatus.PENDING, imageObject.getStatus());
    }

    @Test
    @DisplayName("UPLOADED 可以 DELETED，DELETED 保留实际元数据")
    void uploadedCanBeDeleted() {
        ImageObject imageObject = pending(NOW.plusSeconds(900));
        imageObject.markUploaded(ImageMimeType.IMAGE_JPEG, 100L, "etag", NOW);
        imageObject.markDeleted("1001", NOW.plusSeconds(60));
        assertTrue(imageObject.isDeleted());
        assertEquals("1001", imageObject.getDeleteBy());
        assertEquals(NOW.plusSeconds(60), imageObject.getDeleteTime());
        assertEquals(ImageMimeType.IMAGE_JPEG, imageObject.getActualContentType());
        assertEquals(100L, imageObject.getActualSize());
    }

    @Test
    @DisplayName("DELETED 不得重新 UPLOADED，UPLOADED 不得重复上传来改状态")
    void deletedCannotBeUploadedAgain() {
        ImageObject imageObject = uploaded();
        imageObject.markDeleted("1001", NOW);
        assertThrows(IllegalStateException.class,
                () -> imageObject.markUploaded(ImageMimeType.IMAGE_PNG, 10L, "etag-2", NOW));
    }

    @Test
    @DisplayName("过期判定只对 PENDING 生效")
    void expiry() {
        ImageObject imageObject = pending(NOW.plusSeconds(900));
        assertFalse(imageObject.isExpired(NOW));
        assertFalse(imageObject.isExpired(NOW.plusSeconds(899)));
        assertTrue(imageObject.isExpired(NOW.plusSeconds(900)));
        assertTrue(imageObject.isExpired(NOW.plusSeconds(901)));
        ImageObject uploaded = uploaded();
        assertFalse(uploaded.isExpired(NOW.plusSeconds(100000)));
    }

    @Test
    @DisplayName("归属判定使用认证后的服务名精确匹配")
    void ownership() {
        ImageObject imageObject = pending(NOW.plusSeconds(900));
        assertTrue(imageObject.belongsTo("tsp-account"));
        assertFalse(imageObject.belongsTo("tsp-other"));
        assertFalse(imageObject.belongsTo(null));
    }

    @Test
    @DisplayName("条件更新成功后 rowVersion 递增并记录修改方")
    void increaseRowVersion() {
        ImageObject imageObject = pending(NOW.plusSeconds(900));
        imageObject.increaseRowVersion("tsp-account", NOW.plusSeconds(10));
        assertEquals(2, imageObject.getRowVersion());
        assertEquals("tsp-account", imageObject.getModifyBy());
        assertEquals(NOW.plusSeconds(10), imageObject.getModifyTime());
    }

    @Test
    @DisplayName("恢复持久化状态时字段完整")
    void restoreKeepsState() {
        ImageObject imageObject = ImageObject.restore(9L, "object-id", StorageProvider.TENCENT_COS, "ap-shanghai",
                "bucket", "images/svc/object-id.jpg", "svc", "a.jpg", ImageMimeType.IMAGE_JPEG, 10L,
                ImageMimeType.IMAGE_JPEG, 12L, "etag", ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE,
                ImageStatus.UPLOADED, NOW, NOW, null, null, 3, "svc", NOW, "svc", NOW);
        assertEquals(9L, imageObject.getId());
        assertEquals(3, imageObject.getRowVersion());
        assertTrue(imageObject.isUploaded());
    }

    private ImageObject pending(Instant expireTime) {
        return ImageObject.pending("2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3", StorageProvider.TENCENT_COS,
                "ap-shanghai", "iov-public-1253442587",
                "images/tsp-account/2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3.jpg", "tsp-account", "avatar.jpg",
                ImageMimeType.IMAGE_JPEG, 204800L, ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE, expireTime,
                "tsp-account", NOW);
    }

    private ImageObject uploaded() {
        ImageObject imageObject = pending(NOW.plusSeconds(900));
        imageObject.markUploaded(ImageMimeType.IMAGE_JPEG, 204800L, "etag", NOW);
        return imageObject;
    }

}
