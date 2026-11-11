package net.hwyz.iov.cloud.edd.oss.service.infrastructure.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 COS 配置（EDD-OSS-DSN-CR-001 §8）。
 * <p>
 * 使用 HTTPS、最大错误重试 3 次，Bean 销毁时关闭客户端；地域与 Bucket 不得硬编码。
 *
 * @author hwyz_leo
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CosConfiguration {

    private static final int MAX_ERROR_RETRY = 3;

    private final CosProperties cosProperties;

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient() {
        COSCredentials credentials = new BasicCOSCredentials(cosProperties.getSecretId(), cosProperties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(cosProperties.getRegion()));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        clientConfig.setMaxErrorRetry(MAX_ERROR_RETRY);
        log.info("初始化 COS 客户端，地域[{}]", cosProperties.getRegion());
        return new COSClient(credentials, clientConfig);
    }

}
