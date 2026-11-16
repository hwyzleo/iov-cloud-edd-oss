package net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate;

import lombok.Getter;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogContentType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogFailureReason;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogUploadState;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 日志对象聚合根（EDD-OSS-DSN-CR-003 §4.1、§4.2）。
 * <p>
 * 状态只能经领域方法流转：{@code AUTHORIZED → UPLOADED / FAILED / EXPIRED}；
 * {@code UPLOADED} 只能由 complete HEAD 核验通过后产生。{@code FAILED} 与 {@code EXPIRED}
 * 为终态，需要重新上传时必须使用新的 requestId。
 *
 * @author hwyz_leo
 */
@Getter
public class LogObject {

    /**
     * 数据库内部主键
     */
    private Long id;
    /**
     * 外部对象标识（UUID）
     */
    private final String objectId;
    /**
     * 存储提供方
     */
    private final StorageProvider provider;
    /**
     * 授权时的 COS 地域快照
     */
    private final String region;
    /**
     * 授权时的私有日志 Bucket 快照
     */
    private final String bucketName;
    /**
     * 调用方归属命名空间
     */
    private final String callerService;
    /**
     * 业务类型
     */
    private final LogBusinessType businessType;
    /**
     * 业务主标识，本期为 vehicleTaskId
     */
    private final String businessId;
    /**
     * 业务子标识，本期可选 executionId
     */
    private final String businessSubId;
    /**
     * 调用方幂等键
     */
    private final String requestId;
    /**
     * 原始文件名，仅元数据，不进入对象键
     */
    private final String fileName;
    /**
     * 服务端生成的对象键
     */
    private final String objectKey;
    /**
     * 声明且强制签名的日志 MIME
     */
    private final LogContentType contentType;
    /**
     * 声明大小
     */
    private final long declaredSize;
    /**
     * HEAD 观察到的实际大小
     */
    private Long actualSize;
    /**
     * 声明摘要（SHA-256 小写十六进制）
     */
    private final String declaredSha256;
    /**
     * HEAD 观察/核验的摘要元数据
     */
    private String actualSha256;
    /**
     * HEAD ETag，仅登记
     */
    private String etag;
    /**
     * 调用方隐私分级元数据
     */
    private final String privacyLevel;
    /**
     * 当前状态
     */
    private LogUploadState uploadState;
    /**
     * 稳定失败原因码
     */
    private LogFailureReason failureReason;
    /**
     * 授权登记时间
     */
    private final Instant authorizedAt;
    /**
     * 核验通过并完成条件更新的时间
     */
    private Instant uploadedAt;
    /**
     * 上传授权过期时间
     */
    private final Instant expiresAt;
    /**
     * 首次 complete 受理时间
     */
    private Instant receivedAt;
    /**
     * 乐观锁版本
     */
    private Integer rowVersion;
    /**
     * 创建方
     */
    private final String createBy;
    /**
     * 审计创建时间
     */
    private final Instant createTime;
    /**
     * 最后修改方
     */
    private String modifyBy;
    /**
     * 审计修改时间
     */
    private Instant modifyTime;

    private LogObject(Long id, String objectId, StorageProvider provider, String region, String bucketName,
                      String callerService, LogBusinessType businessType, String businessId, String businessSubId,
                      String requestId, String fileName, String objectKey, LogContentType contentType,
                      long declaredSize, Long actualSize, String declaredSha256, String actualSha256, String etag,
                      String privacyLevel, LogUploadState uploadState, LogFailureReason failureReason,
                      Instant authorizedAt, Instant uploadedAt, Instant expiresAt, Instant receivedAt,
                      Integer rowVersion, String createBy, Instant createTime, String modifyBy, Instant modifyTime) {
        this.id = id;
        this.objectId = objectId;
        this.provider = provider;
        this.region = region;
        this.bucketName = bucketName;
        this.callerService = callerService;
        this.businessType = businessType;
        this.businessId = businessId;
        this.businessSubId = businessSubId;
        this.requestId = requestId;
        this.fileName = fileName;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.declaredSize = declaredSize;
        this.actualSize = actualSize;
        this.declaredSha256 = declaredSha256;
        this.actualSha256 = actualSha256;
        this.etag = etag;
        this.privacyLevel = privacyLevel;
        this.uploadState = uploadState;
        this.failureReason = failureReason;
        this.authorizedAt = authorizedAt;
        this.uploadedAt = uploadedAt;
        this.expiresAt = expiresAt;
        this.receivedAt = receivedAt;
        this.rowVersion = rowVersion;
        this.createBy = createBy;
        this.createTime = createTime;
        this.modifyBy = modifyBy;
        this.modifyTime = modifyTime;
    }

