package net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * COS 对象元数据（EDD-OSS-DSN-CR-001 §8）。
 * <p>
 * 仅表达 HEAD 结果，不包含任何 COS SDK 类型。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CosObjectMetadata {

    /**
     * 对象 Content-Type 元数据
     */
    private String contentType;
    /**
     * 对象字节数
     */
    private long contentLength;
    /**
     * 对象 ETag
     */
    private String etag;

}
