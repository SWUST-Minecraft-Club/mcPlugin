package com.example.mainmenu.arcmenu;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/** Only publish texture hashes ArcMenu can build; never forward unchecked profile properties. */
final class HeadMaterial {
    static final String FALLBACK = "minecraft:player_head";
    private static final Pattern PATH = Pattern.compile("/texture/([0-9a-fA-F]{32,64})");

    private HeadMaterial() { }

    static String of(final PlayerProfile profile) {
        if (profile != null) {
            for (final var property : profile.getProperties()) {
                if ("textures".equals(property.getName())) {
                    final String material = texture(property.getValue());
                    if (!FALLBACK.equals(material)) {
                        return material;
                    }
                }
            }
        }
        return FALLBACK;
    }

    static String texture(final String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 32768) {
            return FALLBACK;
        }
        try {
            final String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            final var skin = JsonParser.parseString(json).getAsJsonObject()
                    .getAsJsonObject("textures").getAsJsonObject("SKIN");
            final URI url = URI.create(skin.get("url").getAsString());
            final var path = PATH.matcher(url.getPath());
            if (("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()))
                    && "textures.minecraft.net".equalsIgnoreCase(url.getRawAuthority())
                    && url.getRawQuery() == null && url.getRawFragment() == null && path.matches()) {
                return "<skull:" + path.group(1).toLowerCase(Locale.ROOT) + ">";
            }
        } catch (final RuntimeException ignored) {
            // Missing, malformed, or custom authentication textures must not break the whole menu.
        }
        return FALLBACK;
    }
}
