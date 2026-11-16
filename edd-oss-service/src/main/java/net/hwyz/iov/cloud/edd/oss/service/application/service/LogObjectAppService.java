package net.hwyz.iov.cloud.edd.oss.service.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteLogObjectUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateLogObjectUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.query.LogObjectQuery;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectDownloadUrlResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectHeadMetadata;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectStorageGateway;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.domain.exception.LogValidationException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.LogObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogContentType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogFailureReason;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogUploadState;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.StorageProvider;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.query.LogObjectSearchCriteria;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.LogObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.domain.service.LogValidationPolicy;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.CosProperties;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.config.LogProperties;
import net.hwyz.iov.cloud.framework.web.util.PageUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 日志对象应用服务（EDD-OSS-DSN-CR-003 §8、§9）。
 * <p>
 * 授权与 complete 均以调用方身份隔离；只有 HEAD 核验通过后的条件更新才能产生
 * {@code UPLOADED}。预签名 URL 不落库、不写日志。
 *
 * @author hwyz_leo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogObjectAppService {

    /**
     * 强制携带的 Content-Type 头
     */
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    /**
     * 强制签名的摘要元数据头
     */
    public static final String HEADER_SHA256 = "x-cos-meta-sha256";

    private static final int GENERATE_ATTEMPTS = 2;

    private final LogObjectRepository logObjectRepository;
    private final ObjectStorageGateway objectStorageGateway;
    private final CosProperties cosProperties;
    private final LogProperties logProperties;

    /**
     * 申请日志上传授权：参数校验 → requestId 幂等 → 签名 → 插入 AUTHORIZED（§8.2、§9.1）。
     *
     * @param cmd               申请命令
     * @param callerServiceName 调用方服务名
     * @return 上传参数
     */
    public LogObjectUploadResult generate(GenerateLogObjectUploadCmd cmd, String callerServiceName) {
        UploadDeclaration declaration = validate(cmd, callerServiceName);
        LogObject registered = null;
        LogObjectUploadResult result = null;
        for (int attempt = 0; attempt < GENERATE_ATTEMPTS; attempt++) {
            Optional<LogObject> existing = logObjectRepository.findByRequest(declaration.callerService(),
                    declaration.businessType(), declaration.requestId());
            if (existing.isPresent()) {
                return reuseOrReject(existing.get(), declaration);
            }
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(declaration.ttlSeconds());
            String objectId = UUID.randomUUID().toString();
            String objectKey = LogValidationPolicy.buildObjectKey(declaration.callerService(),
                    declaration.businessType(), now, objectId);
            String uploadUrl = objectStorageGateway.generateUploadUrlWithMetadata(logProperties.getBucket(),
                    objectKey, declaration.contentType().getValue(), declaration.sha256Hex(), expiresAt);
            LogObject logObject = LogObject.authorized(objectId, StorageProvider.TENCENT_COS,
                    cosProperties.getRegion(), logProperties.getBucket(), declaration.callerService(),
                    declaration.businessType(), declaration.businessId(), declaration.businessSubId(),
                    declaration.requestId(), declaration.fileName(), objectKey, declaration.contentType(),
                    declaration.sizeBytes(), declaration.sha256Hex(), declaration.privacyLevel(), expiresAt, now);
            try {
                logObjectRepository.insert(logObject);
                registered = logObject;
                result = toUploadResult(logObject, uploadUrl);
                log.info("创建日志对象上传授权 objectId[{}] caller[{}] businessType[{}]", objectId,
                        declaration.callerService(), declaration.businessType());
                break;
            } catch (DuplicateKeyException duplicateKeyException) {
                log.warn("日志对象唯一键冲突，重读幂等键 objectId[{}] caller[{}]", objectId,
                        declaration.callerService());
            }
        }
        if (registered == null) {
            throw new OssException(OssErrorCode.INTERNAL_ERROR, "failed to create log object registration");
        }
        return result;
    }

    /**
     * 完成日志上传：归属校验 → 首次受理时间 → 事务外 HEAD 核验 → 条件更新（§8.4、§9.2）。
     *
     * @param cmd 完成命令
     * @return 已核验的日志对象
     */
    public LogObjectResult complete(CompleteLogObjectUploadCmd cmd) {
        String callerService = requireCaller(cmd.getCallerService());
        LogObject logObject = loadOwned(cmd.getObjectId(), callerService);
        Instant now = Instant.now();
        if (logObject.isUploaded()) {
            return toResult(logObject);
        }
        if (logObject.isFailed()) {
            throw failureException(logObject.getFailureReason());
        }
        if (logObject.isExpired()) {
            throw new OssException(OssErrorCode.LOG_STATE_CONFLICT);
        }
        if (logObject.isUploadExpired(now)) {
            return expireThenThrow(logObject, callerService, now);
        }
        if (logObject.recordCompleteReceived(now)) {
            logObjectRepository.recordCompleteReceived(logObject);
        }
        ObjectHeadMetadata metadata = objectStorageGateway.headObjectWithMetadata(logObject.getBucketName(),
                logObject.getObjectKey());
        if (metadata == null) {
            return failComplete(logObject, callerService, LogFailureReason.OBJECT_NOT_FOUND, null, null, null, now);
        }
        if (metadata.getContentLength() != logObject.getDeclaredSize()) {
            return failComplete(logObject, callerService, LogFailureReason.SIZE_MISMATCH,
                    metadata.getContentLength(), metadata.getSha256Hex(), metadata.getEtag(), now);
        }
        if (!logObject.getContentType().getValue().equalsIgnoreCase(normalizeContentType(metadata.getContentType()))) {
            return failComplete(logObject, callerService, LogFailureReason.CONTENT_TYPE_MISMATCH,
                    metadata.getContentLength(), metadata.getSha256Hex(), metadata.getEtag(), now);
        }
        if (metadata.getSha256Hex() == null
                || !logObject.getDeclaredSha256().equalsIgnoreCase(normalizeSha256(metadata.getSha256Hex()))) {
            return failComplete(logObject, callerService, LogFailureReason.DIGEST_MISMATCH,
                    metadata.getContentLength(), metadata.getSha256Hex(), metadata.getEtag(), now);
        }
        if (logObject.isUploadExpired(now)) {
            return expireThenThrow(logObject, callerService, now);
        }
        logObject.markUploaded(metadata.getContentLength(), metadata.getSha256Hex(), metadata.getEtag(),
                metadata.getContentType(), now);
        if (logObjectRepository.updateAuthorizedToUploaded(logObject)) {
            log.info("日志对象上传核验成功 objectId[{}] caller[{}]", logObject.getObjectId(), callerService);
            return toResult(logObject);
        }
        return resolveAfterConditionFailure(cmd.getObjectId(), callerService);
    }

    /**
     * 查询登记事实；过期的 AUTHORIZED 记录先惰性转为 EXPIRED（§8.3、§9.3）。
     *
     * @param objectId          对象标识
     * @param callerServiceName 调用方服务名
     * @return 登记事实
     */
    public LogObjectResult detail(String objectId, String callerServiceName) {
        String callerService = requireCaller(callerServiceName);
        LogObject logObject = loadOwned(objectId, callerService);
        Instant now = Instant.now();
        if (logObject.isUploadExpired(now)) {
            logObject.markExpired(now);
            if (!logObjectRepository.updateAuthorizedToExpired(logObject)) {
                LogObject latest = loadOwned(objectId, callerService);
                return toResult(latest);
            }
            log.info("日志对象授权已过期 objectId[{}] caller[{}]", objectId, callerService);
        }
        return toResult(logObject);
    }

    /**
     * 申请短期下载地址：仅本人 UPLOADED 对象可申请（§8.5）。
     *
     * @param objectId          对象标识
     * @param callerServiceName 调用方服务名
     * @return 下载地址
     */
    public LogObjectDownloadUrlResult downloadUrl(String objectId, String callerServiceName) {
        String callerService = requireCaller(callerServiceName);
        LogObject logObject = loadOwned(objectId, callerService);
        if (!logObject.isUploaded()) {
            throw new OssException(OssErrorCode.LOG_STATE_CONFLICT);
        }
        Instant expiresAt = Instant.now().plusSeconds(logProperties.getDownloadExpirationSeconds());
        String downloadUrl = objectStorageGateway.generateDownloadUrl(logObject.getBucketName(),
                logObject.getObjectKey(), expiresAt);
        log.info("签发日志对象短期下载地址 objectId[{}] caller[{}]", objectId, callerService);
        return LogObjectDownloadUrlResult.builder()
                .objectId(objectId)
                .downloadUrl(downloadUrl)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * 调用方隔离的数据库分页查询（§8.6）。
     *
     * @param query 查询条件（包含服务端注入的调用方）
     * @return 日志对象列表
     */
    public List<LogObjectResult> search(LogObjectQuery query) {
        String callerService = requireCaller(query.getCallerService());
        if (query.getBeginTime() != null && query.getEndTime() != null
                && !query.getEndTime().isAfter(query.getBeginTime())) {
            throw new OssException(OssErrorCode.PARAMETER_INVALID, "endTime must be greater than beginTime");
        }
        LogObjectSearchCriteria criteria = LogObjectSearchCriteria.builder()
                .callerService(callerService)
                .businessType(parseBusinessType(query.getBusinessType()))
                .businessId(query.getBusinessId() == null || query.getBusinessId().isBlank() ? null
                        : query.getBusinessId().trim())
                .state(parseState(query.getState()))
                .beginTime(query.getBeginTime())
                .endTime(query.getEndTime())
                .build();
        List<LogObject> logObjects = logObjectRepository.search(criteria);
        return PageUtil.convert(logObjects, this::toResult);
    }

    /**
     * 小批量将到期 AUTHORIZED 记录转为 EXPIRED（§9.3）。
     *
     * @param now 当前时间
     * @return 更新行数
     */
    public int expireAuthorizedBatch(Instant now) {
        int batchSize = logProperties.getExpirationScanBatchSize();
        int expired = logObjectRepository.expireAuthorizedBatch(now, batchSize);
        if (expired > 0) {
            log.info("日志对象过期扫描完成，本批更新[{}]条", expired);
        }
        return expired;
    }

    /**
     * 将领域对象转换为应用层结果。
     *
     * @param logObject 领域对象
     * @return 应用层结果
     */
    public LogObjectResult toResult(LogObject logObject) {
        return LogObjectResult.builder()
                .objectId(logObject.getObjectId())
                .businessType(logObject.getBusinessType() == null ? null : logObject.getBusinessType().name())
                .businessId(logObject.getBusinessId())
                .businessSubId(logObject.getBusinessSubId())
                .objectKey(logObject.getObjectKey())
                .contentType(logObject.getContentType() == null ? null : logObject.getContentType().getValue())
                .sizeBytes(logObject.getDeclaredSize())
                .actualSizeBytes(logObject.getActualSize())
                .sha256Hex(logObject.getDeclaredSha256())
                .actualSha256Hex(logObject.getActualSha256())
                .etag(logObject.getEtag())
                .uploadState(logObject.getUploadState() == null ? null : logObject.getUploadState().name())
                .failureReason(logObject.getFailureReason() == null ? null : logObject.getFailureReason().name())
                .authorizedAt(logObject.getAuthorizedAt())
                .uploadedAt(logObject.getUploadedAt())
                .expiresAt(logObject.getExpiresAt())
                .receivedAt(logObject.getReceivedAt())
                .build();
    }

    private UploadDeclaration validate(GenerateLogObjectUploadCmd cmd, String callerServiceName) {
        try {
            String callerService = LogValidationPolicy.validateCallerService(callerServiceName);
            LogBusinessType businessType = LogValidationPolicy.validateBusinessType(cmd.getBusinessType());
            String businessId = LogValidationPolicy.validateBusinessId("businessId", cmd.getBusinessId());
            String businessSubId = LogValidationPolicy.validateOptionalBusinessSubId("businessSubId",
                    cmd.getBusinessSubId());
            String requestId = LogValidationPolicy.validateBusinessId("requestId", cmd.getRequestId());
            String fileName = LogValidationPolicy.validateFileName(cmd.getFileName());
            LogContentType contentType = LogValidationPolicy.validateContentType(cmd.getContentType());
            long sizeBytes = LogValidationPolicy.validateDeclaredSize(cmd.getSizeBytes(),
                    logProperties.getMaxSizeBytes());
            String sha256Hex = LogValidationPolicy.validateSha256(cmd.getSha256Hex());
            String privacyLevel = LogValidationPolicy.validatePrivacyLevel(cmd.getPrivacyLevel());
            int ttlSeconds = LogValidationPolicy.resolveTtlSeconds(cmd.getTtlSeconds(),
                    logProperties.getUploadExpirationSeconds(), logProperties.getMaxUploadExpirationSeconds());
            return new UploadDeclaration(callerService, businessType, businessId, businessSubId, requestId, fileName,
                    contentType, sizeBytes, sha256Hex, privacyLevel, ttlSeconds);
        } catch (LogValidationException exception) {
            throw new OssException(OssErrorCode.PARAMETER_INVALID, exception.getMessage());
        }
    }

    private LogObjectUploadResult reuseOrReject(LogObject existing, UploadDeclaration declaration) {
        long effectiveTtlSeconds = Duration.between(existing.getAuthorizedAt(), existing.getExpiresAt()).getSeconds();
        boolean sameDeclaration = Objects.equals(existing.getBusinessId(), declaration.businessId())
                && Objects.equals(existing.getBusinessSubId(), declaration.businessSubId())
                && Objects.equals(existing.getFileName(), declaration.fileName())
                && existing.getContentType() == declaration.contentType()
                && existing.getDeclaredSize() == declaration.sizeBytes()
                && Objects.equals(existing.getDeclaredSha256(), declaration.sha256Hex())
                && Objects.equals(existing.getPrivacyLevel(), declaration.privacyLevel())
                && effectiveTtlSeconds == declaration.ttlSeconds();
        if (!sameDeclaration) {
            log.warn("日志对象幂等键参数冲突 objectId[{}] caller[{}] requestId[{}]", existing.getObjectId(),
                    declaration.callerService(), declaration.requestId());
            throw new OssException(OssErrorCode.LOG_IDEMPOTENCY_CONFLICT);
        }
        if (!existing.isAuthorized()) {
            throw new OssException(OssErrorCode.LOG_STATE_CONFLICT);
        }
        Instant now = Instant.now();
        if (existing.isUploadExpired(now)) {
            existing.markExpired(now);
            logObjectRepository.updateAuthorizedToExpired(existing);
            throw new OssException(OssErrorCode.LOG_STATE_CONFLICT);
        }
        String uploadUrl = objectStorageGateway.generateUploadUrlWithMetadata(existing.getBucketName(),
                existing.getObjectKey(), existing.getContentType().getValue(), existing.getDeclaredSha256(),
                existing.getExpiresAt());
        log.info("复用日志对象上传授权 objectId[{}] caller[{}] requestId[{}]", existing.getObjectId(),
                declaration.callerService(), declaration.requestId());
        return toUploadResult(existing, uploadUrl);
    }

    private LogObjectUploadResult toUploadResult(LogObject logObject, String uploadUrl) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_CONTENT_TYPE, logObject.getContentType().getValue());
        headers.put(HEADER_SHA256, logObject.getDeclaredSha256());
        return LogObjectUploadResult.builder()
                .objectId(logObject.getObjectId())
                .objectKey(logObject.getObjectKey())
                .uploadUrl(uploadUrl)
                .headers(headers)
                .expiresAt(logObject.getExpiresAt())
                .maxSizeBytes(logProperties.getMaxSizeBytes())
                .build();
    }

    /**
     * 确定性核验失败：条件更新 FAILED 后返回对应 422；并发成功则以最新事实为准。
     */
    private LogObjectResult failComplete(LogObject logObject, String callerService, LogFailureReason reason,
                                         Long observedSize, String observedSha256, String observedEtag, Instant now) {
        if (logObject.isUploadExpired(now)) {
            return expireThenThrow(logObject, callerService, now);
        }
        logObject.markFailed(reason, observedSize, observedSha256, observedEtag, now);
        if (logObjectRepository.updateAuthorizedToFailed(logObject)) {
            log.warn("日志对象核验失败 objectId[{}] caller[{}] reason[{}]", logObject.getObjectId(), callerService,
                    reason);
            throw failureException(reason);
        }
        LogObject latest = loadOwned(logObject.getObjectId(), callerService);
        if (latest.isUploaded()) {
            return toResult(latest);
        }
        if (latest.isFailed()) {
            throw failureException(latest.getFailureReason());
        }
        throw new OssException(OssErrorCode.LOG_STATE_CONFLICT);
    }

    /**
     * 惰性过期：条件更新 EXPIRED 后抛状态冲突；并发完成成功则返回 UPLOADED 事实。
     */
    private LogObjectResult expireThenThrow(LogObject logObject, String callerService, Instant now) {
        logObject.markExpired(now);
        if (!logObjectRepository.updateAuthorizedToExpired(logObject)) {
            LogObject latest = loadOwned(logObject.getObjectId(), callerService);
            if (latest.isUploaded()) {
                return toResult(latest);
            }
            if (latest.isFailed()) {
                throw failureException(latest.getFailureReason());
            }
        }
        log.info("日志对象授权已过期 objectId[{}] caller[{}]", logObject.getObjectId(), callerService);
        throw new OssException(OssErrorCode.LOG_STATE_CONFLICT);
    }

    /**
     * 条件更新失败后重读：已 UPLOADED 按幂等返回，FAILED 返回原确定性失败。
     */
    private LogObjectResult resolveAfterConditionFailure(String objectId, String callerService) {
        LogObject latest = loadOwned(objectId, callerService);
        if (latest.isUploaded()) {
            return toResult(latest);
        }
        if (latest.isFailed()) {
            throw failureException(latest.getFailureReason());
        }
        throw new OssException(OssErrorCode.LOG_STATE_CONFLICT);
    }

    private LogObject loadOwned(String objectId, String callerService) {
        LogObject logObject = logObjectRepository.findByObjectId(objectId)
                .orElseThrow(() -> new OssException(OssErrorCode.LOG_OBJECT_NOT_FOUND));
        if (!logObject.belongsTo(callerService)) {
            log.warn("日志对象[{}]不属于调用方[{}]", objectId, callerService);
            throw new OssException(OssErrorCode.LOG_NOT_OWNER);
        }
        return logObject;
    }

    private String requireCaller(String callerServiceName) {
        if (callerServiceName == null || callerServiceName.isBlank()) {
            throw new OssException(OssErrorCode.INVALID_TOKEN);
        }
        try {
            return LogValidationPolicy.validateCallerService(callerServiceName);
        } catch (LogValidationException exception) {
            throw new OssException(OssErrorCode.PARAMETER_INVALID, "invalid caller service name");
        }
    }

    private LogBusinessType parseBusinessType(String businessType) {
        if (businessType == null || businessType.isBlank()) {
            return null;
        }
        return LogBusinessType.fromValue(businessType)
                .orElseThrow(() -> new OssException(OssErrorCode.PARAMETER_INVALID, "unsupported business type"));
    }

    private LogUploadState parseState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        try {
            return LogUploadState.valueOf(state.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new OssException(OssErrorCode.PARAMETER_INVALID, "unsupported log upload state");
        }
    }

    private OssException failureException(LogFailureReason reason) {
        if (reason == null) {
            return new OssException(OssErrorCode.LOG_STATE_CONFLICT);
        }
        return switch (reason) {
            case OBJECT_NOT_FOUND -> new OssException(OssErrorCode.LOG_OBJECT_MISSING);
            case SIZE_MISMATCH -> new OssException(OssErrorCode.LOG_SIZE_MISMATCH);
            case DIGEST_MISMATCH -> new OssException(OssErrorCode.LOG_DIGEST_MISMATCH);
            case CONTENT_TYPE_MISMATCH -> new OssException(OssErrorCode.LOG_CONTENT_TYPE_MISMATCH);
        };
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

    /**
     * 规范化后的上传申请声明
     */
    private record UploadDeclaration(String callerService, LogBusinessType businessType, String businessId,
                                     String businessSubId, String requestId, String fileName,
                                     LogContentType contentType, long sizeBytes, String sha256Hex,
                                     String privacyLevel, int ttlSeconds) {
    }

}
