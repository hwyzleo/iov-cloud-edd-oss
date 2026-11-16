package net.hwyz.iov.cloud.edd.oss.service.application.port.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 带签名摘要元数据的 HEAD 结果（EDD-OSS-DSN-CR-003 §5.1）。
 * <p>
 * 不包含任何 COS SDK 类型；{@code sha256Hex} 来自强制签名的
 * {@code x-cos-meta-sha256}，缺失时为 {@code null}。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectHeadMetadata {

    /**
     * 对象 Content-Type 元数据
     */
    private String contentType;
    /**
     * 对象实际字节数
     */
    private long contentLength;
    /**
     * 对象 ETag，仅登记，不作为 SHA-256
     */
    private String etag;
    /**
     * 强制签名摘要元数据，缺失为 null
     */
    private String sha256Hex;

}
