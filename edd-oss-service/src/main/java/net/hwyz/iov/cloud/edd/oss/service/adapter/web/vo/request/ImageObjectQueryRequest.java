package net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MPT 图片对象列表查询请求（EDD-OSS-DSN-CR-001 §10.2.1）。
 * <p>
 * 时间参数使用 epoch milliseconds，避免 JSON 时区与格式歧义。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageObjectQueryRequest {

    private String serviceName;
    private String status;
    private String contentType;
    private String objectKey;
    private String originalFilename;
    private Long uploadBeginTime;
    private Long uploadEndTime;

}
