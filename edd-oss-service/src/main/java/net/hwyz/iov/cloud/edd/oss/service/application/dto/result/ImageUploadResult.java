package net.hwyz.iov.cloud.edd.oss.service.application.dto.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 图片上传任务结果（EDD-OSS-DSN-CR-001 §10.1.1）。
 * <p>
 * 预签名地址不得持久化、不得写入日志。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResult {

    private String objectId;
    private String objectKey;
    private String uploadUrl;
    private String httpMethod;
    private String contentType;
    private Instant expiresAt;

}
