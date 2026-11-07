package com.example.yudreampresence.report;

import java.io.IOException;

/** 上报传输层；测试里替换成假实现，核心队列不依赖 HTTP。 */
@FunctionalInterface
public interface ReportTransport {

    /**
     * @param relativePath 例如 {@code players/join}、{@code players/snapshot}
     * @param jsonBody     请求体
     */
    HttpResult post(String relativePath, String jsonBody) throws IOException;
}
