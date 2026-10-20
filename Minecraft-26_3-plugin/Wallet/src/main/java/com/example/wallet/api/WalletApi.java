package com.example.wallet.api;

/**
 * Wallet 对外入口（与 MainMenuApi 同样的用法）。
 *
 * <p>其它插件：{@code compileOnly project(':Wallet')}，运行期用
 * {@link #wallet()} / {@link #walletOrNull()} 取得 {@link WalletService}。
 */
public final class WalletApi {

    private static volatile WalletService service;

    private WalletApi() {
    }

    /** 钱包插件是否已加载并可用。 */
    public static boolean isAvailable() {
        return service != null;
    }

    /**
     * 取得钱包服务。
     *
     * @throws IllegalStateException Wallet 尚未加载（没装或没在 paper-plugin.yml 里声明依赖）
     */
    public static WalletService wallet() {
        final WalletService current = service;
        if (current == null) {
            throw new IllegalStateException(
                    "Wallet 尚未加载。请在 paper-plugin.yml 的 dependencies.server 中声明 Wallet");
        }
        return current;
    }

    /** 取得钱包服务；不可用时返回 {@code null}（软依赖场景）。 */
    public static WalletService walletOrNull() {
        return service;
    }

    // ------------------------------------------------------------------
    // 以下为 Wallet 内部使用
    // ------------------------------------------------------------------

    public static void install(final WalletService implementation) {
        service = implementation;
    }

    public static void uninstall() {
        service = null;
    }
}
