package net.hwyz.iov.cloud.edd.oss.service.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageObject;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.aggregate.ImageReplacement;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageObjectRepository;
import net.hwyz.iov.cloud.edd.oss.service.domain.repository.ImageReplacementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 替换最终提交的本地事务（EDD-OSS-DSN-CR-002 §8.4、§6.1.2）。
 * <p>
 * 事务内只做数据库条件更新：图片新元数据与替换任务 COMPLETED 必须原子提交，
 * 任何一项失败整体回滚；不得包含 COS HEAD、复制、删除等网络调用。
 *
 * @author hwyz_leo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageReplacementTxService {

    private final ImageObjectRepository imageObjectRepository;
    private final ImageReplacementRepository imageReplacementRepository;

    /**
     * 原子提交图片替换元数据与替换任务完成状态。
     *
     * @param imageObject 已应用替换元数据的图片对象
     * @param replacement 已标记完成的替换任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void commit(ImageObject imageObject, ImageReplacement replacement) {
        if (!imageObjectRepository.updateUploadedToReplaced(imageObject)) {
            throw new OssException(OssErrorCode.CONCURRENT_CONFLICT,
                    "concurrent image update during replacement commit");
        }
        if (!imageReplacementRepository.updatePromotingToCompleted(replacement)) {
            throw new OssException(OssErrorCode.CONCURRENT_CONFLICT,
                    "concurrent replacement update during replacement commit");
        }
        log.info("替换最终提交成功 objectId[{}] replacementId[{}]", imageObject.getObjectId(),
                replacement.getReplacementId());
    }

}