    /**
     * 创建 AUTHORIZED 日志对象登记。
     *
     * @param objectId       对象标识
     * @param provider       存储提供方
     * @param region         COS 地域
     * @param bucketName     私有日志 Bucket
     * @param callerService  调用方服务名
     * @param businessType   业务类型
     * @param businessId     业务主标识
     * @param businessSubId  业务子标识，可为 null
     * @param requestId      幂等键
     * @param fileName       原始文件名
     * @param objectKey      服务端生成对象键
     * @param contentType    声明 Content-Type
     * @param declaredSize   声明大小
     * @param declaredSha256 声明摘要
     * @param privacyLevel   隐私分级
     * @param expiresAt      授权过期时间
     * @param now            授权时间
     * @return 新建日志对象
     */
    public static LogObject authorized(String objectId, StorageProvider provider, String region, String bucketName,
                                       String callerService, LogBusinessType businessType, String businessId,
                                       String businessSubId, String requestId, String fileName, String objectKey,
                                       LogContentType contentType, long declaredSize, String declaredSha256,
                                       String privacyLevel, Instant expiresAt, Instant now) {
        return new LogObject(null, objectId, provider, region, bucketName, callerService, businessType, businessId,
                businessSubId, requestId, fileName, objectKey, contentType, declaredSize, null, declaredSha256, null,
                null, privacyLevel, LogUploadState.AUTHORIZED, null, now, null, expiresAt, null, 1, callerService,
                now, callerService, now);
    }

    /**
     * 从持久化状态还原聚合根（仅基础设施转换器使用）。
     *
     * @return 还原的日志对象
     */
    public static LogObject restore(Long id, String objectId, StorageProvider provider, String region,
                                    String bucketName, String callerService, LogBusinessType businessType,
                                    String businessId, String businessSubId, String requestId, String fileName,
                                    String objectKey, LogContentType contentType, long declaredSize, Long actualSize,
                                    String declaredSha256, String actualSha256, String etag, String privacyLevel,
                                    LogUploadState uploadState, LogFailureReason failureReason, Instant authorizedAt,
                                    Instant uploadedAt, Instant expiresAt, Instant receivedAt, Integer rowVersion,
                                    String createBy, Instant createTime, String modifyBy, Instant modifyTime) {
        return new LogObject(id, objectId, provider, region, bucketName, callerService, businessType, businessId,
                businessSubId, requestId, fileName, objectKey, contentType, declaredSize, actualSize, declaredSha256,
                actualSha256, etag, privacyLevel, uploadState, failureReason, authorizedAt, uploadedAt, expiresAt,
                receivedAt, rowVersion, createBy, createTime, modifyBy, modifyTime);
    }

    /**
     * 记录首次 complete 受理时间；后续 complete 不覆盖。
     *
     * @param now 受理时间
     * @return 是否首次写入
     */
    public boolean recordCompleteReceived(Instant now) {
        if (uploadState != LogUploadState.AUTHORIZED || receivedAt != null) {
            return false;
        }
        this.receivedAt = now;
        this.modifyTime = now;
        return true;
    }

    /**
     * HEAD 核验全部一致后的上传成功事实。
     *
     * @param observedSize        实际大小
     * @param observedSha256      实际摘要元数据
     * @param observedEtag        实际 ETag
     * @param observedContentType 实际 Content-Type
     * @param now                 确认时间
     */
    public void markUploaded(long observedSize, String observedSha256, String observedEtag,
                             String observedContentType, Instant now) {
        if (uploadState != LogUploadState.AUTHORIZED) {
            throw new IllegalStateException("log object " + objectId + " is not AUTHORIZED");
        }
        if (isUploadExpired(now)) {
            throw new IllegalStateException("log object " + objectId + " upload authorization has expired");
        }
        if (observedSize != declaredSize) {
            throw new IllegalStateException("observed size does not match declared size");
        }
        if (!declaredSha256.equalsIgnoreCase(normalizeSha256(observedSha256))) {
            throw new IllegalStateException("observed sha256 does not match declared sha256");
        }
        if (!contentType.getValue().equalsIgnoreCase(normalizeContentType(observedContentType))) {
            throw new IllegalStateException("observed content type does not match declared content type");
        }
        this.actualSize = observedSize;
        this.actualSha256 = normalizeSha256(observedSha256);
        this.etag = observedEtag;
        this.uploadState = LogUploadState.UPLOADED;
        this.uploadedAt = now;
        if (this.receivedAt == null) {
            this.receivedAt = now;
        }
        this.modifyBy = callerService;
        this.modifyTime = now;
    }

