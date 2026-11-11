package net.hwyz.iov.cloud.edd.oss.service.domain;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageReplacementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 替换任务状态机单元测试（EDD-OSS-DSN-CR-002 §15.7.1）。
 *
 * @author hwyz_leo
 */
class ImageReplacementTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final String OBJECT_ID = "2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3";
    private static final String REPLACEMENT_ID = "fb62a661-946f-49b1-8268-a65991d44184";

    @Test
    @DisplayName("创建得到 PENDING，并以 activeObjectId 占用活动任务位")
    void pendingCreation() {
        ImageReplacement replacement = pending();
        assertEquals(ImageReplacementStatus.PENDING, replacement.getStatus());
        assertEquals(OBJECT_ID, replacement.getActiveObjectId());
        assertEquals(1, replacement.getRowVersion());
        assertNull(replacement.getActualContentType());
        assertNull(replacement.getActualSize());
        assertTrue(replacement.isPending());
    }

    @Test
    @DisplayName("PENDING → PROMOTING 写入临时对象实际元数据")
    void startPromoting() {
        ImageReplacement replacement = pending();
        replacement.startPromoting(ImageMimeType.IMAGE_PNG, 307200L, "staging-etag", "1001", NOW);
        assertTrue(replacement.isPromoting());
        assertEquals(ImageMimeType.IMAGE_PNG, replacement.getActualContentType());
        assertEquals(307200L, replacement.getActualSize());
        assertEquals("staging-etag", replacement.getEtag());
        assertEquals(OBJECT_ID, replacement.getActiveObjectId());
    }

    @Test
    @DisplayName("PROMOTING → COMPLETED 清空活动标识")
    void markCompleted() {
        ImageReplacement replacement = promoting();
        replacement.markCompleted("1001", NOW);
        assertTrue(replacement.isCompleted());
        assertNull(replacement.getActiveObjectId());
    }

    @Test
    @DisplayName("PROMOTING → FAILED 清空活动标识并保留故障说明")
    void markFailed() {
        ImageReplacement replacement = promoting();
        replacement.markFailed("promote failed", "1001", NOW);
        assertTrue(replacement.isFailed());
        assertNull(replacement.getActiveObjectId());
        assertEquals("promote failed", replacement.getDescription());
    }

    @Test
    @DisplayName("PENDING → CANCELLED 清空活动标识")
    void cancel() {
        ImageReplacement replacement = pending();
        replacement.cancel("1001", NOW);
        assertTrue(replacement.isCancelled());
        assertNull(replacement.getActiveObjectId());
    }

    @Test
    @DisplayName("非法逆向或跳转状态被拒绝")
    void rejectsIllegalTransitions() {
        ImageReplacement replacement = pending();
        assertThrows(IllegalStateException.class, () -> replacement.markCompleted("1001", NOW));
        assertThrows(IllegalStateException.class, () -> replacement.markFailed("x", "1001", NOW));
        replacement.startPromoting(ImageMimeType.IMAGE_PNG, 10L, "etag", "1001", NOW);
        assertThrows(IllegalStateException.class, () -> replacement.cancel("1001", NOW));
        assertThrows(IllegalStateException.class,
                () -> replacement.startPromoting(ImageMimeType.IMAGE_PNG, 10L, "etag", "1001", NOW));
    }

    @Test
    @DisplayName("恢复未确认时保留 PROMOTING、活动标识并记录故障说明")
    void recordFaultKeepsPromoting() {
        ImageReplacement replacement = promoting();
        replacement.recordFault("restore not confirmed", "1001", NOW);
        assertTrue(replacement.isPromoting());
        assertEquals(OBJECT_ID, replacement.getActiveObjectId());
        assertEquals("restore not confirmed", replacement.getDescription());
    }

    @Test
    @DisplayName("过期只对 PENDING 生效，PROMOTING 不因过期丢弃故障现场")
    void expiry() {
        ImageReplacement pending = pending();
        assertTrue(pending.isExpired(NOW.plusSeconds(900)));
        assertFalse(pending.isExpired(NOW.plusSeconds(899)));
        ImageReplacement promoting = promoting();
        assertFalse(promoting.isExpired(NOW.plusSeconds(100000)));
    }

    @Test
    @DisplayName("条件更新成功后 rowVersion 递增并记录修改人")
    void increaseRowVersion() {
        ImageReplacement replacement = pending();
        replacement.increaseRowVersion("1001", NOW.plusSeconds(5));
        assertEquals(2, replacement.getRowVersion());
        assertEquals("1001", replacement.getModifyBy());
        assertEquals(NOW.plusSeconds(5), replacement.getModifyTime());
    }

    @Test
    @DisplayName("恢复持久化状态时字段完整")
    void restoreKeepsState() {
        ImageReplacement replacement = ImageReplacement.restore(7L, REPLACEMENT_ID, OBJECT_ID, null,
                stagingKey(), backupKey(), "avatar-v2.png", ImageMimeType.IMAGE_PNG, 307200L,
                ImageMimeType.IMAGE_PNG, 300000L, "etag", ImageReplacementStatus.COMPLETED, NOW,
                null, 3, "1001", NOW, "1001", NOW);
        assertEquals(7L, replacement.getId());
        assertEquals(3, replacement.getRowVersion());
        assertTrue(replacement.isCompleted());
        assertNull(replacement.getActiveObjectId());
    }

    private ImageReplacement pending() {
        return ImageReplacement.pending(REPLACEMENT_ID, OBJECT_ID, stagingKey(), backupKey(), "avatar-v2.png",
                ImageMimeType.IMAGE_PNG, 307200L, NOW.plusSeconds(900), "1001", NOW);
    }

    private ImageReplacement promoting() {
        ImageReplacement replacement = pending();
        replacement.startPromoting(ImageMimeType.IMAGE_PNG, 300000L, "staging-etag", "1001", NOW);
        return replacement;
    }

    private String stagingKey() {
        return "images/edd-oss/replacements/" + OBJECT_ID + "/" + REPLACEMENT_ID + ".png";
    }

    private String backupKey() {
        return "images/edd-oss/replacement-backups/" + OBJECT_ID + "/" + REPLACEMENT_ID + ".png";
    }

}
