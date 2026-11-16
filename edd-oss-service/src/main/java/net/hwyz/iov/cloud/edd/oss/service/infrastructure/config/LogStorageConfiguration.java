package net.hwyz.iov.cloud.edd.oss.service.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 日志对象基础设施配置（EDD-OSS-DSN-CR-003 §7.2、§9.3）。
 * <p>
 * 启动时强制校验私有日志 Bucket 与配置区间，避免误用公有读图片 Bucket；
 * 同时启用过期扫描定时任务。
 *
 * @author hwyz_leo
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class LogStorageConfiguration {

    private final LogProperties logProperties;
    private final CosProperties cosProperties;

    /**
     * 启动校验：日志 Bucket 必填、不得等于图片 Bucket，TTL 与扫描参数必须合法。
     */
    @PostConstruct
    public void validate() {
        String logBucket = logProperties.getBucket();
        if (logBucket == null || logBucket.isBlank()) {
            throw new IllegalStateException("oss.log.bucket is required");
        }
        if (logBucket.equals(cosProperties.getBucket())) {
            throw new IllegalStateException("oss.log.bucket must not equal the image bucket oss.storage.cos.bucket");
        }
        if (logProperties.getUploadExpirationSeconds() > logProperties.getMaxUploadExpirationSeconds()) {
            throw new IllegalStateException(
                    "oss.log.upload-expiration-seconds must not exceed max-upload-expiration-seconds");
        }
        if (logProperties.getExpirationScanBatchSize() <= 0) {
            throw new IllegalStateException("oss.log.expiration-scan-batch-size must be greater than 0");
        }
        log.info("日志对象存储配置校验通过，私有日志 Bucket 已与图片 Bucket 隔离");
    }

}
