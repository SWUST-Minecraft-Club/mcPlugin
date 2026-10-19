package online.yudream.minecraft.bridge.core.queue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import online.yudream.minecraft.bridge.core.json.JsonValue;
import online.yudream.minecraft.bridge.core.log.LogSink;

public final class ReportJournal {
   private final Path path;
   private final LogSink log;

   public ReportJournal(Path path, LogSink log) {
      this.path = path;
      this.log = log;
   }

   public boolean isEnabled() {
      return this.path != null;
   }

   public Path getPath() {
      return this.path;
   }

   public List<ReportTask> load() {
      List<ReportTask> tasks = new ArrayList();
      if (this.path != null && Files.isRegularFile(this.path, new LinkOption[0])) {
         int skipped = 0;

         try {
            for(String line : Files.readAllLines(this.path, StandardCharsets.UTF_8)) {
               String trimmed = line.trim();
               if (!trimmed.isEmpty()) {
                  JsonValue node = JsonValue.tryParse(trimmed);
                  ReportTask task = node == null ? null : ReportTask.fromJson(node);
                  if (task == null) {
                     ++skipped;
                  } else {
                     tasks.add(task);
                  }
               }
            }
         } catch (IOException e) {
            this.log.warn("Could not read the YuDream report journal at " + this.path + ": " + e.getMessage());
            return tasks;
         }

         if (!tasks.isEmpty() || skipped > 0) {
            this.log.info("Recovered " + tasks.size() + " pending YuDream report(s) from " + this.path + (skipped > 0 ? " (" + skipped + " unusable line(s) dropped)" : ""));
         }

         return tasks;
      } else {
         return tasks;
      }
   }

   public void save(Collection<ReportTask> tasks) {
      if (this.path != null) {
         StringBuilder out = new StringBuilder();

         for(ReportTask task : tasks) {
            out.append(task.toJson()).append('\n');
         }

         try {
            Path parent = this.path.getParent();
            if (parent != null) {
               Files.createDirectories(parent);
            }

            Path temp = this.path.resolveSibling(this.path.getFileName() + ".tmp");
            Files.write(temp, out.toString().getBytes(StandardCharsets.UTF_8), new OpenOption[0]);

            try {
               Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException var6) {
               Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException e) {
            this.log.warn("Could not write the YuDream report journal at " + this.path + ": " + e.getMessage());
         }

      }
   }
}
