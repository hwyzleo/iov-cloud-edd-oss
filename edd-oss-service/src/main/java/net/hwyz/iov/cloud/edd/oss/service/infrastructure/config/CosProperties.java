package net.hwyz.iov.cloud.edd.oss.service.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * COS 存储配置（EDD-OSS-DSN-CR-001 §8）。
 * <p>
 * 必填项缺失时启动失败；Secret 不得写入仓库、测试快照或日志。
 *
 * @author hwyz_leo
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "oss.storage.cos")
public class CosProperties {

    /**
     * COS 地域，例如 ap-shanghai
     */
    @NotBlank
    private String region;
    /**
     * COS Bucket
     */
    @NotBlank
    private String bucket;
    /**
     * COS SecretId
     */
    @NotBlank
    private String secretId;
    /**
     * COS SecretKey
     */
    @NotBlank
    private String secretKey;

}
