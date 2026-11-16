package net.hwyz.iov.cloud.edd.oss.service.domain.model.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 日志业务类型（EDD-OSS-DSN-CR-003 §4.1）。
 * <p>
 * 本期仅开放 {@link #OTA_UPGRADE_LOG}，其他类型必须通过新 CR 明确。
 *
 * @author hwyz_leo
 */
public enum LogBusinessType {

    /**
     * OTA 升级日志
     */
    OTA_UPGRADE_LOG;

    /**
     * 解析业务类型，精确匹配且区分大小写。
     *
     * @param value 业务类型值
     * @return 业务类型
     */
    public static Optional<LogBusinessType> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst();
    }

}
