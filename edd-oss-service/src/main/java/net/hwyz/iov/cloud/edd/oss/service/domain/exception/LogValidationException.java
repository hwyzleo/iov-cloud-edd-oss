package net.hwyz.iov.cloud.edd.oss.service.domain.exception;

import lombok.Getter;

/**
 * 日志参数校验异常（EDD-OSS-DSN-CR-003 §4.3）。
 * <p>
 * 应用层统一映射为 {@code OSS-811001} / 400，不泄露内部细节。
 *
 * @author hwyz_leo
 */
@Getter
public class LogValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Failure failure;

    public LogValidationException(Failure failure, String message) {
        super(message);
        this.failure = failure;
    }

    /**
     * 校验失败类型
     */
    public enum Failure {
        /**
         * 调用方服务名非法
         */
        INVALID_CALLER,
        /**
         * businessType 非法
         */
        INVALID_BUSINESS_TYPE,
        /**
         * businessId/businessSubId/requestId 非法
         */
        INVALID_TOKEN,
        /**
         * 文件名非法
         */
        INVALID_FILENAME,
        /**
         * Content-Type 不受支持
         */
        UNSUPPORTED_CONTENT_TYPE,
        /**
         * 声明大小非法
         */
        INVALID_SIZE,
        /**
         * SHA-256 非法
         */
        INVALID_SHA256,
        /**
         * ttlSeconds 非法
         */
        INVALID_TTL,
        /**
         * 隐私分级非法
         */
        INVALID_PRIVACY_LEVEL
    }

}
