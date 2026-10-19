package online.yudream.minecraft.bridge.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigFile {
   private final List<String> lines = new ArrayList();

   private ConfigFile() {
   }

   public static ConfigFile empty() {
      return new ConfigFile();
   }

   public static ConfigFile load(Path path) throws IOException {
      ConfigFile config = new ConfigFile();
      if (path != null && Files.isRegularFile(path, new LinkOption[0])) {
         config.lines.addAll(Files.readAllLines(path, StandardCharsets.UTF_8));
      }

      return config;
   }

   public static ConfigFile ofText(String text) {
      ConfigFile config = new ConfigFile();
      if (text != null && !text.isEmpty()) {
         for(String line : text.split("\r\n|\r|\n", -1)) {
            config.lines.add(line);
         }

         while(!config.lines.isEmpty() && ((String)config.lines.get(config.lines.size() - 1)).isEmpty()) {
            config.lines.remove(config.lines.size() - 1);
         }
      }

      return config;
   }

   private static String parseKey(String line) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
         int separator = trimmed.indexOf(61);
         if (separator < 0) {
            separator = trimmed.indexOf(58);
         }

         return separator <= 0 ? null : trimmed.substring(0, separator).trim();
      } else {
         return null;
      }
   }

   private static String parseValue(String line) {
      String trimmed = line.trim();
      int separator = trimmed.indexOf(61);
      if (separator < 0) {
         separator = trimmed.indexOf(58);
      }

      if (separator < 0) {
         return "";
      } else {
         String value = trimmed.substring(separator + 1).trim();
         if (value.length() >= 2 && (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"' || value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'')) {
            value = value.substring(1, value.length() - 1);
         }

         return value;
      }
   }

   public Map<String, String> entries() {
      Map<String, String> result = new LinkedHashMap();

      for(String line : this.lines) {
         String key = parseKey(line);
         if (key != null && !result.containsKey(key)) {
            result.put(key, parseValue(line));
         }
      }

      return result;
   }

   public boolean contains(String key) {
      return this.entries().containsKey(key);
   }

   public String get(String key, String fallback) {
      String value = (String)this.entries().get(key);
      return value == null ? fallback : value;
   }

   public boolean getBoolean(String key, boolean fallback) {
      String value = (String)this.entries().get(key);
      if (value != null && !value.isEmpty()) {
         return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
      } else {
         return fallback;
      }
   }

   public int getInt(String key, int fallback) {
      try {
         String value = (String)this.entries().get(key);
         return value != null && !value.isEmpty() ? Integer.parseInt(value) : fallback;
      } catch (NumberFormatException var4) {
         return fallback;
      }
   }

   public long getLong(String key, long fallback) {
      try {
         String value = (String)this.entries().get(key);
         return value != null && !value.isEmpty() ? Long.parseLong(value) : fallback;
      } catch (NumberFormatException var5) {
         return fallback;
      }
   }

   public List<String> getList(String key) {
      List<String> result = new ArrayList();
      String value = (String)this.entries().get(key);
      if (value == null) {
         return result;
      } else {
         for(String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
               result.add(trimmed);
            }
         }

         return result;
      }
   }

   public ConfigFile set(String key, String value) {
      String rendered = key + "=" + (value == null ? "" : value);

      for(int i = 0; i < this.lines.size(); ++i) {
         String existing = parseKey((String)this.lines.get(i));
         if (key.equals(existing)) {
            this.lines.set(i, rendered);
            return this;
         }
      }

      this.lines.add(rendered);
      return this;
   }

   public ConfigFile setIfAbsent(String key, String value) {
      return this.contains(key) ? this : this.set(key, value);
   }

   public ConfigFile setIfAbsent(String key, boolean value) {
      return this.setIfAbsent(key, Boolean.toString(value));
   }

   public ConfigFile setIfAbsent(String key, long value) {
      return this.setIfAbsent(key, Long.toString(value));
   }

   public ConfigFile setIfAbsent(String key, double value) {
      return this.setIfAbsent(key, Double.toString(value));
   }

   public ConfigFile addComment(String comment) {
      this.lines.add(comment != null && !comment.isEmpty() ? "# " + comment : "#");
      return this;
   }

   public ConfigFile addRawLine(String line) {
      this.lines.add(line == null ? "" : line);
      return this;
   }

   public String render() {
      StringBuilder out = new StringBuilder();

      for(String line : this.lines) {
         out.append(line).append(System.lineSeparator());
      }

      return out.toString();
   }

   public void save(Path path) throws IOException {
      Path parent = path.getParent();
      if (parent != null) {
         Files.createDirectories(parent);
      }

      Files.write(path, this.render().getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
   }

   public static ConfigFile loadOrCreate(Path path, String template) throws IOException {
      if (path != null && Files.isRegularFile(path, new LinkOption[0])) {
         return load(path);
      } else {
         ConfigFile config = ofText(template);
         if (path != null) {
            config.save(path);
         }

         return config;
      }
   }
}
