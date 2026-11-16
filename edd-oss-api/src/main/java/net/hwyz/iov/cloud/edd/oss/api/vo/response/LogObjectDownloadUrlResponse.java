package net.hwyz.iov.cloud.edd.oss.api.vo.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志对象短期下载地址响应（EDD-OSS-DSN-CR-003 §8.5）。
 * <p>
 * 不返回 Secret、永久 URL 或上传 URL。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogObjectDownloadUrlResponse {

    private String objectId;
    private String downloadUrl;
    private Long expiresAt;

}
