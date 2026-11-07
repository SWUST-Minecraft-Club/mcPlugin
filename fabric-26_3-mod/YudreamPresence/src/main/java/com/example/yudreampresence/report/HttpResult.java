package com.example.yudreampresence.report;

/** HTTP 结果；2xx 视为成功。 */
public record HttpResult(int statusCode, String body) {

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
