package online.yudream.minecraft.bukkit.compat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;

public final class ServerCompatibility {
   private static final Pattern VERSION_PATTERN = Pattern.compile("\\(MC: ([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?\\)");

   private ServerCompatibility() {
   }

   public static String describe() {
      Version version = detectVersion();
      if (version == null) {
         return "Detected " + Bukkit.getVersion() + ". Using generic Bukkit compatibility mode.";
      } else if (FoliaSchedulerCompat.isFolia()) {
         return "Detected Minecraft " + version + " on Folia. Regionised scheduler compatibility is active.";
      } else if (version.major == 1 && version.minor < 8) {
         return "Detected Minecraft " + version + ". This plugin is built for Bukkit 1.8.8 and newer.";
      } else if (version.major == 1 && version.minor <= 12) {
         return "Detected Minecraft " + version + ". Legacy Bukkit compatibility mode is active.";
      } else {
         return version.major == 1 && version.minor <= 18 ? "Detected Minecraft " + version + ". Modern Bukkit compatibility mode is active." : "Detected Minecraft " + version + ". High-version Bukkit compatibility mode is active.";
      }
   }

   static Version detectVersion() {
      Matcher matcher = VERSION_PATTERN.matcher(Bukkit.getVersion());
      if (!matcher.find()) {
         return null;
      } else {
         int major = parse(matcher.group(1));
         int minor = parse(matcher.group(2));
         int patch = matcher.group(3) == null ? 0 : parse(matcher.group(3));
         return new Version(major, minor, patch);
      }
   }

   private static int parse(String value) {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException var2) {
         return 0;
      }
   }

   static final class Version {
      private final int major;
      private final int minor;
      private final int patch;

      private Version(int major, int minor, int patch) {
         this.major = major;
         this.minor = minor;
         this.patch = patch;
      }

      public String toString() {
         return this.major + "." + this.minor + (this.patch > 0 ? "." + this.patch : "");
      }
   }
}