    /**
     * 确定性核验失败：AUTHORIZED → FAILED，保存观察到的元数据与稳定原因码。
     *
     * @param reason          失败原因
     * @param observedSize    观察大小，可为 null
     * @param observedSha256  观察摘要，可为 null
     * @param observedEtag    观察 ETag，可为 null
     * @param now             失败时间
     */
    public void markFailed(LogFailureReason reason, Long observedSize, String observedSha256, String observedEtag,
                           Instant now) {
        if (uploadState != LogUploadState.AUTHORIZED) {
            throw new IllegalStateException("log object " + objectId + " is not AUTHORIZED");
        }
        if (isUploadExpired(now)) {
            throw new IllegalStateException("log object " + objectId + " upload authorization has expired");
        }
        if (reason == null) {
            throw new IllegalArgumentException("failure reason is required");
        }
        this.failureReason = reason;
        this.actualSize = observedSize;
        this.actualSha256 = normalizeSha256(observedSha256);
        this.etag = observedEtag;
        this.uploadState = LogUploadState.FAILED;
        this.modifyBy = callerService;
        this.modifyTime = now;
    }

    /**
     * 上传授权过期：AUTHORIZED → EXPIRED，不删除对象、不改写历史。
     *
     * @param now 过期判断时间
     */
    public void markExpired(Instant now) {
        if (uploadState != LogUploadState.AUTHORIZED) {
            throw new IllegalStateException("log object " + objectId + " is not AUTHORIZED");
        }
        if (!isUploadExpired(now)) {
            throw new IllegalStateException("log object " + objectId + " upload authorization has not expired");
        }
        this.uploadState = LogUploadState.EXPIRED;
        this.modifyBy = callerService;
        this.modifyTime = now;
    }

    /**
     * 是否属于指定调用方。
     *
     * @param callerServiceName 调用方服务名
     * @return 是否归属
     */
    public boolean belongsTo(String callerServiceName) {
        return Objects.equals(callerService, callerServiceName);
    }

    /**
     * 上传授权是否已过期。
     *
     * @param now 当前时间
     * @return 是否过期
     */
    public boolean isUploadExpired(Instant now) {
        return uploadState == LogUploadState.AUTHORIZED && !now.isBefore(expiresAt);
    }

    /**
     * 是否已核验上传。
     *
     * @return 是否 UPLOADED
     */
    public boolean isUploaded() {
        return uploadState == LogUploadState.UPLOADED;
    }

    /**
     * 是否确定性失败。
     *
     * @return 是否 FAILED
     */
    public boolean isFailed() {
        return uploadState == LogUploadState.FAILED;
    }

    /**
     * 是否已过期。
     *
     * @return 是否 EXPIRED
     */
    public boolean isExpired() {
        return uploadState == LogUploadState.EXPIRED;
    }

    /**
     * 是否处于授权可上传状态。
     *
     * @return 是否 AUTHORIZED
     */
    public boolean isAuthorized() {
        return uploadState == LogUploadState.AUTHORIZED;
    }

    /**
     * 条件更新成功后递增乐观锁版本。
     *
     * @param modifier 修改方
     * @param now      修改时间
     */
    public void increaseRowVersion(String modifier, Instant now) {
        this.rowVersion = (rowVersion == null ? 1 : rowVersion) + 1;
        this.modifyBy = modifier;
        this.modifyTime = now;
    }

    /**
     * 记录数据库主键（插入成功后回填）。
     *
     * @param id 主键
     */
    public void assignId(Long id) {
        this.id = id;
    }

    private static String normalizeSha256(String sha256) {
        return sha256 == null ? null : sha256.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int parameterIndex = contentType.indexOf(';');
        String value = parameterIndex >= 0 ? contentType.substring(0, parameterIndex) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

}
