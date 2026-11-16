package net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.po.LogObjectPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日志对象表 Mapper（EDD-OSS-DSN-CR-003 §5）。
 * <p>
 * 直接继承 MyBatis-Plus {@link BaseMapper}，不使用已废弃 BaseDao。
 *
 * @author hwyz_leo
 */
@Mapper
public interface LogObjectMapper extends BaseMapper<LogObjectPo> {

}
