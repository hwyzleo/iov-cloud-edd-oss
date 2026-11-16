package net.hwyz.iov.cloud.edd.oss.api.vo.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 日志对象分页响应（EDD-OSS-DSN-CR-003 §8.6）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogObjectPageResponse {

    private long total;
    private int pageNum;
    private int pageSize;
    private List<LogObjectResponse> items;

}
