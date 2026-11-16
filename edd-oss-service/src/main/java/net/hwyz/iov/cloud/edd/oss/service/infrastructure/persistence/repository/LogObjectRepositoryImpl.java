package net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.LogObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogUploadState;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.query.LogObjectSearchCriteria;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.LogObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.converter.LogObjectPoConverter;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.mapper.LogObjectMapper;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.po.LogObjectPo;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 日志对象仓储实现（EDD-OSS-DSN-CR-003 §6.2）。
 * <p>
 * 条件更新同时校验状态与 {@code row_version}；不使用物理删除；排序固定 id DESC；
 * {@code search} 强制携带调用方隔离条件。
 *
 * @author hwyz_leo
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class LogObjectRepositoryImpl implements LogObjectRepository {

    private static final String SYSTEM_MODIFIER = "system";

    private final LogObjectMapper logObjectMapper;

    @Override
    public Optional<LogObject> findByObjectId(String objectId) {
        LogObjectPo po = logObjectMapper.selectOne(new LambdaQueryWrapper<LogObjectPo>()
                .eq(LogObjectPo::getObjectId, objectId)
                .last("LIMIT 1"));
        return Optional.ofNullable(LogObjectPoConverter.toDomain(po));
    }

    @Override
    public Optional<LogObject> findByRequest(String callerService, LogBusinessType businessType, String requestId) {
        LogObjectPo po = logObjectMapper.selectOne(new LambdaQueryWrapper<LogObjectPo>()
                .eq(LogObjectPo::getCallerService, callerService)
                .eq(LogObjectPo::getBusinessType, businessType.name())
                .eq(LogObjectPo::getRequestId, requestId)
                .last("LIMIT 1"));
        return Optional.ofNullable(LogObjectPoConverter.toDomain(po));
    }

    @Override
    public LogObject insert(LogObject logObject) {
        LogObjectPo po = LogObjectPoConverter.fromDomain(logObject);
        if (po.getCreateTime() == null) {
            po.setCreateTime(new Timestamp(System.currentTimeMillis()));
        }
        if (po.getModifyTime() == null) {
            po.setModifyTime(new Timestamp(System.currentTimeMillis()));
        }
        if (po.getRowVersion() == null) {
            po.setRowVersion(1);
        }
        if (po.getRowValid() == null) {
            po.setRowValid(Boolean.TRUE);
        }
        logObjectMapper.insert(po);
        logObject.assignId(po.getId());
        return logObject;
    }

    @Override
    public boolean recordCompleteReceived(LogObject logObject) {
        Timestamp receivedAt = logObject.getReceivedAt() == null ? new Timestamp(System.currentTimeMillis())
                : Timestamp.from(logObject.getReceivedAt());
        String modifier = modifierOf(logObject);
        LambdaUpdateWrapper<LogObjectPo> wrapper = new LambdaUpdateWrapper<LogObjectPo>()
                .set(LogObjectPo::getReceivedAt, receivedAt)
                .set(LogObjectPo::getModifyBy, modifier)
                .set(LogObjectPo::getModifyTime, receivedAt)
                .setSql("row_version = row_version + 1")
                .eq(LogObjectPo::getObjectId, logObject.getObjectId())
                .eq(LogObjectPo::getUploadState, LogUploadState.AUTHORIZED.name())
                .isNull(LogObjectPo::getReceivedAt);
        int rows = logObjectMapper.update(null, wrapper);
        if (rows > 0) {
            logObject.increaseRowVersion(modifier, receivedAt.toInstant());
        }
        return rows > 0;
    }

    @Override
    public boolean updateAuthorizedToUploaded(LogObject logObject) {
        Timestamp now = nowOf(logObject);
        String modifier = modifierOf(logObject);
        LambdaUpdateWrapper<LogObjectPo> wrapper = new LambdaUpdateWrapper<LogObjectPo>()
                .set(LogObjectPo::getUploadState, LogUploadState.UPLOADED.name())
                .set(LogObjectPo::getActualSize, logObject.getActualSize())
                .set(LogObjectPo::getActualSha256, logObject.getActualSha256())
                .set(LogObjectPo::getEtag, logObject.getEtag())
                .set(LogObjectPo::getUploadedAt, now)
                .set(LogObjectPo::getReceivedAt, logObject.getReceivedAt() == null ? now
                        : Timestamp.from(logObject.getReceivedAt()))
                .set(LogObjectPo::getFailureReason, null)
                .set(LogObjectPo::getModifyBy, modifier)
                .set(LogObjectPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(LogObjectPo::getObjectId, logObject.getObjectId())
                .eq(LogObjectPo::getUploadState, LogUploadState.AUTHORIZED.name())
                .eq(LogObjectPo::getRowVersion, logObject.getRowVersion());
        int rows = logObjectMapper.update(null, wrapper);
        if (rows > 0) {
            logObject.increaseRowVersion(modifier, now.toInstant());
        }
        return rows > 0;
    }

    @Override
    public boolean updateAuthorizedToFailed(LogObject logObject) {
        Timestamp now = nowOf(logObject);
        String modifier = modifierOf(logObject);
        LambdaUpdateWrapper<LogObjectPo> wrapper = new LambdaUpdateWrapper<LogObjectPo>()
                .set(LogObjectPo::getUploadState, LogUploadState.FAILED.name())
                .set(LogObjectPo::getFailureReason,
                        logObject.getFailureReason() == null ? null : logObject.getFailureReason().name())
                .set(LogObjectPo::getActualSize, logObject.getActualSize())
                .set(LogObjectPo::getActualSha256, logObject.getActualSha256())
                .set(LogObjectPo::getEtag, logObject.getEtag())
                .set(LogObjectPo::getModifyBy, modifier)
                .set(LogObjectPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(LogObjectPo::getObjectId, logObject.getObjectId())
                .eq(LogObjectPo::getUploadState, LogUploadState.AUTHORIZED.name())
                .eq(LogObjectPo::getRowVersion, logObject.getRowVersion());
        int rows = logObjectMapper.update(null, wrapper);
        if (rows > 0) {
            logObject.increaseRowVersion(modifier, now.toInstant());
        }
        return rows > 0;
    }

    @Override
    public boolean updateAuthorizedToExpired(LogObject logObject) {
        Timestamp now = nowOf(logObject);
        String modifier = modifierOf(logObject);
        LambdaUpdateWrapper<LogObjectPo> wrapper = new LambdaUpdateWrapper<LogObjectPo>()
                .set(LogObjectPo::getUploadState, LogUploadState.EXPIRED.name())
                .set(LogObjectPo::getModifyBy, modifier)
                .set(LogObjectPo::getModifyTime, now)
                .setSql("row_version = row_version + 1")
                .eq(LogObjectPo::getObjectId, logObject.getObjectId())
                .eq(LogObjectPo::getUploadState, LogUploadState.AUTHORIZED.name())
                .eq(LogObjectPo::getRowVersion, logObject.getRowVersion());
        int rows = logObjectMapper.update(null, wrapper);
        if (rows > 0) {
            logObject.increaseRowVersion(modifier, now.toInstant());
        }
        return rows > 0;
    }

    @Override
    public int expireAuthorizedBatch(Instant now, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        Timestamp expireTime = Timestamp.from(now);
        LambdaUpdateWrapper<LogObjectPo> wrapper = new LambdaUpdateWrapper<LogObjectPo>()
                .set(LogObjectPo::getUploadState, LogUploadState.EXPIRED.name())
                .set(LogObjectPo::getModifyBy, SYSTEM_MODIFIER)
                .set(LogObjectPo::getModifyTime, expireTime)
                .setSql("row_version = row_version + 1")
                .eq(LogObjectPo::getUploadState, LogUploadState.AUTHORIZED.name())
                .le(LogObjectPo::getExpiresAt, expireTime)
                .last("LIMIT " + batchSize);
        return logObjectMapper.update(null, wrapper);
    }

    @Override
    public List<LogObject> search(LogObjectSearchCriteria criteria) {
        if (criteria.getCallerService() == null || criteria.getCallerService().isBlank()) {
            throw new IllegalArgumentException("callerService is required for log object search");
        }
        LambdaQueryWrapper<LogObjectPo> wrapper = new LambdaQueryWrapper<LogObjectPo>()
                .eq(LogObjectPo::getCallerService, criteria.getCallerService());
        if (criteria.getBusinessType() != null) {
            wrapper.eq(LogObjectPo::getBusinessType, criteria.getBusinessType().name());
        }
        if (criteria.getBusinessId() != null && !criteria.getBusinessId().isBlank()) {
            wrapper.eq(LogObjectPo::getBusinessId, criteria.getBusinessId());
        }
        if (criteria.getState() != null) {
            wrapper.eq(LogObjectPo::getUploadState, criteria.getState().name());
        }
        if (criteria.getBeginTime() != null) {
            wrapper.ge(LogObjectPo::getAuthorizedAt, Timestamp.from(criteria.getBeginTime()));
        }
        if (criteria.getEndTime() != null) {
            wrapper.lt(LogObjectPo::getAuthorizedAt, Timestamp.from(criteria.getEndTime()));
        }
        wrapper.orderByDesc(LogObjectPo::getId);
        return LogObjectPoConverter.toDomainList(logObjectMapper.selectList(wrapper));
    }

    private static String modifierOf(LogObject logObject) {
        String modifyBy = logObject.getModifyBy();
        return modifyBy == null || modifyBy.isBlank() ? logObject.getCallerService() : modifyBy;
    }

    private static Timestamp nowOf(LogObject logObject) {
        return logObject.getModifyTime() == null ? new Timestamp(System.currentTimeMillis())
                : Timestamp.from(logObject.getModifyTime());
    }

}
