package online.yudream.minecraft.bukkit.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class ChatBridgeOptions {
   private final boolean enabled;
   private final boolean reportEvents;
   private final boolean pollInbound;
   private final long pollIntervalTicks;
   private final String inboundFormat;
   private final boolean debug;

   private ChatBridgeOptions(boolean enabled, boolean reportEvents, boolean pollInbound, long pollIntervalTicks, String inboundFormat, boolean debug) {
      this.enabled = enabled;
      this.reportEvents = reportEvents;
      this.pollInbound = pollInbound;
      this.pollIntervalTicks = pollIntervalTicks;
      this.inboundFormat = inboundFormat;
      this.debug = debug;
   }

   public static ChatBridgeOptions load(FileConfiguration config) {
      int intervalSeconds = config.getInt("chat-bridge.poll-interval-seconds", 3);
      return new ChatBridgeOptions(config.getBoolean("chat-bridge.enabled", true), config.getBoolean("chat-bridge.report-events", true), config.getBoolean("chat-bridge.poll-inbound", true), (long)Math.max(intervalSeconds, 2) * 20L, normalizeFormat(config.getString("chat-bridge.inbound-format", (String)null)), config.getBoolean("chat-bridge.debug", false));
   }

   public static ChatBridgeOptions defaults() {
      return new ChatBridgeOptions(true, true, true, 60L, "§8[§a群§8] §f{sender} §7» §f{content}", false);
   }

   private static String normalizeFormat(String value) {
      String format = value != null && !value.trim().isEmpty() ? value.trim() : "§8[§a群§8] §f{sender} §7» §f{content}";
      return !format.contains("{sender}") && !format.contains("{content}") ? "§8[§a群§8] §f{sender} §7» §f{content}" : format;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean isReportEvents() {
      return this.reportEvents;
   }

   public boolean isPollInbound() {
      return this.pollInbound;
   }

   public long getPollIntervalTicks() {
      return this.pollIntervalTicks;
   }

   public boolean isDebug() {
      return this.debug;
   }

   public String formatInbound(String sender, String content) {
      return this.inboundFormat.replace("{sender}", sanitize(sender)).replace("{content}", sanitize(content));
   }

   private static String sanitize(String value) {
      return value == null ? "" : value.replace('§', '&');
   }
}
