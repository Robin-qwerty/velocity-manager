# robin-network (Velocity + Paper)

Multi-module project that links Paper backend servers through **Redis**:

- **Cross-server chat** — full Adventure components (LuckPerms prefixes preserved), no custom format prefix.
- **Public server messages** — join, quit, death, and advancement messages sync across linked servers.
- **Player-specific messages** — targeted messages can reach a player on another linked backend.
- **Shared tab list** — header/footer shows network-wide online players (Velocity publishes presence).
- **Velocity utilities** — `/find`, `/lobby`, `/hub`, `/discord`, domain routing, plugin-message transfers.
- **Per-domain MOTD** — different server-list description, hover box, player count, fake version, and favicon per hostname, with feature toggles throughout so unused parts cost nothing.

## Modules

| Module | Artifact | Install on |
|--------|----------|------------|
| `common` | `robin-network-common` | (library, not deployed) |
| `velocity-plugin` | `robin-velocity-manager-1.0.0.jar` | Velocity `plugins/` |
| `paper-plugin` | `robin-paper-bridge-1.0.0.jar` | Each Paper backend `plugins/` |

## Build

```bash
mvn package
```

Output jars:

- `velocity-plugin/target/robin-velocity-manager-1.0.0.jar`
- `paper-plugin/target/robin-paper-bridge-1.0.0.jar`

## Setup

1. Run **Redis** (same host or reachable from Velocity + all Paper servers).
2. Install **robin-velocity-manager** on Velocity.
3. Install **robin-paper-bridge** on **every** Paper backend you want linked.
4. Edit each plugin’s `config.yml` (created on first start).
5. Set `server-name` on each Paper server to match the name in `velocity.toml`.
6. Use the same `redis.channel-prefix` on Velocity and all Paper servers.
7. Reload Velocity config with `/velocitymanager reload` or restart both proxy and backends.

### Redis channels (auto, from `channel-prefix`)

- `{prefix}:chat` — chat messages (Paper → all Paper)
- `{prefix}:presence` — join / quit / switch / snapshot (Velocity → Paper)
- `{prefix}:control` — resync requests (Paper → Velocity)

## How Redis is used (no new database)

This plugin uses **Redis pub/sub** — a live message bus, not a database.

- **No new Redis database** is created (you will not see a new DB folder or keyspace fill up).
- Plugins **publish** small JSON messages to channels like `robin-network:chat` and `robin-network:presence`.
- Other servers **subscribe** to those channels and react immediately.
- Messages are **not stored**; if no subscriber is listening, the message is gone (like a radio broadcast).

Verify Redis traffic:

```bash
redis-cli SUBSCRIBE robin-network:chat robin-network:presence robin-network:control
```

You should see JSON messages when players chat or join. If you see nothing, check `redis.enabled`, host/port/password, and that Velocity + Paper use the same `channel-prefix`.

## Tab list and tab-complete (Velocity)

Tab entries and chat name autocomplete are handled on **Velocity** (the proxy sees every online player).

Configure in `plugins/robin-velocity-manager/config.yml`:

```yaml
network-sync:
  tablist:
    enabled: true
    player-line: "&a{player} &7({server})"
  chat-completions: true
```

- **Tab list**: adds players from **other** linked backends into your tab (same-server players are already shown by Paper).
- **Chat tab-complete**: all linked network player names while typing in chat.

Paper `tablist` is disabled by default — leave it off to avoid fighting Velocity.

## Join / quit messages

Join and quit messages are **not** synced across servers, and are **hidden** on linked Paper backends (no “Player joined/left the game” spam network-wide).

## Configuration

### Velocity (`plugins/robin-velocity-manager/config.yml`)

```yaml
redis:
  enabled: true
  host: localhost
  port: 6379
  password: ""
  channel-prefix: robin-network

network-sync:
  publish-presence: true
  linked-servers:
    - lobby
    - bedwars
```

- **`linked-servers`**: Velocity server names (from `velocity.toml`) that share chat and tab list. **Configure this only on Velocity** — Paper backends receive the list automatically via Redis. Leave empty or omit to link every backend.

