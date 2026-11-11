package net.hwyz.iov.cloud.edd.oss.service.application.dto.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;

import java.time.Instant;

/**
 * 图片对象结果（EDD-OSS-DSN-CR-001 §10.1.2、§10.2.2）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageObjectResult {

    private String objectId;
    private String serviceName;
    private String originalFilename;
    private String objectKey;
    private String provider;
    private String region;
    private String bucketName;
    private String permission;
    private ImageMimeType declaredContentType;
    private long declaredSize;
    private ImageMimeType actualContentType;
    private Long actualSize;
    private String etag;
    /**
     * 仅 UPLOADED 状态返回
     */
    private String accessUrl;
    private ImageStatus status;
    private Instant expireTime;
    private Instant uploadTime;
    private Instant deleteTime;
    private String deleteBy;
    private String createBy;
    private Instant createTime;
    private String modifyBy;
    private Instant modifyTime;

}
