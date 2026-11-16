package net.hwyz.iov.cloud.edd.oss.service.integration;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.LogObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogContentType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogFailureReason;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogUploadState;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.query.LogObjectSearchCriteria;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.LogObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.repository.LogObjectRepositoryImpl;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志对象仓储真实 MySQL 集成测试（EDD-OSS-DSN-CR-003 §6、§12）。
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
@SpringBootTest(classes = LogObjectRepositoryIT.TestApplication.class,
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
class LogObjectRepositoryIT {

    private static final String CALLER = "it-ota";
    private static final String OTHER_CALLER = "it-other";
    private static final String SHA256 = "a".repeat(64);

    @Autowired
    private LogObjectRepository logObjectRepository;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void tearDown() {
        PageHelper.clearPage();
    }

    @Test
    @DisplayName("Flyway V3 可从 V1/V2 前向执行并创建 tb_log_object")
    void flywayMigrationApplied() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                             + "AND table_name = 'tb_log_object'");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = 1");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    @DisplayName("objectId、requestId 幂等键与 objectKey 唯一约束生效")
    void uniqueConstraints() {
        LogObject first = newAuthorized("dup-object", "request-dup", "logs/dup/object");
        logObjectRepository.insert(first);

        assertThrows(DuplicateKeyException.class,
                () -> logObjectRepository.insert(newAuthorized("dup-object", "request-dup-2", "logs/dup/object-2")));
        assertThrows(DuplicateKeyException.class,
                () -> logObjectRepository.insert(newAuthorized("dup-object-2", "request-dup", "logs/dup/object-3")));
        assertThrows(DuplicateKeyException.class,
                () -> logObjectRepository.insert(newAuthorized("dup-object-3", "request-dup-3", "logs/dup/object")));
    }

    @Test
    @DisplayName("按 objectId 与 (callerService,businessType,requestId) 查询，跨调用方不可见")
    void findByObjectIdAndRequest() {
        LogObject logObject = newAuthorized("query-object", "request-query", "logs/query/object");
        logObjectRepository.insert(logObject);

        assertTrue(logObjectRepository.findByObjectId("query-object").isPresent());
        assertTrue(logObjectRepository.findByRequest(CALLER, LogBusinessType.OTA_UPGRADE_LOG, "request-query")
                .isPresent());
        assertFalse(logObjectRepository.findByRequest(OTHER_CALLER, LogBusinessType.OTA_UPGRADE_LOG, "request-query")
                .isPresent());
    }

    @Test
    @DisplayName("receivedAt 仅首次写入，不被后续 complete 覆盖")
    void receivedAtNotOverwritten() {
        LogObject logObject = newAuthorized("received-object", "request-received", "logs/received/object");
        logObjectRepository.insert(logObject);

        Instant first = Instant.parse("2026-11-15T10:01:00Z");
        logObject.recordCompleteReceived(first);
        assertTrue(logObjectRepository.recordCompleteReceived(logObject));

        LogObject reloaded = logObjectRepository.findByObjectId("received-object").orElseThrow();
        assertEquals(first, reloaded.getReceivedAt());

        LogObject second = logObjectRepository.findByObjectId("received-object").orElseThrow();
        assertFalse(second.recordCompleteReceived(Instant.parse("2026-11-15T10:02:00Z")));
        assertFalse(logObjectRepository.recordCompleteReceived(second));
        assertEquals(first, logObjectRepository.findByObjectId("received-object").orElseThrow().getReceivedAt());
    }

    @Test
    @DisplayName("AUTHORIZED → UPLOADED 条件更新校验 row_version，旧版本更新失败")
    void conditionalUpload() {
        LogObject logObject = newAuthorized("upload-object", "request-upload", "logs/upload/object");
        logObjectRepository.insert(logObject);
        LogObject stale = logObjectRepository.findByObjectId("upload-object").orElseThrow();

        LogObject current = logObjectRepository.findByObjectId("upload-object").orElseThrow();
        current.markUploaded(1024L, SHA256, "\"etag\"", "application/gzip", Instant.now());
        assertTrue(logObjectRepository.updateAuthorizedToUploaded(current));

        LogObject reloaded = logObjectRepository.findByObjectId("upload-object").orElseThrow();
        assertEquals(LogUploadState.UPLOADED, reloaded.getUploadState());
        assertEquals(1024L, reloaded.getActualSize());
        assertEquals(SHA256, reloaded.getActualSha256());
        assertNotNull(reloaded.getUploadedAt());

        stale.markUploaded(1024L, SHA256, "\"etag\"", "application/gzip", Instant.now());
        assertFalse(logObjectRepository.updateAuthorizedToUploaded(stale));
    }

    @Test
    @DisplayName("AUTHORIZED → FAILED 保存稳定原因与观察元数据")
    void conditionalFailure() {
        LogObject logObject = newAuthorized("failed-object", "request-failed", "logs/failed/object");
        logObjectRepository.insert(logObject);
        LogObject current = logObjectRepository.findByObjectId("failed-object").orElseThrow();
        current.markFailed(LogFailureReason.SIZE_MISMATCH, 2048L, SHA256, "\"etag\"", Instant.now());
        assertTrue(logObjectRepository.updateAuthorizedToFailed(current));

        LogObject reloaded = logObjectRepository.findByObjectId("failed-object").orElseThrow();
        assertEquals(LogUploadState.FAILED, reloaded.getUploadState());
        assertEquals(LogFailureReason.SIZE_MISMATCH, reloaded.getFailureReason());
        assertEquals(2048L, reloaded.getActualSize());
        assertNull(reloaded.getUploadedAt());
    }

    @Test
    @DisplayName("过期：单对象条件更新与小批量扫描均只改状态，不删除历史")
    void expirationUpdatesStateOnly() {
        Instant past = Instant.now().minusSeconds(3600);
        LogObject expired = newAuthorized("expired-object", "request-expired", "logs/expired/object", past);
        logObjectRepository.insert(expired);
        LogObject current = logObjectRepository.findByObjectId("expired-object").orElseThrow();
        current.markExpired(Instant.now());
        assertTrue(logObjectRepository.updateAuthorizedToExpired(current));
        assertEquals(LogUploadState.EXPIRED,
                logObjectRepository.findByObjectId("expired-object").orElseThrow().getUploadState());

        LogObject scanTarget = newAuthorized("scan-object", "request-scan", "logs/scan/object", past);
        logObjectRepository.insert(scanTarget);
        assertTrue(logObjectRepository.expireAuthorizedBatch(Instant.now(), 100) >= 1);
        assertEquals(LogUploadState.EXPIRED,
                logObjectRepository.findByObjectId("scan-object").orElseThrow().getUploadState());
    }

    @Test
    @DisplayName("分页查询强制调用方隔离，过滤授权时间并固定 id DESC")
    void searchEnforcesCallerAndOrdering() {
        Instant now = Instant.now();
        logObjectRepository.insert(newAuthorized("search-1", "request-search-1", "logs/search/1", now));
        logObjectRepository.insert(newAuthorized("search-2", "request-search-2", "logs/search/2",
                now.plusSeconds(10)));
        logObjectRepository.insert(LogObject.authorized(UUID.randomUUID().toString(), StorageProvider.TENCENT_COS,
                "ap-shanghai", "it-log-bucket", OTHER_CALLER, LogBusinessType.OTA_UPGRADE_LOG, "task-other", null,
                "request-other", "other.log.gz", "logs/other/object", LogContentType.GZIP, 1024L, SHA256,
                "INTERNAL", now.plusSeconds(900), now));

        PageHelper.startPage(1, 10);
        List<LogObject> results = logObjectRepository.search(LogObjectSearchCriteria.builder()
                .callerService(CALLER)
                .businessType(LogBusinessType.OTA_UPGRADE_LOG)
                .businessId("task-1")
                .beginTime(now.minusSeconds(1))
                .endTime(now.plusSeconds(3600))
                .build());
        assertEquals(2, results.size());
        assertTrue(results instanceof Page<?>);
        assertEquals("search-2", results.get(0).getObjectId());
        assertEquals("search-1", results.get(1).getObjectId());
        assertFalse(results.stream().anyMatch(item -> OTHER_CALLER.equals(item.getCallerService())));

        PageHelper.startPage(1, 1);
        List<LogObject> firstPage = logObjectRepository.search(LogObjectSearchCriteria.builder()
                .callerService(CALLER)
                .build());
        assertEquals(1, firstPage.size());
        assertEquals(2, ((Page<?>) firstPage).getTotal());
    }

    @Test
    @DisplayName("仓储搜索缺少调用方时拒绝执行，避免全量查询")
    void searchRequiresCaller() {
        assertThrows(IllegalArgumentException.class,
                () -> logObjectRepository.search(LogObjectSearchCriteria.builder().build()));
    }

    private LogObject newAuthorized(String objectId, String requestId, String objectKey) {
        return newAuthorized(objectId, requestId, objectKey, Instant.now());
    }

    private LogObject newAuthorized(String objectId, String requestId, String objectKey, Instant authorizedAt) {
        return LogObject.authorized(objectId, StorageProvider.TENCENT_COS, "ap-shanghai", "it-log-bucket", CALLER,
                LogBusinessType.OTA_UPGRADE_LOG, "task-1", "execution-1", requestId, "upgrade.log.gz", objectKey,
                LogContentType.GZIP, 1024L, SHA256, "INTERNAL", authorizedAt.plusSeconds(900), authorizedAt);
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            TransactionAutoConfiguration.class, FlywayAutoConfiguration.class, MyBatisPlusConfig.class})
    @MapperScan("net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.mapper")
    @ComponentScan(basePackageClasses = LogObjectRepositoryImpl.class)
    @Import(MyBatisMetaObjectHandler.class)
    static class TestApplication {
    }

}
