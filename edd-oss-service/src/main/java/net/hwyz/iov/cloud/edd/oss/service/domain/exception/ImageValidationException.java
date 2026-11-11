package net.hwyz.iov.cloud.edd.oss.service.domain.exception;

import lombok.Getter;

/**
 * 图片校验异常（EDD-OSS-DSN-CR-001 §5.2）。
 * <p>
 * 领域层只表达失败类型，具体错误码与 HTTP 状态由应用层映射。
 *
 * @author hwyz_leo
 */
@Getter
public class ImageValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Failure failure;

    public ImageValidationException(Failure failure, String message) {
        super(message);
        this.failure = failure;
    }

    /**
     * 校验失败类型
     */
    public enum Failure {
        /**
         * serviceName 不合法
         */
        INVALID_SERVICE_NAME,
        /**
         * 原始文件名不合法
         */
        INVALID_FILENAME,
        /**
         * 不支持的类型或扩展名
         */
        UNSUPPORTED_MIME,
        /**
         * 扩展名与声明 MIME 不匹配
         */
        MIME_EXTENSION_MISMATCH,
        /**
         * 声明大小非法
         */
        INVALID_DECLARED_SIZE,
        /**
         * 实际大小非法
         */
        ACTUAL_SIZE_INVALID,
        /**
         * 实际 MIME 非法或与声明不一致
         */
        ACTUAL_MIME_INVALID
    }

}
