package net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 编辑默认素材请求（EDD-OSS-DSN-CR-002 §9.3）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDefaultAssetRequest {

    /**
     * 素材名称，去首尾空白后 1～100 字符
     */
    @NotBlank(message = "assetName must not be blank")
    @Size(max = 100, message = "assetName length must not exceed 100")
    private String assetName;

    /**
     * 备注，最大 255 字符；空字符串按无备注处理
     */
    @Size(max = 255, message = "description length must not exceed 255")
    private String description;

}
