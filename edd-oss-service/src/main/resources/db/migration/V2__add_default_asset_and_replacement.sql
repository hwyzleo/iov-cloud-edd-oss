-- EDD-OSS-DSN-CR-002 §6.1：默认素材字段与替换任务表

-- 6.1.1 tb_image_object 增加素材名称；备注复用既有 description 字段
ALTER TABLE `tb_image_object`
    ADD COLUMN `asset_name` VARCHAR(100) NULL COMMENT '默认素材名称' AFTER `service_name`;

-- 6.1.2 替换任务表：活动任务由 uk_active_object_id 保证同一图片最多一个
CREATE TABLE IF NOT EXISTS `tb_image_replacement`
(
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `replacement_id`        VARCHAR(36)  NOT NULL COMMENT '替换任务标识',
    `object_id`             VARCHAR(36)  NOT NULL COMMENT '目标图片对象',
    `active_object_id`      VARCHAR(36)  NULL COMMENT '活动任务约束，终态为 NULL',
    `staging_object_key`    VARCHAR(512) NOT NULL COMMENT '新图片临时对象键',
    `backup_object_key`     VARCHAR(512) NOT NULL COMMENT '原图片备份对象键',
    `original_filename`     VARCHAR(255) NOT NULL COMMENT '新图片文件名',
    `declared_content_type` VARCHAR(64)  NOT NULL COMMENT '声明 MIME',
    `declared_size`         BIGINT       NOT NULL COMMENT '声明大小',
    `actual_content_type`   VARCHAR(64)  NULL COMMENT '实际 MIME',
    `actual_size`           BIGINT       NULL COMMENT '实际大小',
    `etag`                  VARCHAR(255) NULL COMMENT '临时对象 ETag',
    `status`                VARCHAR(16)  NOT NULL COMMENT '替换任务状态',
    `expire_time`           TIMESTAMP(3) NOT NULL COMMENT '临时上传过期时间',
    `description`           VARCHAR(255) NULL COMMENT '故障或任务说明',
    `create_by`             VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`           TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    `modify_by`             VARCHAR(64)  NULL COMMENT '修改人',
    `modify_time`           TIMESTAMP(3) NULL COMMENT '修改时间',
    `row_version`           INT          NOT NULL DEFAULT 1 COMMENT '乐观锁',
    `row_valid`             TINYINT      NOT NULL DEFAULT 1 COMMENT '逻辑有效标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_replacement_id` (`replacement_id`),
    UNIQUE KEY `uk_active_object_id` (`active_object_id`),
    UNIQUE KEY `uk_staging_object_key` (`staging_object_key`),
    UNIQUE KEY `uk_backup_object_key` (`backup_object_key`),
    KEY `idx_object_status_id` (`object_id`, `status`, `id`),
    KEY `idx_status_modify_time` (`status`, `modify_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='图片替换任务表';
