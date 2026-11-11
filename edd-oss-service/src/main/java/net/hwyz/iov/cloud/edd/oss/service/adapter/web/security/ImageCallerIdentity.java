package net.hwyz.iov.cloud.edd.oss.service.adapter.web.security;

import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.domain.exception.ImageValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.service.ImageValidationPolicy;

/**
 * Service Image API 调用方身份解析。
 * <p>
 * 身份来自 Feign 自动写入的 {@code X-Caller-Service}（调用方 {@code spring.application.name}）。
 * 该值为调用方自报，仅校验格式，不校验注册关系（内网信任模型）。
 *
 * @author hwyz_leo
 */
public final class ImageCallerIdentity {

    private ImageCallerIdentity() {
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
        String normalized = callerServiceName.trim();
        try {
            ImageValidationPolicy.validateServiceName(normalized);
        } catch (ImageValidationException exception) {
            throw new OssException(OssErrorCode.PARAMETER_INVALID, "invalid caller service name");
        }
        return normalized;
    }

}
