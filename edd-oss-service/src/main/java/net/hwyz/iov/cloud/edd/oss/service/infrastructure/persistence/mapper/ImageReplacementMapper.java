package net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.po.ImageReplacementPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图片替换任务表 Mapper（EDD-OSS-DSN-CR-002 §4.1）。
 * <p>
 * 直接继承 MyBatis-Plus {@link BaseMapper}，不使用已废弃 BaseDao。
 *
 * @author hwyz_leo
 */
@Mapper
public interface ImageReplacementMapper extends BaseMapper<ImageReplacementPo> {

}