Velocity publishes when players join, quit, or switch backend servers. Paper backends use this for the shared tab list.

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
  - `<online>` / `<max>` — network-wide online/max count (or this profile's override value)
  - `<online_SERVERNAME>` — live online count on one backend server, e.g. `<online_lobby>`,
    `<online_bedwars>` — generated automatically for every server in `velocity.toml`
  - `<newline>` — splits one YAML string into an extra display line
  - `<center>` — must be the first thing in a line; approximately centers it (pixel-width
    approximation of the vanilla font, not pixel-perfect)
- **Favicons**: PNG files placed in `plugins/robin-velocity-manager/favicons/`, referenced by
  filename. Missing/invalid files just log a warning and are skipped.
- Reload with `/velocitymanager reload` after editing `motd.yml`.

### Paper (`plugins/robin-paper-bridge/config.yml`)

Set **`server-name`** to the matching Velocity server name. The linked-server group is controlled from Velocity (see above).

```yaml
server-name: lobby   # must match velocity.toml

redis:
  enabled: true
  host: localhost
  port: 6379
  password: ""
  channel-prefix: robin-network

network-sync:
  chat:
    enabled: true
  messages:
    public: true
    player-specific: true
  tablist:
    enabled: true
    header: "&6AvesClub Network\n&7Online: {network_count}"
    footer: "&eNetwork players:\n{network_players}"
    player-line: "&a{player} &7({server})"
```

**Tab list placeholders:** `{network_count}`, `{network_players}`, `{server}`  
**Player line placeholders:** `{player}`, `{server}`, `{uuid}`

**Chat:** No `format` option — messages are sent as rendered Adventure components so **LuckPerms prefixes** (and other chat plugins) appear as configured on each backend.

**Messages:**
- `messages.public` — sync join/quit/death/advancement messages
- `messages.player-specific` — deliver targeted messages to players on other linked backends (chat with a limited viewer list, or via API below)

### Send a message to a player on another server (API)

```java
RobinPaperBridgePlugin bridge = RobinPaperBridgePlugin.getInstance();
bridge.sendToNetworkPlayer(targetUuid, Component.text("Hello from another server!"));
```

> Tab sync uses header/footer with a network player list. Native tab entries for players on other servers would need packet-level handling (e.g. TAB plugin); this keeps the implementation lightweight and stable.

## Velocity commands

- `/find <player>` — permission `robinvelocitymanager.find`
- `/lobby`, `/hub` — send to configured lobby server
- `/discord` — clickable Discord link from config
- `/velocitymanager reload` — permission `robinvelocitymanager.reload`

## Backend plugin messaging (existing)

Paper backends can still move players via the `myplugin:network` channel and `CONNECT` subcommand (see below).

## Quick Start for Backend Plugins

**To send a player to another server from a Paper backend plugin:**

1. Register the outgoing channel: `getServer().getMessenger().registerOutgoingPluginChannel(this, "myplugin:network");`
2. Send a plugin message through the player with two UTF strings: `"CONNECT"` and the target server name.
3. The Velocity plugin will handle the transfer automatically.

**Example:**
```java
ByteArrayOutputStream out = new ByteArrayOutputStream();
DataOutputStream data = new DataOutputStream(out);
data.writeUTF("CONNECT");
data.writeUTF("lobby");  // target server name
player.sendPluginMessage(this, "myplugin:network", out.toByteArray());
```

## How backend servers send players around

1. Register the same plugin channel on the Paper backend.
2. Write a payload containing two UTF strings: the subcommand (`CONNECT`) and the target server name.
3. Send the plugin message through the player's connection so Velocity sees it.

Minimal Paper example:

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

## Architecture

```text
Player chat on Paper A
        │
        ▼
  robin-paper-bridge ──publish──► Redis (chat channel)
        ▲                              │
        │                              ▼
  robin-paper-bridge ◄──subscribe── all Paper servers

Player join/switch on Velocity
        │
        ▼
robin-velocity-manager ──publish──► Redis (presence channel)
                                           │
                                           ▼
                              robin-paper-bridge (tab list update)
```

## Notes

- Chat is cancelled locally and re-displayed via Redis so LuckPerms/chat-plugin formatting is preserved network-wide.
- Join/quit/death/advancement messages are suppressed locally and broadcast to linked servers.
- Plugin `/msg` commands that use `player.sendMessage()` directly are not auto-synced unless they use `sendToNetworkPlayer()` or chat with a limited viewer set.
- On startup, each Paper backend requests a full player snapshot from Velocity.
- Redis and SnakeYAML/Lettuce/Gson are bundled in both plugin jars (shaded).

## Credits

The per-domain MOTD feature's config layout, placeholder style, and ping-manipulation approach
were inspired by [PistonMOTD](https://github.com/AlexProgrammerDE/PistonMOTD) (Apache-2.0), an
open-source multi-platform MOTD plugin by AlexProgrammerDE. This project's implementation is a
from-scratch rewrite for Velocity only, using SnakeYAML and MiniMessage directly rather than
PistonMOTD's own config/serialization stack.
