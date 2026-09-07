package com.avesclub.network.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class ComponentCodec {

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.builder()
            .emitLegacyHoverEvent()
            .downsampleColors()
            .build();

    private ComponentCodec() {
    }

    public static String serialize(Component component) {
        return GSON.serialize(component);
    }

    public static Component deserialize(String json) {
        return GSON.deserialize(json);
    }
}
