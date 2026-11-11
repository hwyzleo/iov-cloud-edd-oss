package net.hwyz.iov.cloud.edd.oss.service.domain.repository;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;

import java.util.Optional;

/**
 * 图片替换任务仓储（EDD-OSS-DSN-CR-002 §4.1、§12）。
 * <p>
 * 不返回 PO、Page 或 MyBatis 类型；所有状态更新必须带状态与 row_version 条件。
 *
 * @author hwyz_leo
 */
public interface ImageReplacementRepository {

    /**
     * 插入 PENDING 替换任务；{@code active_object_id} 唯一约束冲突时抛出
     * {@link org.springframework.dao.DuplicateKeyException}。
     *
     * @param replacement 替换任务
     * @return 已回填主键的替换任务
     */
    ImageReplacement insert(ImageReplacement replacement);

    /**
     * 按 replacementId 查询。
     *
     * @param replacementId 替换任务标识
     * @return 替换任务
     */
    Optional<ImageReplacement> findByReplacementId(String replacementId);

    /**
     * 查询目标图片的活动替换任务（PENDING 或 PROMOTING）。
     *
     * @param objectId 目标图片对象标识
     * @return 活动替换任务
     */
    Optional<ImageReplacement> findActiveByObjectId(String objectId);

    /**
     * 条件更新 PENDING → PROMOTING。
     *
     * @param replacement 替换任务
     * @return 是否更新成功
     */
    boolean updatePendingToPromoting(ImageReplacement replacement);

    /**
     * 条件更新 PROMOTING → COMPLETED 并清空活动标识。
     *
     * @param replacement 替换任务
     * @return 是否更新成功
     */
    boolean updatePromotingToCompleted(ImageReplacement replacement);

    /**
     * 条件更新 PROMOTING → FAILED 并清空活动标识。
     *
     * @param replacement 替换任务
     * @return 是否更新成功
     */
    boolean updatePromotingToFailed(ImageReplacement replacement);

    /**
     * 条件更新 PENDING → CANCELLED 并清空活动标识。
     *
     * @param replacement 替换任务
     * @return 是否更新成功
     */
    boolean updatePendingToCancelled(ImageReplacement replacement);

    /**
     * 仅更新 PROMOTING 任务的故障说明（保留状态与活动标识）。
     *
     * @param replacement 替换任务
     * @return 是否更新成功
     */
    boolean updateFaultDescription(ImageReplacement replacement);

}
