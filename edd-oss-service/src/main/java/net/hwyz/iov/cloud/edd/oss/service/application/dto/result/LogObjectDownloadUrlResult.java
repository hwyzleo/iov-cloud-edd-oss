package net.hwyz.iov.cloud.edd.oss.service.application.dto.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 日志对象短期下载地址结果（EDD-OSS-DSN-CR-003 §8.5）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogObjectDownloadUrlResult {

    /**
     * 对象标识
     */
    private String objectId;
    /**
     * 短期预签名 GET 地址，不落库、不写日志
     */
    private String downloadUrl;
    /**
     * 下载地址过期时间
     */
    private Instant expiresAt;

}
