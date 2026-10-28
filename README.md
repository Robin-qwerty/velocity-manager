# robin-velocity-manager

Standalone Velocity proxy plugin:

- **Domain routing** — send players to a specific backend server based on the hostname they used to connect.
- **Per-domain MOTD** — different server-list description, hover box, player count, fake version, and favicon per hostname, with feature toggles throughout so unused parts cost nothing.
- **Utility commands** — `/find`, `/lobby`, `/hub`, `/discord`.
- **Grouped join and leave messages** — proxy-side join/quit chat for included servers, silent while a player stays inside a server group.
- **Network online count** — several proxies (for example Java and Geyser/Bedrock) share their player counts through Redis so every server list shows the total.
- **Plugin-message transfers** — any backend plugin can move a player to another server over a plugin-message channel.

## Build

```bash
mvn package
```

Output jar: `velocity-plugin/target/robin-velocity-manager-1.0.0.jar`

## Setup

1. Install the jar on Velocity (`plugins/`).
2. Edit `plugins/robin-velocity-manager/config.yml` (created on first start).
3. Reload with `/velocitymanager reload`, or restart the proxy.

## Configuration

### Velocity (`plugins/robin-velocity-manager/config.yml`)

```yaml
lobby-server: lobby

domain-servers:
  bedwars.avesclub.net: bedwars

discord:
  message: "Join our Discord server!"
  link-text: "discord.gg/avesclub"
  url: "https://discord.gg/avesclub"
```

- **`lobby-server`**: Velocity server name used by `/lobby` and `/hub`.
- **`domain-servers`**: maps the hostname a player used to connect (case-insensitive, port stripped) to a Velocity server name. If no entry matches, Velocity uses its normal default try order.

### Feature toggles (`plugins/robin-velocity-manager/config.yml`)

Turn off anything you don't use so the plugin does less work at runtime:

```yaml
features:
  domain-routing: true
  discord-command: true
  lobby-hub-commands: true
```

- **`domain-routing`** — master switch for routing players by `domain-servers` (above).
- **`discord-command`** — enables/disables `/discord`.
- **`lobby-hub-commands`** — enables/disables `/lobby` and `/hub`.
- `/find` and `/velocitymanager reload` are always available.
- The MOTD feature (below) has its own master switch in `motd.yml`, not here.
- Join/leave messages use `join-leave-messages.enabled`, not this section.

### Join and leave messages (`plugins/robin-velocity-manager/config.yml`)

The proxy sends a join or quit line when a player enters or leaves a server group. Only players who are currently on a server in that group see it. Switching between servers in the same group does not announce. Servers that are not listed in `included-servers` are left alone, so those backends can keep their own join and leave messages.

An included server that is not listed under `groups` is its own group of one. A server listed in a group but missing from `included-servers` is ignored. A server listed in two groups stays in the first one. Names are matched case-insensitively. `%player%` is the username. Legacy `&` codes and MiniMessage tags both work. A blank message is not sent.

```yaml
join-leave-messages:
  enabled: false
  join-message: "&8[&a+&8] &a%player%"
  quit-message: "&8[&c-&8] &c%player%"
  included-servers:
    - lobby
    - survival
    - survival-nether
  groups:
    survival:
      - survival
      - survival-nether
```

### Network online count (`plugins/robin-velocity-manager/config.yml`)

