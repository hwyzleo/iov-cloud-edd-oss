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
 * 日志对象表 持久化对象（EDD-OSS-DSN-CR-003 §6.1）。
 *
 * @author hwyz_leo
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_log_object")
public class LogObjectPo extends BasePo {

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

    @TableField("caller_service")
    private String callerService;

    @TableField("business_type")
    private String businessType;

    @TableField("business_id")
    private String businessId;

    @TableField("business_sub_id")
    private String businessSubId;

    @TableField("request_id")
    private String requestId;

    @TableField("file_name")
    private String fileName;

    @TableField("object_key")
    private String objectKey;

    @TableField("content_type")
    private String contentType;

    @TableField("declared_size")
    private Long declaredSize;

    @TableField("actual_size")
    private Long actualSize;

    @TableField("declared_sha256")
    private String declaredSha256;

    @TableField("actual_sha256")
    private String actualSha256;

    @TableField("etag")
    private String etag;

    @TableField("privacy_level")
    private String privacyLevel;

    @TableField("upload_state")
    private String uploadState;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("authorized_at")
    private Date authorizedAt;

    @TableField("uploaded_at")
    private Date uploadedAt;

    @TableField("expires_at")
    private Date expiresAt;

    @TableField("received_at")
    private Date receivedAt;

}
