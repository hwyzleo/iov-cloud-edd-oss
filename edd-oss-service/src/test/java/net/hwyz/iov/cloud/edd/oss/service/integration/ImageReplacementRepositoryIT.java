package net.hwyz.iov.cloud.edd.oss.service.integration;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageReplacementStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ObjectAccessPermission;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageReplacementRepository;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.repository.ImageObjectRepositoryImpl;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.repository.ImageReplacementRepositoryImpl;
import net.hwyz.iov.cloud.framework.mysql.config.MyBatisMetaObjectHandler;
import net.hwyz.iov.cloud.framework.mysql.config.MyBatisPlusConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片替换任务仓储真实 MySQL 集成测试（EDD-OSS-DSN-CR-002 §15.7.3）。
 * <p>
 * 执行方式：
 * <pre>
 * mvn test -Dit.mysql.password=&lt;password&gt; [-Dit.mysql.username=root] \
 *          [-Dit.mysql.host=127.0.0.1] [-Dit.mysql.db=db_oss]
 * </pre>
 * 未提供密码时显式跳过（不伪造通过）。
 *
 * @author hwyz_leo
 */
@EnabledIfSystemProperty(named = "it.mysql.password", matches = ".+")
@SpringBootTest(classes = ImageReplacementRepositoryIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.datasource.url=jdbc:mysql://${it.mysql.host:127.0.0.1}:3306/${it.mysql.db:db_oss}"
                        + "?useUnicode=true&characterEncoding=utf8&allowMultiQueries=true"
                        + "&allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC"
                        + "&forceConnectionTimeZoneToSession=true&preserveInstants=true",
                "spring.datasource.username=${it.mysql.username:root}",
                "spring.datasource.password=${it.mysql.password:}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.baseline-on-migrate=true",
                "spring.flyway.baseline-version=0",
                "spring.flyway.table=flyway_schema_history",
                "spring.flyway.validate-on-migrate=true"
        })
@Transactional
class ImageReplacementRepositoryIT {

    private static final String BUCKET = "iov-public-1253442587";

    @Autowired
    private ImageObjectRepository imageObjectRepository;