Each Velocity proxy only knows its own players. With this enabled, every proxy writes its count to Redis, and the server-list ping on every proxy shows the sum of all proxies. This applies to the numeric online count in the ping (so Geyser's `passthrough-player-counts` shows it too) and to `<online>` in `motd.yml`. It works whether or not `motd.yml` is enabled.

```yaml
network-players:
  enabled: false
  redis:
    host: 127.0.0.1
    port: 6379
    username: ""
    password: ""
    database: 0
  proxy-id: java
  publish-interval-seconds: 3
  key-ttl-seconds: 10
  key-prefix: "avesclub:players:"
```

- Every proxy uses the same `redis` settings and its own **unique** `proxy-id` (for example `java` and `bedrock`).
- Every `publish-interval-seconds` the proxy runs `SET {key-prefix}{proxy-id} <count> EX {key-ttl-seconds}`. If a proxy stops, its key expires and the others stop counting it. `key-ttl-seconds` must be greater than the interval.
- The ping total is this proxy's live count plus the values of all other keys under `key-prefix` (found with `SCAN`, cached for about one second).
- If Redis is unreachable, or this proxy's own key is missing, the ping shows this proxy's own count. Redis is only touched on a background thread, so a slow or dead Redis never delays a ping. The log shows one warning when Redis goes away and one line when it comes back.
- `<max>` is not summed. It stays Velocity's `show-max-players` or the profile's `max-override`.
- `<online_SERVERNAME>` stays this proxy's own backend count.
- A `motd.yml` profile with `online-override.enabled: true` still shows its override.
- TLS is not used. Keep Redis on a private network.

Redis ACL for a dedicated user (database 0; any other database also needs `+select`):

```
ACL SETUSER avesplayers on >CHANGE_ME resetkeys ~avesclub:players:* resetchannels -@all +set +get +scan +ping
```

The Redis client (Jedis) is shaded and relocated to `com.avesclub.velocity.libs.jedis`, so it does not clash with LuckPerms or other plugins that bundle Jedis.

## Per-domain MOTD (`plugins/robin-velocity-manager/motd.yml`)

Shows a different server-list description, hover box, player count, fake version, and favicon
depending on the hostname a player connected with — the same domain concept as `domain-servers`,
but for the ping instead of routing. Matching is **exact** (lowercase, port stripped).

```yaml
enabled: true

default:
  favicon:
    enabled: false
    mode: SINGLE   # SINGLE = files[0]; RANDOM = a random entry from files on every ping
    files: []
  version:
    name:
      enabled: true
      text: "<gradient:#00c6ff:#0072ff>AvesClub Network</gradient>"
    protocol:
      enabled: false
      value: -1
  player-count:
    hide: false
    online-override:
      enabled: false
      value: 0
    max-override:
      enabled: false
      value: 0
  hover:
    enabled: true
    vanilla: false   # false = show the custom `lines:` below instead of real sample players
    lines:
      - "&7Online: &f<online>&7/&f<max>"
      - "&7Lobby: &f<online_lobby> &8| &7Bedwars: &f<online_bedwars>"
  description:
    enabled: true
    lines:
      - "<center><gradient:#00c6ff:#0072ff><bold>AVESCLUB</bold></gradient>"

domains:
  bedwars.avesclub.net:
    # Anything omitted here (a whole section, or one field inside it) is inherited from
    # `default:` above — not from a hardcoded plugin default.
    hover:
      lines:
        - "&7Bedwars players: &f<online_bedwars>"
    description:
      lines:
        - "<center><gradient:#ff8a00:#e52e71><bold>BEDWARS</bold></gradient>"
```

- **`enabled: false`** turns the whole feature off — Velocity's normal ping is left untouched.
- Text fields accept **both** legacy `&` codes (including `&#RRGGBB` hex) **and** full MiniMessage
  tags in the same string — legacy codes are converted to MiniMessage tags first, so
  `"&aHello <gradient:#00c6ff:#0072ff>World</gradient>"` works as-is.
- **Placeholders** (usable in `description.lines`, `hover.lines`, `version.name.text`):
  - `<online>` / `<max>` — this proxy's online/max count, or this profile's override value.
    With `network-players` enabled, `<online>` is the total across all proxies.
  - `<online_SERVERNAME>` — live online count on one backend server, e.g. `<online_lobby>`,
    `<online_bedwars>` — generated automatically for every server in `velocity.toml`
  - `<newline>` — splits one YAML string into an extra display line
  - `<center>` — must be the first thing in a line; approximately centers it (pixel-width
    approximation of the vanilla font, not pixel-perfect)
- **Favicons**: PNG files placed in `plugins/robin-velocity-manager/favicons/`, referenced by
  filename. Missing/invalid files just log a warning and are skipped.
- Reload with `/velocitymanager reload` after editing `motd.yml`.

## Velocity commands

- `/find <player>` — permission `robinvelocitymanager.find`
- `/lobby`, `/hub` — send to configured lobby server
- `/discord` — clickable Discord link from config
- `/velocitymanager reload` — permission `robinvelocitymanager.reload`

## Backend plugin messaging

Any backend plugin (Paper, Spigot, ...) can move a player to another server via a plugin-message
channel — no dependency on this plugin's jar required on the backend side.

**To send a player to another server from a backend plugin:**

1. Register the outgoing channel: `getServer().getMessenger().registerOutgoingPluginChannel(this, "myplugin:network");`
2. Send a plugin message through the player with two UTF strings: `"CONNECT"` and the target server name.
3. The Velocity plugin handles the transfer automatically.

**Example:**
```java
public final class BackendPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, "myplugin:network");
    }

    public void sendPlayer(Player player, String serverName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        try {
            data.writeUTF("CONNECT");
            data.writeUTF(serverName);
        } catch (IOException e) {
            getLogger().warning("Failed to build plugin message: " + e.getMessage());
            return;
        }

        player.sendPluginMessage(this, "myplugin:network", out.toByteArray());
    }
}
```

## Credits

The per-domain MOTD feature's config layout, placeholder style, and ping-manipulation approach
were inspired by [PistonMOTD](https://github.com/AlexProgrammerDE/PistonMOTD) (Apache-2.0), an
open-source multi-platform MOTD plugin by AlexProgrammerDE. This project's implementation is a
from-scratch rewrite for Velocity only, using SnakeYAML and MiniMessage directly rather than
PistonMOTD's own config/serialization stack.
