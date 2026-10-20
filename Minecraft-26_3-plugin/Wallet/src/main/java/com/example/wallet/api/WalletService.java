package com.example.wallet.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 钱包服务：给后续插件依赖的公开接口。
 *
 * <p>约定：
 * <ul>
 *     <li><b>官网是唯一数据源</b>：余额与流水都来自 YuDream 官网，本插件不在本地保存余额。</li>
 *     <li><b>所有加减货币都必须调这里的 credit / debit</b>：它们直接调用官网接口，
 *         官网流水就是"上报"结果；本地没有绕过官网的写路径。</li>
 *     <li>方法全部异步（HTTP），不要在服务端主线程阻塞等待结果。</li>
 *     <li>{@code businessNo} 是官网的幂等键：重试时传同一个值不会重复记账；
 *         不传时由插件生成随机值（只适合"确定只调用一次"的场景）。</li>
 * </ul>
 *
 * <p>用法：{@code compileOnly project(':Wallet')}，运行期通过 {@link WalletApi#wallet()} 取实例。
 */
public interface WalletService {

    /** 官网全部货币（含官网启用状态；本服是否启用看 {@code config.yml} 的 assets）。 */
    CompletableFuture<List<WalletAsset>> assets();

    /** 玩家在某种货币上的余额。 */
    CompletableFuture<WalletBalance> balance(UUID playerId, String playerName, String assetCode);

    /** 给玩家加货币（官网记一条 CREDIT 流水）。 */
    CompletableFuture<WalletTransaction> credit(UUID playerId, String playerName, String assetCode,
                                                BigDecimal amount, String businessNo, String remark);

    /** 给玩家扣货币（官网记一条 DEBIT 流水）。 */
    CompletableFuture<WalletTransaction> debit(UUID playerId, String playerName, String assetCode,
                                               BigDecimal amount, String businessNo, String remark);

    /** 给玩家加货币（自动生成幂等键；只适合确定只调用一次的场景）。 */
    default CompletableFuture<WalletTransaction> credit(final UUID playerId, final String playerName,
                                                        final String assetCode, final BigDecimal amount,
                                                        final String remark) {
        return credit(playerId, playerName, assetCode, amount, UUID.randomUUID().toString(), remark);
    }

    /** 给玩家扣货币（自动生成幂等键；只适合确定只调用一次的场景）。 */
    default CompletableFuture<WalletTransaction> debit(final UUID playerId, final String playerName,
                                                       final String assetCode, final BigDecimal amount,
                                                       final String remark) {
        return debit(playerId, playerName, assetCode, amount, UUID.randomUUID().toString(), remark);
    }

    /** 玩家流水（按时间倒序）。 */
    CompletableFuture<WalletTransactionPage> transactions(UUID playerId, String playerName, int page, int size);

    /** 只读缓存：官网货币列表（没有缓存时返回空列表，不触发网络）。 */
    List<WalletAsset> cachedAssets();

    /** 只读缓存：余额（没有缓存时返回 {@code null}，不触发网络）。菜单渲染用。 */
    WalletBalance cachedBalance(UUID playerId, String assetCode);
}
