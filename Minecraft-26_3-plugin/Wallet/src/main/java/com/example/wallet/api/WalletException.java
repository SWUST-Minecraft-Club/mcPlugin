package com.example.wallet.api;

/** 钱包接口调用失败（官网返回非 2xx，或业务错误）。{@code statusCode} 为 0 表示本地校验失败。 */
public final class WalletException extends RuntimeException {

    private final int statusCode;

    public WalletException(final int statusCode, final String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
