package net.hwyz.iov.cloud.edd.oss.service.integration;

import com.github.pagehelper.PageHelper;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ObjectAccessPermission;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.query.ImageObjectSearchCriteria;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.repository.ImageObjectRepositoryImpl;
import net.hwyz.iov.cloud.framework.mysql.config.MyBatisMetaObjectHandler;
import net.hwyz.iov.cloud.framework.mysql.config.MyBatisPlusConfig;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片对象仓储真实 MySQL 集成测试（EDD-OSS-DSN-CR-001 §15.4）。
 * <p>
 * 需要本地或 CI MySQL 提供 {@code db_oss}；执行方式：
 * <pre>
 * mvn test -Dit.mysql.password=&lt;password&gt; [-Dit.mysql.username=root] \
 *          [-Dit.mysql.host=127.0.0.1] [-Dit.mysql.db=db_oss]
 * </pre>
 * 未提供密码时显式跳过（不伪造通过）。
 *
 * @author hwyz_leo
 */
@EnabledIfSystemProperty(named = "it.mysql.password", matches = ".+")
@SpringBootTest(classes = ImageObjectRepositoryIT.TestApplication.class,
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
class ImageObjectRepositoryIT {

    private static final String SERVICE_NAME = "it-service";

    @Autowired
    private ImageObjectRepository imageObjectRepository;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void tearDown() {
        PageHelper.clearPage();
    }

    @Test
    @DisplayName("Flyway V1 可从空库执行并创建 tb_image_object")
    void flywayMigrationApplied() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                             + "AND table_name = 'tb_image_object'");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    @DisplayName("objectId 与 (bucketName, objectKey) 唯一约束生效")
    void uniqueConstraints() {
        ImageObject first = newPending("dup-object", "images/it-service/dup-object.jpg");
        imageObjectRepository.insert(first);
        assertThrows(DuplicateKeyException.class, () -> imageObjectRepository.insert(
                newPending("dup-object", "images/it-service/dup-object-2.jpg")));
        assertThrows(DuplicateKeyException.class, () -> imageObjectRepository.insert(
                newPending("dup-object-3", "images/it-service/dup-object.jpg")));
    }

    @Test
    @DisplayName("PENDING → UPLOADED 条件更新校验状态与 row_version")
    void updatePendingToUploaded() {
        ImageObject imageObject = imageObjectRepository.insert(newPending(uniqueId(), uniqueKey()));
        assertTrue(imageObject.isPending());
        imageObject.markUploaded(ImageMimeType.IMAGE_JPEG, 1024L, "etag-it", Instant.now());
        assertTrue(imageObjectRepository.updatePendingToUploaded(imageObject));
        assertEquals(2, imageObject.getRowVersion());

        ImageObject persisted = imageObjectRepository.findByObjectId(imageObject.getObjectId()).orElseThrow();
        assertEquals(ImageStatus.UPLOADED, persisted.getStatus());
        assertEquals(1024L, persisted.getActualSize());
        assertEquals(ImageMimeType.IMAGE_JPEG, persisted.getActualContentType());
        assertEquals("etag-it", persisted.getEtag());
        assertNotNull(persisted.getUploadTime());
        assertEquals(2, persisted.getRowVersion());

        // 过期版本再次更新必须失败（row_version 条件不满足）
        assertFalse(imageObjectRepository.updatePendingToUploaded(imageObject));
    }

    @Test
    @DisplayName("PENDING → DELETED 条件更新校验状态与 row_version（BUGFIX-001）")
    void updatePendingToDeleted() {
        ImageObject imageObject = imageObjectRepository.insert(newPending(uniqueId(), uniqueKey()));
        imageObject.markDeleted("1001", Instant.now());
        assertTrue(imageObjectRepository.updatePendingToDeleted(imageObject));
        assertEquals(2, imageObject.getRowVersion());

        ImageObject persisted = imageObjectRepository.findByObjectId(imageObject.getObjectId()).orElseThrow();
        assertEquals(ImageStatus.DELETED, persisted.getStatus());
        assertEquals("1001", persisted.getDeleteBy());
        assertNotNull(persisted.getDeleteTime());
        assertNull(persisted.getActualSize());
        assertEquals(2, persisted.getRowVersion());

        // 状态已非 PENDING，再次条件更新必须失败
        assertFalse(imageObjectRepository.updatePendingToDeleted(imageObject));
    }

    @Test
    @DisplayName("UPLOADED → DELETED 条件更新保留 row_valid=1 与全部元数据")
    void updateUploadedToDeleted() throws Exception {
        ImageObject imageObject = imageObjectRepository.insert(newPending(uniqueId(), uniqueKey()));
        imageObject.markUploaded(ImageMimeType.IMAGE_PNG, 2048L, "etag-delete", Instant.now());
        assertTrue(imageObjectRepository.updatePendingToUploaded(imageObject));
        imageObject.markDeleted("1001", Instant.now());
        assertTrue(imageObjectRepository.updateUploadedToDeleted(imageObject));

        ImageObject persisted = imageObjectRepository.findByObjectId(imageObject.getObjectId()).orElseThrow();
        assertEquals(ImageStatus.DELETED, persisted.getStatus());
        assertEquals("1001", persisted.getDeleteBy());
        assertNotNull(persisted.getDeleteTime());
        assertEquals(2048L, persisted.getActualSize());
        assertEquals(3, persisted.getRowVersion());

        Connection connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(
                     "SELECT row_valid FROM tb_image_object WHERE object_id = ?")) {
            statement.setString(1, imageObject.getObjectId());
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
            }
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Test
    @DisplayName("PENDING 状态下 UPLOADED → DELETED 条件更新返回失败")
    void deleteRequiresUploaded() {
        ImageObject imageObject = imageObjectRepository.insert(newPending(uniqueId(), uniqueKey()));
        assertFalse(imageObjectRepository.updateUploadedToDeleted(imageObject));
        ImageObject persisted = imageObjectRepository.findByObjectId(imageObject.getObjectId()).orElseThrow();
        assertEquals(ImageStatus.PENDING, persisted.getStatus());
        assertNull(persisted.getDeleteTime());
    }

    @Test
    @DisplayName("默认筛选排除 DELETED，显式 DELETED 可查询历史记录")
    void defaultAndExplicitStatusFilter() {
        ImageObject pending = imageObjectRepository.insert(newPending(uniqueId(), uniqueKey()));
        ImageObject uploaded = insertUploaded(uniqueId(), uniqueKey());
        ImageObject deleted = insertUploaded(uniqueId(), uniqueKey());
        deleted.markDeleted("1001", Instant.now());
        assertTrue(imageObjectRepository.updateUploadedToDeleted(deleted));

        List<ImageObject> defaults = imageObjectRepository.search(ImageObjectSearchCriteria.builder()
                .serviceName(SERVICE_NAME).build());
        assertTrue(defaults.stream().map(ImageObject::getObjectId).toList()
                .containsAll(List.of(pending.getObjectId(), uploaded.getObjectId())));
        assertFalse(defaults.stream().map(ImageObject::getObjectId).toList().contains(deleted.getObjectId()));

        List<ImageObject> deletedList = imageObjectRepository.search(ImageObjectSearchCriteria.builder()
                .serviceName(SERVICE_NAME).status(ImageStatus.DELETED).build());
        assertEquals(List.of(deleted.getObjectId()),
                deletedList.stream().map(ImageObject::getObjectId).filter(id -> id.equals(deleted.getObjectId()))
                        .toList());
    }

    @Test
    @DisplayName("contentType 优先匹配实际 MIME，未完成时匹配声明 MIME")
    void contentTypeFilter() {
        ImageObject pending = imageObjectRepository.insert(newPending(uniqueId(), uniqueKey()));
        ImageObject uploaded = insertUploaded(uniqueId(), uniqueKey());

        List<String> ids = imageObjectRepository.search(ImageObjectSearchCriteria.builder()
                        .serviceName(SERVICE_NAME).contentType("image/jpeg").build()).stream()
                .map(ImageObject::getObjectId).toList();
        assertTrue(ids.contains(pending.getObjectId()));
        assertTrue(ids.contains(uploaded.getObjectId()));
    }

    @Test
    @DisplayName("objectKey 精确匹配、原始文件名前缀匹配并转义 LIKE 通配符")
    void keyAndFilenameFilter() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ImageObject underscore = imageObjectRepository.insert(
                newPending(uniqueId(), uniqueKey(), "a_b_" + suffix + ".jpg"));
        ImageObject plain = imageObjectRepository.insert(
                newPending(uniqueId(), uniqueKey(), "axb_" + suffix + ".jpg"));

        List<String> ids = imageObjectRepository.search(ImageObjectSearchCriteria.builder()
                        .serviceName(SERVICE_NAME).originalFilenamePrefix("a_b_" + suffix).build()).stream()
                .map(ImageObject::getObjectId).toList();
        assertTrue(ids.contains(underscore.getObjectId()));
        assertFalse(ids.contains(plain.getObjectId()));

        List<String> byKey = imageObjectRepository.search(ImageObjectSearchCriteria.builder()
                        .serviceName(SERVICE_NAME).objectKey(underscore.getObjectKey()).build()).stream()
                .map(ImageObject::getObjectId).toList();
        assertEquals(List.of(underscore.getObjectId()), byKey);
    }

    @Test
    @DisplayName("上传时间范围筛选排除 PENDING 记录")
    void uploadTimeRangeFilter() {
        ImageObject pending = imageObjectRepository.insert(newPending(uniqueId(), uniqueKey()));
        ImageObject uploaded = insertUploaded(uniqueId(), uniqueKey());
        Instant begin = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS);
        List<String> ids = imageObjectRepository.search(ImageObjectSearchCriteria.builder()
                        .serviceName(SERVICE_NAME).uploadBeginTime(begin).uploadEndTime(end).build()).stream()
                .map(ImageObject::getObjectId).toList();
        assertTrue(ids.contains(uploaded.getObjectId()));
        assertFalse(ids.contains(pending.getObjectId()));
    }

    @Test
    @DisplayName("分页由数据库执行：总数、页大小与 id DESC 稳定排序")
    void physicalPagination() {
        for (int index = 0; index < 3; index++) {
            insertUploaded(uniqueId(), uniqueKey());
        }
        PageHelper.startPage(1, 2);
        List<ImageObject> page = imageObjectRepository.search(ImageObjectSearchCriteria.builder()
                .serviceName(SERVICE_NAME).status(ImageStatus.UPLOADED).build());
        assertTrue(page instanceof com.github.pagehelper.Page<?>);
        com.github.pagehelper.Page<?> pageResult = (com.github.pagehelper.Page<?>) page;
        assertEquals(2, pageResult.size());
        assertTrue(pageResult.getTotal() >= 3);
        List<Long> ids = page.stream().map(ImageObject::getId).toList();
        assertEquals(2, ids.size());
        assertTrue(ids.get(0) > ids.get(1));
    }

    private ImageObject insertUploaded(String objectId, String objectKey) {
        ImageObject imageObject = imageObjectRepository.insert(newPending(objectId, objectKey));
        imageObject.markUploaded(ImageMimeType.IMAGE_JPEG, 1024L, "etag", Instant.now());
        assertTrue(imageObjectRepository.updatePendingToUploaded(imageObject));
        return imageObject;
    }

    private ImageObject newPending(String objectId, String objectKey) {
        return newPending(objectId, objectKey, "avatar.jpg");
    }

    private ImageObject newPending(String objectId, String objectKey, String originalFilename) {
        return ImageObject.pending(objectId, StorageProvider.TENCENT_COS, "ap-shanghai",
                "iov-public-1253442587", objectKey, SERVICE_NAME, originalFilename, ImageMimeType.IMAGE_JPEG,
                204800L, ObjectAccessPermission.PUBLIC_READ_PRIVATE_WRITE, Instant.now().plusSeconds(900),
                SERVICE_NAME, Instant.now());
    }

    private String uniqueId() {
        return UUID.randomUUID().toString();
    }

    private String uniqueKey() {
        return "images/it-service/" + UUID.randomUUID() + ".jpg";
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
    @ComponentScan(basePackageClasses = ImageObjectRepositoryImpl.class)
    @Import({MyBatisPlusConfig.class, MyBatisMetaObjectHandler.class})
    @MapperScan("net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.mapper")
    static class TestApplication {
    }

}
