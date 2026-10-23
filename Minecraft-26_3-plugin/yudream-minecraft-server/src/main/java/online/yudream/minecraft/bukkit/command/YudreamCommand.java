package online.yudream.minecraft.bukkit.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import online.yudream.minecraft.bukkit.YudreamMinecraftPlugin;
import online.yudream.minecraft.bukkit.api.HttpResult;
import online.yudream.minecraft.bukkit.bridge.BridgeMode;
import online.yudream.minecraft.bukkit.bridge.DownstreamSensor;
import online.yudream.minecraft.bukkit.config.YudreamConfig;
import online.yudream.minecraft.bukkit.compat.FoliaSchedulerCompat;
import online.yudream.minecraft.bukkit.playtime.PlaytimeLedger;
import online.yudream.minecraft.bukkit.playtime.PlaytimeSync;
import online.yudream.minecraft.bukkit.util.PlayerSummaryParser;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class YudreamCommand implements CommandExecutor, TabCompleter {
   private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "status", "sync", "queue", "playtime", "syncplaytime", "mode", "help");
   private final YudreamMinecraftPlugin plugin;

   public YudreamCommand(YudreamMinecraftPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String subcommand = args.length == 0 ? "help" : args[0].toLowerCase();
      if ("reload".equals(subcommand)) {
         this.plugin.reloadBridge();
         sender.sendMessage(prefix() + "Config reloaded.");
         return true;
      } else if ("status".equals(subcommand)) {
         this.status(sender);
         return true;
      } else if ("sync".equals(subcommand)) {
         this.plugin.syncOnlinePlayers();
         sender.sendMessage(prefix() + (this.plugin.isDownstreamMode() ? "Asked the proxy to re-announce the players on this backend." : "Queued join reports for current online players."));
         return true;
      } else if ("queue".equals(subcommand)) {
         sender.sendMessage(prefix() + "Pending reports: " + this.plugin.getReportQueue().size());
         return true;
      } else if ("playtime".equals(subcommand)) {
         this.playtime(sender, args);
         return true;
      } else if ("syncplaytime".equals(subcommand)) {
         this.syncPlaytime(sender);
         return true;
      } else if ("mode".equals(subcommand)) {
         this.mode(sender, label, args);
         return true;
      } else {
         this.help(sender, label);
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         return matching(SUBCOMMANDS, args[0]);
      } else if (args.length == 2 && "playtime".equalsIgnoreCase(args[0])) {
         List<String> names = new ArrayList();
         for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
         }

         return matching(names, args[1]);
      } else {
         return (List<String>)(args.length == 2 && "mode".equalsIgnoreCase(args[0]) ? matching(Arrays.asList("standalone", "downstream"), args[1]) : new ArrayList());
      }
   }

   private static List<String> matching(List<String> candidates, String partial) {
      String prefix = partial == null ? "" : partial.toLowerCase();
      List<String> results = new ArrayList();

      for(String candidate : candidates) {
         if (candidate.startsWith(prefix)) {
            results.add(candidate);
         }
      }

      return results;
   }

   private void mode(CommandSender sender, String label, String[] args) {
      YudreamConfig settings = this.plugin.getSettings();
      if (args.length < 2) {
         sender.sendMessage(prefix() + "Mode: " + ChatColor.WHITE + settings.getDownstream().getMode().getId());
         sender.sendMessage(prefix() + ChatColor.GRAY + "/" + label + " mode standalone|downstream");
      } else {
         String raw = args[1].toLowerCase();
         BridgeMode requested;
         if ("standalone".equals(raw)) {
            requested = BridgeMode.STANDALONE;
         } else {
            if (!"downstream".equals(raw)) {
               sender.sendMessage(prefix() + ChatColor.RED + "Unknown mode '" + args[1] + "'. Use standalone or downstream.");
               return;
            }

            requested = BridgeMode.DOWNSTREAM;
         }

         if (!this.plugin.setMode(requested)) {
            sender.sendMessage(prefix() + ChatColor.RED + "Could not write the mode to config.yml; see the console log.");
         } else {
            sender.sendMessage(prefix() + "Mode set to " + ChatColor.WHITE + requested.getId() + ChatColor.RESET + " and saved to config.yml.");
            if (requested.isDownstream()) {
               sender.sendMessage(prefix() + ChatColor.GRAY + "This server now forwards player activity to the proxy and does not contact YuDream Admin directly" + (this.plugin.getSettings().getDownstream().isFallbackToApi() ? " unless the proxy stays silent." : "."));
            } else {
               sender.sendMessage(prefix() + ChatColor.GRAY + "This server now reports to YuDream Admin itself.");
            }

         }
      }
   }

   private void status(final CommandSender sender) {
      YudreamConfig settings = this.plugin.getSettings();
      sender.sendMessage(prefix() + "Plugin: " + ChatColor.WHITE + "yudream-minecraft-server "
              + this.plugin.getDescription().getVersion() + ChatColor.RESET + " (api-version " + this.plugin.getDescription().getAPIVersion() + ")");
      sender.sendMessage(prefix() + "Mode: " + ChatColor.WHITE + settings.getDownstream().getMode().getId() + ChatColor.RESET + " (" + (settings.isEnabled() ? "enabled" : "disabled") + ")");
      sender.sendMessage(prefix() + "Local playtime: " + ChatColor.GRAY + (settings.getLocalPlaytime().isEnabled() ? "enabled" : "disabled")
              + ", backend sync " + (settings.getLocalPlaytime().isSyncFromBackend() ? "every " + settings.getLocalPlaytime().getSyncIntervalTicks() / 20L + "s" : "off")
              + ", last=" + (this.plugin.getPlaytimeSync() == null ? "n/a" : this.plugin.getPlaytimeSync().describeLastResult()));
      if (settings.isDownstream()) {
         DownstreamSensor sensor = this.plugin.getSensor();
         sender.sendMessage(prefix() + "Proxy link: " + ChatColor.GRAY + (sensor == null ? "sensor not started" : sensor.describeLink()));
         sender.sendMessage(prefix() + "Local reporting: " + ChatColor.GRAY + this.localReportingState(settings));
         sender.sendMessage(prefix() + "Queue: " + this.plugin.getReportQueue().size());
      } else if (!this.plugin.canReport()) {
         sender.sendMessage(prefix() + ChatColor.RED + "Not configured or disabled.");
      } else {
         sender.sendMessage(prefix() + "Checking remote players...");
         FoliaSchedulerCompat.runAsync(this.plugin, new Runnable() {
            public void run() {
               String message;
               try {
                  HttpResult result = YudreamCommand.this.plugin.getApiClient().players(1, 100);
                  if (result.isSuccess()) {
                     PlayerSummaryParser.Summary summary = PlayerSummaryParser.parse(result.getBody());
                     message = YudreamCommand.prefix() + "Remote players: total=" + summary.getTotal() + ", online=" + summary.getOnline() + ", afk=" + summary.getAfk() + ", http=" + result.getStatusCode();
                  } else {
                     message = YudreamCommand.prefix() + ChatColor.RED + "Remote status failed: HTTP " + result.getStatusCode() + " " + YudreamCommand.trim(result.getBody());
                  }
               } catch (Exception e) {
                  message = YudreamCommand.prefix() + ChatColor.RED + "Remote status failed: " + e.getMessage();
               }

               YudreamCommand.this.sendTo(sender, message);
            }
         });
      }
   }

   private String localReportingState(YudreamConfig settings) {
      if (!settings.getDownstream().isFallbackToApi()) {
         return "disabled (the proxy is the only uploader)";
      } else {
         return this.plugin.canReport() ? "active, proxy unreachable" : "armed, proxy reachable";
      }
   }

   /** 立即把本地台账与后端对齐一次，结果直接回给执行者（不用去翻日志）。 */
   private void syncPlaytime(final CommandSender sender) {
      if (this.plugin.getPlaytimeSync() == null) {
         sender.sendMessage(prefix() + ChatColor.RED + "本地时长同步不可用。");
         return;
      }

      if (!this.plugin.getSettings().getLocalPlaytime().isSyncFromBackend()) {
         sender.sendMessage(prefix() + ChatColor.RED + "local-playtime.sync-from-backend 已在配置里关闭。");
         return;
      }

      final Set<UUID> online = new HashSet<>();
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         online.add(player.getUniqueId());
      }

      sender.sendMessage(prefix() + "正在与后端对齐本地时长…");
      FoliaSchedulerCompat.runAsync(this.plugin, new Runnable() {
         public void run() {
            final boolean ok = YudreamCommand.this.plugin.getPlaytimeSync().pullFromBackend(online);
            final String result = YudreamCommand.this.plugin.getPlaytimeSync().describeLastResult();
            YudreamCommand.this.sendTo(sender, prefix() + (ok ? ChatColor.GREEN + "对齐完成：" : ChatColor.RED + "对齐未完成：")
                    + ChatColor.GRAY + result);
         }
      });
   }

   /** 本地在线时长（与后端同口径），方便核对本地统计与后端数字是否一致。 */
   private void playtime(CommandSender sender, String[] args) {
      PlaytimeLedger ledger = this.plugin.getPlaytimeLedger();
      if (ledger == null) {
         sender.sendMessage(prefix() + ChatColor.RED + "Local playtime ledger is not available.");
         return;
      }

      UUID targetId;
      String targetName;
      if (args.length >= 2) {
         targetName = args[1];
         targetId = ledger.findByName(targetName);
         if (targetId == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "No local playtime record for '" + targetName + "'.");
            return;
         }
      } else if (sender instanceof Player) {
         targetId = ((Player)sender).getUniqueId();
         targetName = sender.getName();
      } else {
         sender.sendMessage(prefix() + ChatColor.RED + "Usage: /yudreammc playtime <player>");
         return;
      }

      ledger.saveIfDirty();
      long now = System.currentTimeMillis();
      sender.sendMessage(prefix() + "Local playtime for " + ChatColor.WHITE + targetName + ChatColor.RESET + ":");
      sender.sendMessage(prefix() + ChatColor.GRAY + "  online: " + ChatColor.WHITE + formatDuration(ledger.onlineMillisAt(targetId, now))
              + ChatColor.GRAY + (ledger.isOnline(targetId) ? " (counting now)" : ""));
      sender.sendMessage(prefix() + ChatColor.GRAY + "  afk: " + ChatColor.WHITE + formatDuration(ledger.afkMillisAt(targetId, now)));
      sender.sendMessage(prefix() + ChatColor.GRAY + "  ledger: " + ledger.getFile().getName() + " (players=" + ledger.size() + ")"
              + (this.plugin.getReportJournal() == null ? "" : ", pending reports=" + this.plugin.getReportJournal().size()));

      // 顺手把后端侧的数字也打出来做对照（口径与后端界面一致：含当前这次在线）
      final PlaytimeSync sync = this.plugin.getPlaytimeSync();
      if (sync == null || !this.plugin.canReport()) {
         return;
      }

      final UUID backendTarget = targetId;
      FoliaSchedulerCompat.runAsync(this.plugin, new Runnable() {
         public void run() {
            final PlaytimeSync.BackendNumbers backend = sync.fetchPlayerFromBackend(backendTarget.toString());
            if (backend == null) {
               YudreamCommand.this.sendTo(sender, prefix() + ChatColor.GRAY + "  backend: 查不到该玩家（或列表接口不可用）");
               return;
            }

            YudreamCommand.this.sendTo(sender, prefix() + ChatColor.GRAY + "  backend: online " + ChatColor.WHITE
                    + formatDuration(backend.getOnlineMillis()) + ChatColor.GRAY + ", afk " + ChatColor.WHITE
                    + formatDuration(backend.getAfkMillis()) + ChatColor.GRAY
                    + (backend.isOnline() ? " (online now)" : ""));
         }
      });
   }

   /** 时长显示：hh 小时 mm 分钟（不足 1 小时只显示分钟，0 显示 0分钟）。 */
   static String formatDuration(long millis) {
      long totalMinutes = Math.max(millis, 0L) / 60000L;
      long hours = totalMinutes / 60L;
      long minutes = totalMinutes % 60L;
      if (hours <= 0L) {
         return minutes + "分钟";
      }

      return hours + "小时" + (minutes < 10L ? "0" + minutes : String.valueOf(minutes)) + "分钟";
   }

   private void help(CommandSender sender, String label) {
      sender.sendMessage(prefix() + "/" + label + " mode [standalone|downstream]");
      sender.sendMessage(prefix() + "/" + label + " reload");
      sender.sendMessage(prefix() + "/" + label + " status");
      sender.sendMessage(prefix() + "/" + label + " sync");
      sender.sendMessage(prefix() + "/" + label + " queue");
      sender.sendMessage(prefix() + "/" + label + " playtime [玩家]");
      sender.sendMessage(prefix() + "/" + label + " syncplaytime");
   }

   private static String prefix() {
      return ChatColor.AQUA + "[YuDream] " + ChatColor.RESET;
   }

   /** 异步回调里给玩家回消息：回到玩家所属区域线程；控制台直接发。 */
   private void sendTo(final CommandSender sender, final String message) {
      if (sender instanceof Player) {
         final Player player = (Player) sender;
         FoliaSchedulerCompat.runFor(this.plugin, player, new Runnable() {
            public void run() {
               player.sendMessage(message);
            }
         });
      } else {
         sender.sendMessage(message);
      }
   }

   private static String trim(String body) {
      if (body == null) {
         return "";
      } else {
         return body.length() <= 160 ? body : body.substring(0, 160) + "...";
      }
   }
}
