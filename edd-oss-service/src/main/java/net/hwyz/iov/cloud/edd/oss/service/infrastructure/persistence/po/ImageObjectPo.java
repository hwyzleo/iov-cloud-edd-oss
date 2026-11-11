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
 * 图片对象表 持久化对象（EDD-OSS-DSN-CR-001 §6）。
 *
 * @author hwyz_leo
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_image_object")
public class ImageObjectPo extends BasePo {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("object_id")
    private String objectId;

    @TableField("provider")
    private String provider;

    @TableField("region")
    private String region;

    @TableField("bucket_name")
    private String bucketName;

    @TableField("object_key")
    private String objectKey;

    @TableField("service_name")
    private String serviceName;

    @TableField("asset_name")
    private String assetName;

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

    @TableField("permission")
    private String permission;

    @TableField("status")
    private String status;

    @TableField("expire_time")
    private Date expireTime;

    @TableField("upload_time")
    private Date uploadTime;

    @TableField("delete_time")
    private Date deleteTime;

    @TableField("delete_by")
    private String deleteBy;

}
