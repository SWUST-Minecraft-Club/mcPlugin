package online.yudream.minecraft.bridge.core.log;

public interface LogSink {
   void info(String var1);

   void warn(String var1);

   void warn(String var1, Throwable var2);

   default void debug(String message) {
   }
}