    @Autowired
    private ImageReplacementRepository imageReplacementRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Flyway V2 创建 asset_name 与 tb_image_replacement")
    void flywayMigrationApplied() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                             + "AND table_name = 'tb_image_object' AND column_name = 'asset_name'");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                             + "AND table_name = 'tb_image_replacement'");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = 1");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    @DisplayName("active_object_id 唯一约束保证同一图片最多一个活动任务")
    void activeObjectIdUnique() {
        String objectId = uniqueId();
        imageReplacementRepository.insert(newPending(objectId));
        assertThrows(DuplicateKeyException.class, () -> imageReplacementRepository.insert(newPending(objectId)));
    }

    @Test
    @DisplayName("终态清空 active_object_id 后允许多个 NULL 并存")
    void terminalStatesReleaseActiveSlot() {
        String objectId = uniqueId();
        ImageReplacement first = imageReplacementRepository.insert(newPending(objectId));
        first.cancel("1001", Instant.now());
        assertTrue(imageReplacementRepository.updatePendingToCancelled(first));
        assertNull(imageReplacementRepository.findByReplacementId(first.getReplacementId()).orElseThrow()
                .getActiveObjectId());

        ImageReplacement second = imageReplacementRepository.insert(newPending(objectId));
        second.cancel("1001", Instant.now());
        assertTrue(imageReplacementRepository.updatePendingToCancelled(second));

        assertEquals(2, countByObjectId(objectId));
    }

    @Test
    @DisplayName("临时与备份对象键唯一约束生效")
    void objectKeyUnique() {
        ImageReplacement first = imageReplacementRepository.insert(newPending(uniqueId()));
        ImageReplacement duplicateStaging = ImageReplacement.pending(uniqueId(), uniqueId(),
                first.getStagingObjectKey(), backupKey(), "v2.png", ImageMimeType.IMAGE_PNG, 1024L,
                Instant.now().plusSeconds(900), "1001", Instant.now());
        assertThrows(DuplicateKeyException.class, () -> imageReplacementRepository.insert(duplicateStaging));

        ImageReplacement duplicateBackup = ImageReplacement.pending(uniqueId(), uniqueId(), stagingKey(),
                first.getBackupObjectKey(), "v2.png", ImageMimeType.IMAGE_PNG, 1024L,
                Instant.now().plusSeconds(900), "1001", Instant.now());
        assertThrows(DuplicateKeyException.class, () -> imageReplacementRepository.insert(duplicateBackup));
    }

    @Test
    @DisplayName("PENDING → PROMOTING 条件更新校验状态与 row_version")
    void updatePendingToPromoting() {
        ImageReplacement replacement = imageReplacementRepository.insert(newPending(uniqueId()));
        replacement.startPromoting(ImageMimeType.IMAGE_PNG, 1024L, "staging-etag", "1001", Instant.now());
        assertTrue(imageReplacementRepository.updatePendingToPromoting(replacement));
        assertEquals(2, replacement.getRowVersion());

        ImageReplacement persisted = imageReplacementRepository.findByReplacementId(replacement.getReplacementId())
                .orElseThrow();
        assertEquals(ImageReplacementStatus.PROMOTING, persisted.getStatus());
        assertEquals(1024L, persisted.getActualSize());
        assertEquals("staging-etag", persisted.getEtag());
        assertEquals(2, persisted.getRowVersion());

        assertFalse(imageReplacementRepository.updatePendingToPromoting(replacement));
    }

    @Test
    @DisplayName("PROMOTING → COMPLETED 清空活动标识且不物理删除")
    void updatePromotingToCompleted() {
        ImageReplacement replacement = promotingReplacement();
        replacement.markCompleted("1001", Instant.now());
        assertTrue(imageReplacementRepository.updatePromotingToCompleted(replacement));

        ImageReplacement persisted = imageReplacementRepository.findByReplacementId(replacement.getReplacementId())
                .orElseThrow();
        assertEquals(ImageReplacementStatus.COMPLETED, persisted.getStatus());
        assertNull(persisted.getActiveObjectId());
        assertEquals(3, persisted.getRowVersion());
        assertTrue(imageReplacementRepository.findActiveByObjectId(persisted.getObjectId()).isEmpty());
        assertEquals(1, countByObjectId(persisted.getObjectId()));
    }

    @Test
    @DisplayName("恢复未确认时仅更新故障说明并保留 PROMOTING 与活动标识")
    void updateFaultDescription() {
        ImageReplacement replacement = promotingReplacement();
        replacement.recordFault("restore not confirmed", "1001", Instant.now());
        assertTrue(imageReplacementRepository.updateFaultDescription(replacement));

        ImageReplacement persisted = imageReplacementRepository.findByReplacementId(replacement.getReplacementId())
                .orElseThrow();
        assertEquals(ImageReplacementStatus.PROMOTING, persisted.getStatus());
        assertEquals("restore not confirmed", persisted.getDescription());
        assertEquals(persisted.getObjectId(), persisted.getActiveObjectId());
    }

    @Test
    @DisplayName("默认素材 asset_name 与备注可持久化，业务图片保持为空")
    void assetNamePersistence() {
        ImageObject asset = ImageObject.pending(uniqueId(), StorageProvider.TENCENT_COS, "ap-shanghai", BUCKET,
                "images/edd-oss/" + UUID.randomUUID() + ".png", ImageObject.DEFAULT_ASSET_SERVICE_NAME,
                "默认用户头像", "未设置头像时使用", "avatar.png", ImageMimeType.IMAGE_PNG, 204800L,
                ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE, Instant.now().plusSeconds(900), "1001",
                Instant.now());
        imageObjectRepository.insert(asset);
        ImageObject persisted = imageObjectRepository.findByObjectId(asset.getObjectId()).orElseThrow();
        assertEquals("默认用户头像", persisted.getAssetName());
        assertEquals("未设置头像时使用", persisted.getDescription());
        assertTrue(persisted.isDefaultAsset());

        ImageObject business = ImageObject.pending(uniqueId(), StorageProvider.TENCENT_COS, "ap-shanghai", BUCKET,
                "images/it-service/" + UUID.randomUUID() + ".png", "it-service", "avatar.png",
                ImageMimeType.IMAGE_PNG, 204800L, ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE,
                Instant.now().plusSeconds(900), "it-service", Instant.now());
        imageObjectRepository.insert(business);
        ImageObject persistedBusiness = imageObjectRepository.findByObjectId(business.getObjectId()).orElseThrow();
        assertNull(persistedBusiness.getAssetName());
        assertNull(persistedBusiness.getDescription());
        assertFalse(persistedBusiness.isDefaultAsset());
    }

    @Test
    @DisplayName("素材名称与备注条件更新持久化")
    void assetUpdatePersists() {
        ImageObject imageObject = uploadedDefaultAsset();
        imageObject.modifyAsset("默认用户头像（新）", "更新后的备注", "1001", Instant.now());
        assertTrue(imageObjectRepository.updateUploadedAsset(imageObject));

        ImageObject persisted = imageObjectRepository.findByObjectId(imageObject.getObjectId()).orElseThrow();
        assertEquals("默认用户头像（新）", persisted.getAssetName());
        assertEquals("更新后的备注", persisted.getDescription());
        assertEquals(3, persisted.getRowVersion());
    }

    @Test
    @DisplayName("图片元数据更新与替换任务完成在同一本地事务提交")
    void replacementCommitIsAtomic() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement replacement = promotingReplacement(imageObject.getObjectId());
        Instant now = Instant.now();
        imageObject.markReplaced("avatar-v2.png", 307200L, ImageMimeType.IMAGE_PNG, 300000L, "new-etag", "1001", now);
        replacement.markCompleted("1001", now);

        transactionTemplate.executeWithoutResult(status -> {
            assertTrue(imageObjectRepository.updateUploadedToReplaced(imageObject));
            assertTrue(imageReplacementRepository.updatePromotingToCompleted(replacement));
        });

        ImageObject storedImage = imageObjectRepository.findByObjectId(imageObject.getObjectId()).orElseThrow();
        assertEquals("avatar-v2.png", storedImage.getOriginalFilename());
        assertEquals(300000L, storedImage.getActualSize());
        assertEquals("new-etag", storedImage.getEtag());
        assertEquals(ImageReplacementStatus.COMPLETED,
                imageReplacementRepository.findByReplacementId(replacement.getReplacementId()).orElseThrow()
                        .getStatus());
    }

    @Test
    @DisplayName("最终事务任一项失败时整体回滚")
    void replacementCommitRollsBackOnFailure() {
        ImageObject imageObject = uploadedDefaultAsset();
        ImageReplacement replacement = promotingReplacement(imageObject.getObjectId());
        Instant now = Instant.now();
        imageObject.markReplaced("avatar-v2.png", 307200L, ImageMimeType.IMAGE_PNG, 300000L, "new-etag", "1001", now);
        replacement.markCompleted("1001", now);

        TransactionTemplate nested = new TransactionTemplate(transactionManager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        nested.executeWithoutResult(status -> {
            assertTrue(imageObjectRepository.updateUploadedToReplaced(imageObject));
            status.setRollbackOnly();
        });

        ImageObject storedImage = imageObjectRepository.findByObjectId(imageObject.getObjectId()).orElseThrow();
        assertEquals("avatar.png", storedImage.getOriginalFilename());
        assertEquals(204800L, storedImage.getActualSize());
        assertEquals(ImageReplacementStatus.PROMOTING,
                imageReplacementRepository.findByReplacementId(replacement.getReplacementId()).orElseThrow()
                        .getStatus());
        assertNotNull(storedImage.getAssetName());
    }

    private ImageReplacement promotingReplacement() {
        return promotingReplacement(uniqueId());
    }

    private ImageReplacement promotingReplacement(String objectId) {
        ImageReplacement replacement = imageReplacementRepository.insert(newPending(objectId));
        replacement.startPromoting(ImageMimeType.IMAGE_PNG, 300000L, "staging-etag", "1001", Instant.now());
        assertTrue(imageReplacementRepository.updatePendingToPromoting(replacement));
        return replacement;
    }

    private ImageReplacement newPending(String objectId) {
        String replacementId = uniqueId();
        return ImageReplacement.pending(replacementId, objectId,
                "images/edd-oss/replacements/" + objectId + "/" + replacementId + ".png",
                "images/edd-oss/replacement-backups/" + objectId + "/" + replacementId + ".png", "avatar-v2.png",
                ImageMimeType.IMAGE_PNG, 307200L, Instant.now().plusSeconds(900), "1001", Instant.now());
    }

    private ImageObject uploadedDefaultAsset() {
        ImageObject imageObject = ImageObject.pending(uniqueId(), StorageProvider.TENCENT_COS, "ap-shanghai", BUCKET,
                "images/edd-oss/" + UUID.randomUUID() + ".png", ImageObject.DEFAULT_ASSET_SERVICE_NAME,
                "默认用户头像", "未设置头像时使用", "avatar.png", ImageMimeType.IMAGE_PNG, 204800L,
                ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE, Instant.now().plusSeconds(900), "1001",
                Instant.now());
        imageObjectRepository.insert(imageObject);
        imageObject.markUploaded(ImageMimeType.IMAGE_PNG, 204800L, "old-etag", Instant.now());
        assertTrue(imageObjectRepository.updatePendingToUploaded(imageObject));
        return imageObject;
    }

    private int countByObjectId(String objectId) {
        Connection connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tb_image_replacement WHERE object_id = ?")) {
            statement.setString(1, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private String uniqueId() {
        return UUID.randomUUID().toString();
    }

    private String stagingKey() {
        return "images/edd-oss/replacements/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".png";
    }

    private String backupKey() {
        return "images/edd-oss/replacement-backups/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".png";
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusLanguageDriverAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration.class
    })
    @ComponentScan(basePackageClasses = {ImageObjectRepositoryImpl.class, ImageReplacementRepositoryImpl.class})
    @Import({MyBatisPlusConfig.class, MyBatisMetaObjectHandler.class})
    @MapperScan("net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.mapper")
    static class TestApplication {
    }

}
