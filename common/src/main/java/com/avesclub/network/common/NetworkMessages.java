package com.avesclub.network.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NetworkMessages {

    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_PUBLIC = "PUBLIC";
    public static final String TYPE_PLAYER = "PLAYER";
    public static final String TYPE_PRESENCE = "PRESENCE";
    public static final String TYPE_SNAPSHOT = "SNAPSHOT";
    public static final String TYPE_RESYNC = "RESYNC";
    public static final String TYPE_CONFIG = "CONFIG";

    public static final String ACTION_JOIN = "JOIN";
    public static final String ACTION_SWITCH = "SWITCH";
    public static final String ACTION_QUIT = "QUIT";

    private static final Gson GSON = new Gson();

    private NetworkMessages() {
    }

    public static String chat(String server, UUID uuid, String player, String componentJson) {
        JsonObject json = base(TYPE_CHAT);
        json.addProperty("server", server);
        json.addProperty("uuid", uuid.toString());
        json.addProperty("player", player);
        json.addProperty("component", componentJson);
        return GSON.toJson(json);
    }

    public static String publicMessage(String server, String componentJson) {
        JsonObject json = base(TYPE_PUBLIC);
        json.addProperty("server", server);
        json.addProperty("component", componentJson);
        return GSON.toJson(json);
    }

    public static String playerMessage(String server, UUID targetUuid, String componentJson) {
        JsonObject json = base(TYPE_PLAYER);
        json.addProperty("server", server);
        json.addProperty("targetUuid", targetUuid.toString());
        json.addProperty("component", componentJson);
        return GSON.toJson(json);
    }

    public static String presence(String action, UUID uuid, String player, String server, String previousServer) {
        JsonObject json = base(TYPE_PRESENCE);
        json.addProperty("action", action);
        json.addProperty("uuid", uuid.toString());
        json.addProperty("player", player);
        if (server != null) {
            json.addProperty("server", server);
        }
        if (previousServer != null) {
            json.addProperty("previousServer", previousServer);
        }
        return GSON.toJson(json);
    }

    public static String snapshot(List<NetworkPlayer> players) {
        JsonObject json = base(TYPE_SNAPSHOT);
        json.add("players", GSON.toJsonTree(players));
        return GSON.toJson(json);
    }

    public static String resync(String server) {
        JsonObject json = base(TYPE_RESYNC);
        json.addProperty("server", server);
        return GSON.toJson(json);
    }

    public static String config(List<String> linkedServers) {
        JsonObject json = base(TYPE_CONFIG);
        json.add("linkedServers", GSON.toJsonTree(linkedServers == null ? List.of() : linkedServers));
        return GSON.toJson(json);
    }

    public static ParsedMessage parse(String raw) {
        JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
        String type = json.get("type").getAsString();
        return switch (type) {
            case TYPE_CHAT -> new Chat(
                    json.get("server").getAsString(),
                    UUID.fromString(json.get("uuid").getAsString()),
                    json.get("player").getAsString(),
                    componentJson(json)
            );
            case TYPE_PUBLIC -> new Public(
                    json.get("server").getAsString(),
                    componentJson(json)
            );
            case TYPE_PLAYER -> new Player(
                    json.get("server").getAsString(),
                    UUID.fromString(json.get("targetUuid").getAsString()),
                    componentJson(json)
            );
            case TYPE_PRESENCE -> new Presence(
                    json.get("action").getAsString(),
                    UUID.fromString(json.get("uuid").getAsString()),
                    json.get("player").getAsString(),
                    json.has("server") ? json.get("server").getAsString() : null,
                    json.has("previousServer") ? json.get("previousServer").getAsString() : null
            );
            case TYPE_SNAPSHOT -> {
                NetworkPlayer[] players = GSON.fromJson(json.get("players"), NetworkPlayer[].class);
                List<NetworkPlayer> list = new ArrayList<>();
                if (players != null) {
                    for (NetworkPlayer player : players) {
                        list.add(player);
                    }
                }
                yield new Snapshot(list);
            }
            case TYPE_RESYNC -> new Resync(json.get("server").getAsString());
            case TYPE_CONFIG -> {
                List<String> linked = new ArrayList<>();
                if (json.has("linkedServers") && json.get("linkedServers").isJsonArray()) {
                    json.getAsJsonArray("linkedServers").forEach(element ->
                            linked.add(element.getAsString()));
                }
                yield new Config(linked);
            }
            default -> throw new IllegalArgumentException("Unknown message type: " + type);
        };
    }

    private static String componentJson(JsonObject json) {
        if (json.has("component")) {
            return json.get("component").getAsString();
        }
        if (json.has("message")) {
            JsonObject text = new JsonObject();
            text.addProperty("text", json.get("message").getAsString());
            return GSON.toJson(text);
        }
        throw new IllegalArgumentException("Message has no component payload");
    }

    private static JsonObject base(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    public sealed interface ParsedMessage permits Chat, Public, Player, Presence, Snapshot, Resync, Config {
    }

    public record Chat(String server, UUID uuid, String player, String componentJson) implements ParsedMessage {
    }

    public record Public(String server, String componentJson) implements ParsedMessage {
    }

    public record Player(String server, UUID targetUuid, String componentJson) implements ParsedMessage {
    }

    public record Presence(
            String action,
            UUID uuid,
            String player,
            String server,
            String previousServer
    ) implements ParsedMessage {
    }

    public record Snapshot(List<NetworkPlayer> players) implements ParsedMessage {
    }

    public record Resync(String server) implements ParsedMessage {
    }

    public record Config(List<String> linkedServers) implements ParsedMessage {
    }
}
