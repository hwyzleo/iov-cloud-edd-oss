package net.hwyz.iov.cloud.edd.oss.service.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 图片上传配置（EDD-OSS-DSN-CR-001 §8）。
 *
 * @author hwyz_leo
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "oss.image")
public class ImageProperties {

    /**
     * 预签名有效期（秒），第一阶段 900
     */
    @NotNull
    @Min(1)
    private Long uploadExpirationSeconds = 900L;
    /**
     * 单张图片最大字节数，第一阶段 10485760
     */
    @NotNull
    @Min(1)
    private Long maxSizeBytes = 10485760L;

}
