package net.hwyz.iov.cloud.edd.oss.api.vo.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 日志对象上传授权响应（EDD-OSS-DSN-CR-003 §8.2）。
 * <p>
 * 不返回 SecretId、SecretKey、长期凭证或永久权威 URL；uploadUrl 不得写入日志。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogObjectUploadResponse {

    /**
     * 对象标识
     */
    private String objectId;
    /**
     * 服务端生成对象键
     */
    private String objectKey;
    /**
     * 预签名 PUT 地址
     */
    private String uploadUrl;
    /**
     * 上传时必须原样携带的强制头：Content-Type 与 x-cos-meta-sha256
     */
    private Map<String, String> headers;
    /**
     * 原授权过期时间，epoch milliseconds
     */
    private Long expiresAt;
    /**
     * 当前生效单对象上限
     */
    private Long maxSizeBytes;

}
