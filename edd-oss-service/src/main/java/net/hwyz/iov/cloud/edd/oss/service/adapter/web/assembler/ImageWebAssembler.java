package net.hwyz.iov.cloud.edd.oss.service.adapter.web.assembler;

import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageObjectResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageUploadResponse;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.response.ImagePreviewResponse;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;

import java.time.Instant;

/**
 * 图片 Web 层转换器（EDD-OSS-DSN-CR-001 §4、§10）。
 * <p>
 * 时间契约：对外统一 epoch milliseconds。
 *
 * @author hwyz_leo
 */
public final class ImageWebAssembler {

    private ImageWebAssembler() {
    }

    /**
     * 上传结果转响应。
     *
     * @param result 应用层结果
     * @return API 响应
     */
    public static ImageUploadResponse toResponse(ImageUploadResult result) {
        return ImageUploadResponse.builder()
                .objectId(result.getObjectId())
                .objectKey(result.getObjectKey())
                .uploadUrl(result.getUploadUrl())
                .httpMethod(result.getHttpMethod())
                .contentType(result.getContentType())
                .expiresAt(toEpochMilli(result.getExpiresAt()))
                .build();
    }

    /**
     * 图片对象结果转 Service API 响应：按 §10.1.2 只返回上传生命周期字段，不暴露后台审计字段。
     *
     * @param result 应用层结果
     * @return API 响应
     */
    public static ImageObjectResponse toServiceResponse(ImageObjectResult result) {
        ImageObjectResponse response = toResponse(result);
        response.setDeleteTime(null);
        response.setDeleteBy(null);
        response.setCreateBy(null);
        response.setCreateTime(null);
        response.setModifyBy(null);
        response.setModifyTime(null);
        return response;
    }

    /**
     * 图片对象结果转响应。
     *
     * @param result 应用层结果
     * @return API 响应
     */
    public static ImageObjectResponse toResponse(ImageObjectResult result) {
        return ImageObjectResponse.builder()
                .objectId(result.getObjectId())
                .serviceName(result.getServiceName())
                .originalFilename(result.getOriginalFilename())
                .objectKey(result.getObjectKey())
                .provider(result.getProvider())
                .region(result.getRegion())
                .bucketName(result.getBucketName())
                .permission(result.getPermission())
                .declaredContentType(result.getDeclaredContentType() == null ? null
                        : result.getDeclaredContentType().getValue())
                .declaredSize(result.getDeclaredSize())
                .actualContentType(result.getActualContentType() == null ? null
                        : result.getActualContentType().getValue())
                .actualSize(result.getActualSize())
                .etag(result.getEtag())
                .accessUrl(result.getAccessUrl())
                .status(result.getStatus() == null ? null : result.getStatus().name())
                .expireTime(toEpochMilli(result.getExpireTime()))
                .uploadTime(toEpochMilli(result.getUploadTime()))
                .deleteTime(toEpochMilli(result.getDeleteTime()))
                .deleteBy(result.getDeleteBy())
                .createBy(result.getCreateBy())
                .createTime(toEpochMilli(result.getCreateTime()))
                .modifyBy(result.getModifyBy())
                .modifyTime(toEpochMilli(result.getModifyTime()))
                .build();
    }

    /**
     * 图片对象结果转预览响应；只有 UPLOADED 可预览。
     *
     * @param result 应用层结果
     * @return 预览响应
     */
    public static ImagePreviewResponse toPreviewResponse(ImageObjectResult result) {
        boolean previewable = result.getStatus() == ImageStatus.UPLOADED && result.getAccessUrl() != null;
        return ImagePreviewResponse.builder()
                .objectId(result.getObjectId())
                .status(result.getStatus() == null ? null : result.getStatus().name())
                .previewable(previewable)
                .previewUrl(previewable ? result.getAccessUrl() : null)
                .build();
    }

    private static Long toEpochMilli(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

}
