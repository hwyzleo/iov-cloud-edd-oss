package net.hwyz.iov.cloud.edd.oss.service.integration;

import net.hwyz.iov.cloud.edd.oss.service.Application;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.controller.mpt.MptImageController;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.controller.service.ServiceImageController;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectStorageGateway;
import net.hwyz.iov.cloud.edd.oss.service.application.service.ImageAppService;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageObjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 应用装配冒烟测试：验证真实 Spring 上下文可启动、关键 Bean 可装配。
 * <p>
 * 需要真实 MySQL（Flyway 执行）与 Nacos；未提供 {@code it.mysql.password} 时显式跳过。
 *
 * @author hwyz_leo
 */
@EnabledIfSystemProperty(named = "it.mysql.password", matches = ".+")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:mysql://${it.mysql.host:127.0.0.1}:3306/${it.mysql.db:db_oss}"
                + "?useUnicode=true&characterEncoding=utf8&allowMultiQueries=true"
                + "&allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true&preserveInstants=true",
        "spring.datasource.username=${it.mysql.username:root}",
        "spring.datasource.password=${it.mysql.password:}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "oss.storage.cos.region=ap-shanghai",
        "oss.storage.cos.bucket=it-bucket",
        "oss.storage.cos.secret-id=it-secret-id",
        "oss.storage.cos.secret-key=it-secret-key",
        "spring.kafka.listener.auto-startup=false"
})
class ApplicationContextIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("关键 Bean 与控制器装配完整")
    void contextLoads() {
        assertNotNull(applicationContext.getBean(ImageAppService.class));
        assertNotNull(applicationContext.getBean(ImageObjectRepository.class));
        assertNotNull(applicationContext.getBean(ObjectStorageGateway.class));
        assertNotNull(applicationContext.getBean(ServiceImageController.class));
        assertNotNull(applicationContext.getBean(MptImageController.class));
        // 框架新增的 X-Caller-Service 自动配置必须在服务内生效
        assertNotNull(applicationContext.getBean("serviceCallerFeignInterceptor"));
    }

}
