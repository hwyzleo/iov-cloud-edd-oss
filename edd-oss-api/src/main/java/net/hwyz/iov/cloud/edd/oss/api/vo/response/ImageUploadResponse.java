package net.hwyz.iov.cloud.edd.oss.api.vo.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片上传任务响应（EDD-OSS-DSN-CR-001 §10.1.1）。
 * <p>
 * 不返回 SecretId、SecretKey 或任何长期凭证；uploadUrl 不得写入日志。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResponse {

    /**
     * 上传任务与对象标识（UUID）
     */
    private String objectId;
    /**
     * OSS 生成的唯一对象键
     */
    private String objectKey;
    /**
     * COS PUT 预签名地址
     */
    private String uploadUrl;
    /**
     * 上传方法，固定 PUT
     */
    private String httpMethod;
    /**
     * 上传时必须原样携带的 Content-Type
     */
    private String contentType;
    /**
     * 预签名过期时间，epoch milliseconds
     */
    private Long expiresAt;

}
