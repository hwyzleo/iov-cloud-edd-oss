package net.hwyz.iov.cloud.edd.oss.service.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.hwyz.iov.cloud.framework.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * OSS 服务错误码（EDD-OSS-DSN-CR-001 §11）。
 * <p>
 * 错误码与 HTTP 状态必须成对保持稳定：客户端按 code 判定业务语义，按 HTTP 状态判定传输结果。
 *
 * @author hwyz_leo
 */
@Getter
@AllArgsConstructor
public enum OssErrorCode implements ErrorCode {

    /**
     * 通用参数错误
     */
    PARAMETER_INVALID("OSS-400001", "Invalid request parameter", HttpStatus.BAD_REQUEST),
    /**
     * 不支持的 MIME/扩展名
     */
    UNSUPPORTED_MIME("OSS-400101", "Unsupported image type or extension", HttpStatus.BAD_REQUEST),
    /**
     * 扩展名与声明 MIME 不匹配
     */
    MIME_EXTENSION_MISMATCH("OSS-400102", "File extension does not match declared content type", HttpStatus.BAD_REQUEST),
    /**
     * 声明大小非法
     */
    INVALID_DECLARED_SIZE("OSS-400103", "Declared image size is out of range", HttpStatus.BAD_REQUEST),
    /**
     * 调用方身份缺失或非法
     */
    INVALID_TOKEN("OSS-401001", "Caller identity is missing or invalid", HttpStatus.UNAUTHORIZED),
    /**
     * objectId 不存在，或任务不属于当前调用方
     */
    OBJECT_NOT_FOUND("OSS-404001", "Image object not found", HttpStatus.NOT_FOUND),
    /**
     * PENDING 任务已过期
     */
    UPLOAD_EXPIRED("OSS-409001", "Image upload task has expired", HttpStatus.CONFLICT),
    /**
     * 当前状态不允许操作
     */
    INVALID_STATE("OSS-409002", "Operation is not allowed in current image state", HttpStatus.CONFLICT),
    /**
     * 非幂等并发状态冲突
     */
    CONCURRENT_CONFLICT("OSS-409003", "Concurrent image state conflict", HttpStatus.CONFLICT),
    /**
     * COS 对象尚不存在
     */
    IMAGE_NOT_UPLOADED("OSS-422001", "Image object has not been uploaded", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * 实际大小为空、为 0 或超限
     */
    ACTUAL_SIZE_INVALID("OSS-422002", "Uploaded image size is invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * 实际 MIME 不支持或与申请不一致
     */
    ACTUAL_MIME_INVALID("OSS-422003", "Uploaded image content type is invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * COS HEAD、删除或签名操作失败
     */
    STORAGE_ERROR("OSS-502001", "Object storage operation failed", HttpStatus.BAD_GATEWAY),
    /**
     * 未分类内部错误
     */
    INTERNAL_ERROR("OSS-500001", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

}
