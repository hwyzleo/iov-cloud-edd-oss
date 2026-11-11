package net.hwyz.iov.cloud.edd.oss.service.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.framework.common.bean.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * OSS 异常处理（EDD-OSS-DSN-CR-001 §11）。
 * <p>
 * 使用 {@link ResponseEntity} 保留 HTTP 状态；不处理框架安全异常与权限异常，避免覆盖平台既有语义。
 *
 * @author hwyz_leo
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class OssExceptionHandler {

    @ExceptionHandler(OssException.class)
    public ResponseEntity<ApiResponse<Void>> handleOssException(OssException exception, HttpServletRequest request) {
        OssErrorCode errorCode = exception.getErrorCode();
        log.warn("OSS业务异常: uri={}, code={}, message={}", request.getRequestURI(), errorCode.getCode(),
                exception.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? OssErrorCode.PARAMETER_INVALID.getMessage()
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        log.warn("OSS请求参数校验失败: {}", message);
        return ResponseEntity.status(OssErrorCode.PARAMETER_INVALID.getHttpStatus())
                .body(body(OssErrorCode.PARAMETER_INVALID, message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? OssErrorCode.PARAMETER_INVALID.getMessage()
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        log.warn("OSS请求参数绑定失败: {}", message);
        return ResponseEntity.status(OssErrorCode.PARAMETER_INVALID.getHttpStatus())
                .body(body(OssErrorCode.PARAMETER_INVALID, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException exception) {
        log.warn("OSS请求体不可读: {}", exception.getMessage());
        return ResponseEntity.status(OssErrorCode.PARAMETER_INVALID.getHttpStatus())
                .body(body(OssErrorCode.PARAMETER_INVALID, OssErrorCode.PARAMETER_INVALID.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException exception) {
        log.error("OSS数据库访问异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(OssErrorCode.INTERNAL_ERROR, OssErrorCode.INTERNAL_ERROR.getMessage()));
    }

    private ApiResponse<Void> body(OssErrorCode errorCode, String message) {
        return ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(message)
                .traceId(org.slf4j.MDC.get("traceId"))
                .timestamp(System.currentTimeMillis())
                .build();
    }

}
