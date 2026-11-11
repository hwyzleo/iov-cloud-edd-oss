package net.hwyz.iov.cloud.edd.oss.service.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import net.hwyz.iov.cloud.framework.mysql.po.BasePo;

import java.util.Date;

/**
 * 图片替换任务表 持久化对象（EDD-OSS-DSN-CR-002 §6.1.2）。
 *
 * @author hwyz_leo
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_image_replacement")
public class ImageReplacementPo extends BasePo {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("replacement_id")
    private String replacementId;

    @TableField("object_id")
    private String objectId;

    @TableField("active_object_id")
    private String activeObjectId;

    @TableField("staging_object_key")
    private String stagingObjectKey;

    @TableField("backup_object_key")
    private String backupObjectKey;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("declared_content_type")
    private String declaredContentType;

    @TableField("declared_size")
    private Long declaredSize;

    @TableField("actual_content_type")
    private String actualContentType;

    @TableField("actual_size")
    private Long actualSize;

    @TableField("etag")
    private String etag;

    @TableField("status")
    private String status;

    @TableField("expire_time")
    private Date expireTime;

}
