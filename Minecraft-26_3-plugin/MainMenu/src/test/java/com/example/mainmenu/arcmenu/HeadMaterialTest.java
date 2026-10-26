package com.example.mainmenu.arcmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class HeadMaterialTest {
    private static final String HASH = "a".repeat(64);

    private static String encoded(final String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String skin(final String url) {
        return encoded("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}");
    }

    @Test
    void canonicalizesRealSkinPropertiesToTextureHashes() {
        for (final String scheme : new String[] {"http", "https"}) {
            assertEquals("<skull:" + HASH + ">",
                    HeadMaterial.texture(skin(scheme + "://textures.minecraft.net/texture/" + HASH)));
        }
    }

    @Test
    void incompleteOrInvalidAuthenticationProfilesHaveSafeFallbacks() {
        for (final String value : new String[] {"", "not-base64", encoded("{}"), encoded("null"),
                encoded("{\"textures\":{\"CAPE\":{}}}"), encoded("{\"textures\":{\"SKIN\":{\"url\":17}}}"),
                skin("https://custom-skin.example/texture/" + HASH),
                skin("https://textures.minecraft.net.invalid/texture/" + HASH),
                skin("https://textures.minecraft.net/texture/not-a-hash"),
                skin("https://textures.minecraft.net/texture/" + HASH + "?invalid=true"),
                skin("https://textures.minecraft.net:443/texture/" + HASH)}) {
            assertEquals(HeadMaterial.FALLBACK, HeadMaterial.texture(value), value);
        }
        assertEquals(HeadMaterial.FALLBACK, HeadMaterial.texture(null));
        assertEquals(HeadMaterial.FALLBACK, HeadMaterial.of(null));
    }
}
