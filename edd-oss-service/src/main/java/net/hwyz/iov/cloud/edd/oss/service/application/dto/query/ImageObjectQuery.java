package net.hwyz.iov.cloud.edd.oss.service.application.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 图片对象查询对象（EDD-OSS-DSN-CR-001 §10.2.1）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageObjectQuery {

    private String serviceName;
    private String status;
    private String contentType;
    private String objectKey;
    private String originalFilename;
    private Instant uploadBeginTime;
    private Instant uploadEndTime;

}
