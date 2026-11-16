package net.hwyz.iov.cloud.edd.oss.service.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 日志对象存储配置（EDD-OSS-DSN-CR-003 §7.2）。
 * <p>
 * 私有日志 Bucket 必填且不得与图片 Bucket 相同；region/Secret 复用
 * {@code oss.storage.cos.*}，不新增 OTA 侧密钥。
 *
 * @author hwyz_leo
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "oss.log")
public class LogProperties {

    /**
     * 独立私有日志 Bucket
     */
    @NotBlank
    private String bucket;
    /**
     * 默认上传授权 TTL（秒）
     */
    @NotNull
    @Min(1)
    private Long uploadExpirationSeconds = 900L;
    /**
     * 上传授权 TTL 上限（秒）
     */
    @NotNull
    @Min(1)
    private Long maxUploadExpirationSeconds = 3600L;
    /**
     * 单对象最大字节数，默认 512 MiB
     */
    @NotNull
    @Min(1)
    private Long maxSizeBytes = 536870912L;
    /**
     * 短期下载地址 TTL（秒）
     */
    @NotNull
    @Min(1)
    private Long downloadExpirationSeconds = 300L;
    /**
     * 过期扫描间隔（秒）
     */
    @NotNull
    @Min(1)
    private Long expirationScanIntervalSeconds = 60L;
    /**
     * 过期扫描单批上限
     */
    @NotNull
    @Min(1)
    private Integer expirationScanBatchSize = 500;

}
