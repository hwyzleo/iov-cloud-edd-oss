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
    PARAMETER_INVALID("OSS-811001", "Invalid request parameter", HttpStatus.BAD_REQUEST),
    /**
     * 不支持的 MIME/扩展名
     */
    UNSUPPORTED_MIME("OSS-811002", "Unsupported image type or extension", HttpStatus.BAD_REQUEST),
    /**
     * 扩展名与声明 MIME 不匹配
     */
    MIME_EXTENSION_MISMATCH("OSS-811003", "File extension does not match declared content type", HttpStatus.BAD_REQUEST),
    /**
     * 声明大小非法
     */
    INVALID_DECLARED_SIZE("OSS-811004", "Declared image size is out of range", HttpStatus.BAD_REQUEST),
    /**
     * 素材名称或备注非法
     */
    ASSET_INVALID("OSS-811005", "Asset name or description is invalid", HttpStatus.BAD_REQUEST),
    /**
     * 调用方身份缺失或非法
     */
    INVALID_TOKEN("OSS-811006", "Caller identity is missing or invalid", HttpStatus.UNAUTHORIZED),
    /**
     * objectId 不存在，或任务不属于当前调用方
     */
    OBJECT_NOT_FOUND("OSS-811007", "Image object not found", HttpStatus.NOT_FOUND),
    /**
     * replacementId 不存在或不属于 objectId
     */
    REPLACEMENT_NOT_FOUND("OSS-811008", "Image replacement task not found", HttpStatus.NOT_FOUND),
    /**
     * PENDING 任务已过期
     */
    UPLOAD_EXPIRED("OSS-811009", "Image upload task has expired", HttpStatus.CONFLICT),
    /**
     * 当前状态不允许操作
     */
    INVALID_STATE("OSS-811010", "Operation is not allowed in current image state", HttpStatus.CONFLICT),
    /**
     * 非幂等并发状态冲突
     */
    CONCURRENT_CONFLICT("OSS-811011", "Concurrent image state conflict", HttpStatus.CONFLICT),
    /**
     * 活动替换、提升中操作或其他并发冲突
     */
    REPLACEMENT_CONFLICT("OSS-811012", "Image replacement conflict", HttpStatus.CONFLICT),
    /**
     * COS 对象尚不存在
     */
    IMAGE_NOT_UPLOADED("OSS-811013", "Image object has not been uploaded", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * 实际大小为空、为 0 或超限
     */
    ACTUAL_SIZE_INVALID("OSS-811014", "Uploaded image size is invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * 实际 MIME 不支持或与申请不一致
     */
    ACTUAL_MIME_INVALID("OSS-811015", "Uploaded image content type is invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * COS HEAD、删除或签名操作失败
     */
    STORAGE_ERROR("OSS-811017", "Object storage operation failed", HttpStatus.BAD_GATEWAY),
    /**
     * LogObject 登记不存在（EDD-OSS-DSN-CR-003 §10.1）
     */
    LOG_OBJECT_NOT_FOUND("OSS-811018", "Log object registration not found", HttpStatus.NOT_FOUND),
    /**
     * complete 时 COS 物理日志对象不存在
     */
    LOG_OBJECT_MISSING("OSS-811019", "Log object is missing in object storage", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * HEAD 实际大小与声明不一致
     */
    LOG_SIZE_MISMATCH("OSS-811020", "Uploaded log size does not match declared size",
            HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * 签名 SHA-256 元数据缺失或与声明不一致
     */
    LOG_DIGEST_MISMATCH("OSS-811021", "Uploaded log digest does not match declared digest",
            HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * 日志状态不允许操作、已过期或非 UPLOADED 下载
     */
    LOG_STATE_CONFLICT("OSS-811022", "Operation is not allowed in current log state", HttpStatus.CONFLICT),
    /**
     * 同 (callerService,businessType,requestId) 对应不同申请参数
     */
    LOG_IDEMPOTENCY_CONFLICT("OSS-811023", "Log upload request conflicts with an existing request",
            HttpStatus.CONFLICT),
    /**
     * 目标 LogObject 不属于当前调用方
     */
    LOG_NOT_OWNER("OSS-811024", "Log object does not belong to the caller", HttpStatus.FORBIDDEN),
    /**
     * HEAD 实际 Content-Type 与声明不一致
     */
    LOG_CONTENT_TYPE_MISMATCH("OSS-811025", "Uploaded log content type does not match declared content type",
            HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * 未分类内部错误
     */
    INTERNAL_ERROR("OSS-811016", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

}
