package net.hwyz.iov.cloud.edd.oss.api.vo.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志对象登记事实（EDD-OSS-DSN-CR-003 §8.3）。
 * <p>
 * sizeBytes/sha256Hex 为授权声明，actualSizeBytes/actualSha256Hex 为 HEAD 观察值；
 * 不返回日志正文、VIN 或预签名上传地址。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogObjectResponse {

    private String objectId;
    private String businessType;
    private String businessId;
    private String businessSubId;
    private String objectKey;
    private String contentType;
    private Long sizeBytes;
    private Long actualSizeBytes;
    private String sha256Hex;
    private String actualSha256Hex;
    private String etag;
    private String uploadState;
    private String failureReason;
    private Long authorizedAt;
    private Long uploadedAt;
    private Long expiresAt;
    private Long receivedAt;

}
