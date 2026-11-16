package net.hwyz.iov.cloud.edd.oss.service.adapter.web.assembler;

import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectDownloadUrlResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectUploadResponse;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectDownloadUrlResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectUploadResult;

import java.time.Instant;

/**
 * 日志对象 Web 层转换器（EDD-OSS-DSN-CR-003 §8）。
 * <p>
 * 时间契约：对外统一 epoch milliseconds；未发生的时间返回 null。
 *
 * @author hwyz_leo
 */
public final class LogObjectWebAssembler {

    private LogObjectWebAssembler() {
    }

    /**
     * 上传授权结果转 API 响应。
     *
     * @param result 应用层结果
     * @return API 响应
     */
    public static LogObjectUploadResponse toResponse(LogObjectUploadResult result) {
        return LogObjectUploadResponse.builder()
                .objectId(result.getObjectId())
                .objectKey(result.getObjectKey())
                .uploadUrl(result.getUploadUrl())
                .headers(result.getHeaders())
                .expiresAt(toEpochMilli(result.getExpiresAt()))
                .maxSizeBytes(result.getMaxSizeBytes())
                .build();
    }

    /**
     * 登记事实转 API 响应。
     *
     * @param result 应用层结果
     * @return API 响应
     */
    public static LogObjectResponse toResponse(LogObjectResult result) {
        return LogObjectResponse.builder()
                .objectId(result.getObjectId())
                .businessType(result.getBusinessType())
                .businessId(result.getBusinessId())
                .businessSubId(result.getBusinessSubId())
                .objectKey(result.getObjectKey())
                .contentType(result.getContentType())
                .sizeBytes(result.getSizeBytes())
                .actualSizeBytes(result.getActualSizeBytes())
                .sha256Hex(result.getSha256Hex())
                .actualSha256Hex(result.getActualSha256Hex())
                .etag(result.getEtag())
                .uploadState(result.getUploadState())
                .failureReason(result.getFailureReason())
                .authorizedAt(toEpochMilli(result.getAuthorizedAt()))
                .uploadedAt(toEpochMilli(result.getUploadedAt()))
                .expiresAt(toEpochMilli(result.getExpiresAt()))
                .receivedAt(toEpochMilli(result.getReceivedAt()))
                .build();
    }

    /**
     * 下载地址结果转 API 响应。
     *
     * @param result 应用层结果
     * @return API 响应
     */
    public static LogObjectDownloadUrlResponse toResponse(LogObjectDownloadUrlResult result) {
        return LogObjectDownloadUrlResponse.builder()
                .objectId(result.getObjectId())
                .downloadUrl(result.getDownloadUrl())
                .expiresAt(toEpochMilli(result.getExpiresAt()))
                .build();
    }

    private static Long toEpochMilli(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

}
