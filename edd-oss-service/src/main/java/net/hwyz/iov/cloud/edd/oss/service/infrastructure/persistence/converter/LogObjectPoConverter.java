package net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.converter;

import com.github.pagehelper.Page;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.LogObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogContentType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogFailureReason;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogUploadState;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.po.LogObjectPo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 日志对象 PO ↔ Domain 转换器（EDD-OSS-DSN-CR-003 §5）。
 * <p>
 * 时间契约：Domain 使用 {@link Instant}，PO 使用 {@link Date}。
 *
 * @author hwyz_leo
 */
public final class LogObjectPoConverter {

    private LogObjectPoConverter() {
    }

    /**
     * PO 转领域对象。
     *
     * @param po 持久化对象
     * @return 领域对象；入参为 null 时返回 null
     */
    public static LogObject toDomain(LogObjectPo po) {
        if (po == null) {
            return null;
        }
        return LogObject.restore(po.getId(), po.getObjectId(), toProvider(po.getProvider()), po.getRegion(),
                po.getBucketName(), po.getCallerService(), toBusinessType(po.getBusinessType()), po.getBusinessId(),
                po.getBusinessSubId(), po.getRequestId(), po.getFileName(), po.getObjectKey(),
                toContentType(po.getContentType()), po.getDeclaredSize() == null ? 0L : po.getDeclaredSize(),
                po.getActualSize(), po.getDeclaredSha256(), po.getActualSha256(), po.getEtag(), po.getPrivacyLevel(),
                LogUploadState.valueOf(po.getUploadState()), toFailureReason(po.getFailureReason()),
                toInstant(po.getAuthorizedAt()), toInstant(po.getUploadedAt()), toInstant(po.getExpiresAt()),
                toInstant(po.getReceivedAt()), po.getRowVersion(), po.getCreateBy(), toInstant(po.getCreateTime()),
                po.getModifyBy(), toInstant(po.getModifyTime()));
    }

    /**
     * PO 列表转领域对象列表，并保留平台分页元数据。
     *
     * @param poList 持久化对象列表
     * @return 领域对象列表
     */
    public static List<LogObject> toDomainList(List<LogObjectPo> poList) {
        List<LogObject> domainList = new ArrayList<>(poList.size());
        for (LogObjectPo po : poList) {
            domainList.add(toDomain(po));
        }
        if (poList instanceof Page<?> sourcePage) {
            Page<LogObject> targetPage = new Page<>(sourcePage.getPageNum(), sourcePage.getPageSize(),
                    sourcePage.isCount());
            targetPage.setTotal(sourcePage.getTotal());
            targetPage.addAll(domainList);
            return targetPage;
        }
        return domainList;
    }

    /**
     * 领域对象转 PO。
     *
     * @param logObject 领域对象
     * @return 持久化对象
     */
    public static LogObjectPo fromDomain(LogObject logObject) {
        LogObjectPo po = LogObjectPo.builder()
                .id(logObject.getId())
                .objectId(logObject.getObjectId())
                .provider(logObject.getProvider().name())
                .region(logObject.getRegion())
                .bucketName(logObject.getBucketName())
                .callerService(logObject.getCallerService())
                .businessType(logObject.getBusinessType().name())
                .businessId(logObject.getBusinessId())
                .businessSubId(logObject.getBusinessSubId())
                .requestId(logObject.getRequestId())
                .fileName(logObject.getFileName())
                .objectKey(logObject.getObjectKey())
                .contentType(logObject.getContentType().getValue())
                .declaredSize(logObject.getDeclaredSize())
                .actualSize(logObject.getActualSize())
                .declaredSha256(logObject.getDeclaredSha256())
                .actualSha256(logObject.getActualSha256())
                .etag(logObject.getEtag())
                .privacyLevel(logObject.getPrivacyLevel())
                .uploadState(logObject.getUploadState().name())
                .failureReason(logObject.getFailureReason() == null ? null : logObject.getFailureReason().name())
                .authorizedAt(toDate(logObject.getAuthorizedAt()))
                .uploadedAt(toDate(logObject.getUploadedAt()))
                .expiresAt(toDate(logObject.getExpiresAt()))
                .receivedAt(toDate(logObject.getReceivedAt()))
                .build();
        po.setCreateBy(logObject.getCreateBy());
        po.setCreateTime(toDate(logObject.getCreateTime()));
        po.setModifyBy(logObject.getModifyBy());
        po.setModifyTime(toDate(logObject.getModifyTime()));
        po.setRowVersion(logObject.getRowVersion());
        po.setRowValid(Boolean.TRUE);
        return po;
    }

    private static StorageProvider toProvider(String provider) {
        return provider == null ? null : StorageProvider.valueOf(provider);
    }

    private static LogBusinessType toBusinessType(String value) {
        return value == null ? null : LogBusinessType.valueOf(value);
    }

    private static LogContentType toContentType(String value) {
        if (value == null) {
            return null;
        }
        return LogContentType.fromValue(value.toLowerCase(Locale.ROOT)).orElse(null);
    }

    private static LogFailureReason toFailureReason(String value) {
        return value == null ? null : LogFailureReason.valueOf(value);
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private static Date toDate(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

}
