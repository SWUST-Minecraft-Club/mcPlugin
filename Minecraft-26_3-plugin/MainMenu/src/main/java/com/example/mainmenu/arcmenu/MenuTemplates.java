package com.example.mainmenu.arcmenu;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/** Installs editable defaults without replacing an administrator's files. */
public final class MenuTemplates {
    public static final List<String> IDS = List.of("mainmenu-home", "mainmenu-features",
            "mainmenu-manage", "mainmenu-attribute-function", "mainmenu-attribute-fixed",
            "mainmenu-attribute-placeholder", "mainmenu-position", "mainmenu-custom", "mainmenu-profile");

    private MenuTemplates() {
    }

    public static void install(final Path directory, final Function<String, InputStream> resources)
            throws IOException {
        Files.createDirectories(directory);
        for (final String id : IDS) {
            final Path target = directory.resolve(id + ".yml");
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream source = resources.apply("arcmenu/" + id + ".yml")) {
                if (source == null) {
                    throw new IOException("Missing bundled menu: " + id);
                }
                try {
                    Files.copy(source, target);
                } catch (final FileAlreadyExistsException ignored) {
                    // Another installer won the race; its file belongs to the administrator.
                }
            }
        }
    }
}
