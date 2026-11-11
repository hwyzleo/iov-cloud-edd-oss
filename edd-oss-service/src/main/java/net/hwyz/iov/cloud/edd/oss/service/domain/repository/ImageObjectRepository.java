package net.hwyz.iov.cloud.edd.oss.service.domain.repository;

import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.query.ImageObjectSearchCriteria;

import java.util.List;
import java.util.Optional;

/**
 * 图片对象仓储（EDD-OSS-DSN-CR-001 §12）。
 * <p>
 * 不得返回 PO、Page 或任何 MyBatis 类型；不使用物理删除。
 *
 * @author hwyz_leo
 */
public interface ImageObjectRepository {

    /**
     * 按 objectId 查询。
     *
     * @param objectId 对象标识
     * @return 图片对象
     */
    Optional<ImageObject> findByObjectId(String objectId);

    /**
     * 插入 PENDING 记录。
     *
     * @param imageObject 图片对象
     * @return 已回填主键的图片对象
     */
    ImageObject insert(ImageObject imageObject);

    /**
     * 条件更新 PENDING → UPLOADED（object_id + status=PENDING + row_version）。
     *
     * @param imageObject 图片对象
     * @return 是否更新成功
     */
    boolean updatePendingToUploaded(ImageObject imageObject);

    /**
     * 条件更新 UPLOADED → DELETED（object_id + status=UPLOADED + row_version）。
     *
     * @param imageObject 图片对象
     * @return 是否更新成功
     */
    boolean updateUploadedToDeleted(ImageObject imageObject);

    /**
     * 条件搜索（数据库物理分页由调用侧分页插件驱动）。
     *
     * @param criteria 搜索条件
     * @return 图片对象列表
     */
    List<ImageObject> search(ImageObjectSearchCriteria criteria);

}
