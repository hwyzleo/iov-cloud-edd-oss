package net.hwyz.iov.cloud.edd.oss.api.vo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片对象响应（EDD-OSS-DSN-CR-001 §10.1.2、§10.2.2）。
 * <p>
 * 所有时间字段使用 epoch milliseconds；accessUrl 仅对 UPLOADED 返回。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageObjectResponse {

    private String objectId;
    private String serviceName;
    /**
     * 默认素材名称；业务图片为空（仅 MPT 响应返回）
     */
    private String assetName;
    /**
     * 默认素材备注（仅 MPT 响应返回）
     */
    private String description;
    private String originalFilename;
    private String objectKey;
    private String provider;
    private String region;
    private String bucketName;
    private String permission;
    private String declaredContentType;
    private Long declaredSize;
    private String actualContentType;
    private Long actualSize;
    private String etag;
    private String accessUrl;
    private String status;
    private Long expireTime;
    private Long uploadTime;
    private Long deleteTime;
    private String deleteBy;
    private String createBy;
    private Long createTime;
    private String modifyBy;
    private Long modifyTime;

}
