package net.hwyz.iov.cloud.edd.oss.service.application.dto.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 日志对象登记事实（EDD-OSS-DSN-CR-003 §8.3）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogObjectResult {

    private String objectId;
    private String businessType;
    private String businessId;
    private String businessSubId;
    private String objectKey;
    private String contentType;
    private long sizeBytes;
    private Long actualSizeBytes;
    private String sha256Hex;
    private String actualSha256Hex;
    private String etag;
    private String uploadState;
    private String failureReason;
    private Instant authorizedAt;
    private Instant uploadedAt;
    private Instant expiresAt;
    private Instant receivedAt;

}
