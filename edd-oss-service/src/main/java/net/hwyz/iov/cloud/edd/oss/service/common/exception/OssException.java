package net.hwyz.iov.cloud.edd.oss.service.common.exception;

import lombok.Getter;

/**
 * OSS 业务异常，携带错误码与 HTTP 状态。
 *
 * @author hwyz_leo
 */
@Getter
public class OssException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final OssErrorCode errorCode;

    public OssException(OssErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public OssException(OssErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OssException(OssErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}
