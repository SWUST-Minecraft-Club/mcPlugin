package com.example.town.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** 文件强制刷盘后原子替换；替换成功后不会再用普通写入失败回报误导调用者回滚。 */
public final class AtomicFiles {
    private AtomicFiles() { }

    public static void write(Path file, byte[] data) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            try (FileChannel directoryChannel = FileChannel.open(directory, StandardOpenOption.READ)) {
                directoryChannel.force(true);
            } catch (UnsupportedOperationException | AccessDeniedException ignored) {
                // Windows 不允许打开目录；文件本身仍已强制刷盘。
            } catch (IOException durability) {
                // rename 已提交。此时再抛出会让业务回滚内存，而磁盘已是新数据。
                System.getLogger(AtomicFiles.class.getName()).log(System.Logger.Level.WARNING,
                        "文件已替换，但目录刷盘失败："+file.getFileName(),durability);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
