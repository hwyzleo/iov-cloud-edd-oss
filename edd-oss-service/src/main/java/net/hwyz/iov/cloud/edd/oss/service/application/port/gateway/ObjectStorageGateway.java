package net.hwyz.iov.cloud.edd.oss.service.application.port.gateway;

import net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos.CosObjectMetadata;

import java.time.Instant;

/**
 * 对象存储网关端口（EDD-OSS-DSN-CR-001 §8）。
 * <p>
 * 不暴露 COS SDK 类型；实现负责把厂商异常映射为 OSS 统一异常。
 *
 * @author hwyz_leo
 */
public interface ObjectStorageGateway {

    /**
     * 生成预签名上传地址。
     *
     * @param bucket      Bucket
     * @param key         对象键
     * @param contentType 上传时必须携带的 Content-Type
     * @param expireTime  过期时间
     * @return 预签名地址
     */
    String generateUploadUrl(String bucket, String key, String contentType, Instant expireTime);

    /**
     * 读取对象元数据。
     *
     * @param bucket Bucket
     * @param key    对象键
     * @return 元数据；对象不存在时返回 null
     */
    CosObjectMetadata headObject(String bucket, String key);

    /**
     * 删除对象；对象不存在按幂等删除处理。
     *
     * @param bucket Bucket
     * @param key    对象键
     */
    void deleteObject(String bucket, String key);

    /**
     * 受控对象复制：同 Bucket 复制也显式传递源与目标 Bucket（EDD-OSS-DSN-CR-002 §8.1）。
     *
     * @param sourceBucket      源 Bucket
     * @param sourceKey         源对象键
     * @param destinationBucket 目标 Bucket
     * @param destinationKey    目标对象键
     * @param expectedSourceEtag 源对象 ETag 一致性条件；为空时不设置条件
     */
    void copyObject(String sourceBucket, String sourceKey, String destinationBucket, String destinationKey,
                    String expectedSourceEtag);

    /**
     * 构建公开访问地址。
     *
     * @param region COS 地域
     * @param bucket Bucket
     * @param key    对象键
     * @return 公开访问地址
     */
    String buildPublicUrl(String region, String bucket, String key);

}
