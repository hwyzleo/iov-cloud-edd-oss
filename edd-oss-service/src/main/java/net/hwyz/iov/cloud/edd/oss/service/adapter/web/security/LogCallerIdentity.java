package net.hwyz.iov.cloud.edd.oss.service.adapter.web.security;

import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.domain.exception.LogValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.service.LogValidationPolicy;

/**
 * Service LogObject API 调用方身份解析（EDD-OSS-DSN-CR-003 §7.1）。
 * <p>
 * 身份来自 Feign 自动写入的 {@code X-Caller-Service}，为内网自报值；仅校验格式，
 * 不维护调用方白名单。
 *
 * @author hwyz_leo
 */
public final class LogCallerIdentity {

    private LogCallerIdentity() {
    }

    /**
     * 解析并校验调用方服务名。
     *
     * @param callerServiceName 请求头值
     * @return 规范化后的服务名
     */
    public static String require(String callerServiceName) {
        if (callerServiceName == null || callerServiceName.isBlank()) {
            throw new OssException(OssErrorCode.INVALID_TOKEN);
        }
        try {
            return LogValidationPolicy.validateCallerService(callerServiceName);
        } catch (LogValidationException exception) {
            throw new OssException(OssErrorCode.PARAMETER_INVALID, "invalid caller service name");
        }
    }

}
