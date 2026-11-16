package net.hwyz.iov.cloud.edd.oss.service.domain.repository;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.LogObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.query.LogObjectSearchCriteria;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 日志对象仓储（EDD-OSS-DSN-CR-003 §6.2）。
 * <p>
 * 不得返回 PO、Page 或任何 MyBatis 类型；不使用物理删除；状态更新必须带状态与
 * {@code row_version} 条件。
 *
 * @author hwyz_leo
 */
public interface LogObjectRepository {

    /**
     * 按 objectId 查询。
     *
     * @param objectId 对象标识
     * @return 日志对象
     */
    Optional<LogObject> findByObjectId(String objectId);

    /**
     * 按调用方幂等键查询。
     *
     * @param callerService 调用方服务名
     * @param businessType  业务类型
     * @param requestId     幂等键
     * @return 日志对象
     */
    Optional<LogObject> findByRequest(String callerService, LogBusinessType businessType, String requestId);

    /**
     * 插入 AUTHORIZED 登记。
     *
     * @param logObject 日志对象
     * @return 已回填主键的日志对象
     */
    LogObject insert(LogObject logObject);

    /**
     * 记录首次 complete 受理时间：仅当 received_at 为空且状态 AUTHORIZED。
     *
     * @param logObject 日志对象
     * @return 是否写入
     */
    boolean recordCompleteReceived(LogObject logObject);

    /**
     * 条件更新 AUTHORIZED → UPLOADED（object_id + state + row_version）。
     *
     * @param logObject 日志对象
     * @return 是否更新成功
     */
    boolean updateAuthorizedToUploaded(LogObject logObject);

    /**
     * 条件更新 AUTHORIZED → FAILED（object_id + state + row_version）。
     *
     * @param logObject 日志对象
     * @return 是否更新成功
     */
    boolean updateAuthorizedToFailed(LogObject logObject);

    /**
     * 条件更新 AUTHORIZED → EXPIRED（object_id + state + row_version）。
     *
     * @param logObject 日志对象
     * @return 是否更新成功
     */
    boolean updateAuthorizedToExpired(LogObject logObject);

    /**
     * 小批量将已到期的 AUTHORIZED 记录条件更新为 EXPIRED。
     *
     * @param now       当前时间
     * @param batchSize 单批上限
     * @return 更新行数
     */
    int expireAuthorizedBatch(Instant now, int batchSize);

    /**
     * 条件搜索（数据库物理分页由调用侧分页插件驱动）。
     *
     * @param criteria 搜索条件
     * @return 日志对象列表
     */
    List<LogObject> search(LogObjectSearchCriteria criteria);

}
