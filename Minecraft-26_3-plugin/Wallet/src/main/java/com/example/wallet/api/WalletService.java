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
 *     <li>backend=local 使用本地持久账本；website 使用官网；hybrid 按币种 source 分流；测试模式使用独立测试账本。</li>
 *     <li>credit / debit 写入币种对应后端，setBalance 仅限本地；本地币种与 Vault 共用账本及流水。</li>
 *     <li>资金操作返回异步结果，不要在服务端主线程阻塞等待结果。</li>
 *     <li>{@code businessNo} 是当前后端的幂等键：重试时传同一个值不会重复记账；
 *         使用不带业务号的重载时由插件生成随机值（只适合"确定只调用一次"的场景）。</li>
 * </ul>
 *
 * <p>用法：{@code compileOnly project(':Wallet')}，运行期通过 {@link WalletApi#wallet()} 取实例。
 */
public interface WalletService {

    /** 当前后端全部货币（hybrid 仅返回配置币种；本服开关优先取 assets.yml）。 */
    CompletableFuture<List<WalletAsset>> assets();

    /** 玩家在某种货币上的余额。 */
    CompletableFuture<WalletBalance> balance(UUID playerId, String playerName, String assetCode);

    /** 给玩家加货币（记一条 CREDIT 流水）。 */
    CompletableFuture<WalletTransaction> credit(UUID playerId, String playerName, String assetCode,
                                                BigDecimal amount, String businessNo, String remark);

    /** 给玩家扣货币（记一条 DEBIT 流水）。 */
    CompletableFuture<WalletTransaction> debit(UUID playerId, String playerName, String assetCode,
                                               BigDecimal amount, String businessNo, String remark);

    /** 原子设置本地余额，允许 0；官网接口不提供此操作。 */
    default CompletableFuture<WalletTransaction> setBalance(UUID playerId, String playerName, String assetCode,
            BigDecimal amount, String businessNo, String remark) {
        return CompletableFuture.failedFuture(new WalletException(0, "set 仅支持本地币种；官网币种请使用 add/take"));
    }

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

    /** 玩家流水（按时间倒序，全部币种）。 */
    CompletableFuture<WalletTransactionPage> transactions(UUID playerId, String playerName, int page, int size);

    /**
     * 玩家流水（按时间倒序，只取指定币种）。
     *
     * @param assetCodes 要看的币种 code 列表；{@code null} = 全部币种，空列表 = 一条都没有
     */
    CompletableFuture<WalletTransactionPage> transactions(UUID playerId, String playerName,
                                                          List<String> assetCodes, int page, int size);

    /** 只读缓存：货币列表（官网模式没有缓存时返回空列表，不触发网络）。 */
    List<WalletAsset> cachedAssets();

    /** 只读缓存：余额（官网模式没有缓存时返回 {@code null}，不触发网络）。菜单渲染用。 */
    WalletBalance cachedBalance(UUID playerId, String assetCode);
}
