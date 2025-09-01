## ExcellentCrates (Better Fork)

This fork focuses on stability, Folia compatibility, performance, and better cross‑server behavior while staying drop‑in compatible with upstream configs and data.

### Highlights of this fork
- **Folia-safe scheduling**: audited and fixed scheduling for Folia; added utilities for world/region tasks.
- **Remove ProtocolLib dependency**: rendering now relies solely on **PacketEvents** for holograms/packets.
- **Anti‑dupe safeguards**: switched to bounded LRU caches with database fallback and configurable knobs.
- **Cross‑server key delivery**: Redis-backed flow for physical keys and improved cross‑server lookups; fixed O(n) hotspot in Redis sync.
- **NightCore 2.7.18**: updated core library and aligned APIs.
- **Command quality-of-life**: `/crates key giveall` now accepts an optional world filter and silent feedback flag.

### Quick usage changes
- New giveall usage:
  ```
  /crates key giveall <key> [amount] [-s] [-sf] [world]
  ```
  - If `world` is provided, only online players in that world receive keys.
  - `-s` silences player notifications; `-sf` silences command feedback.

### Compatibility
- **Java**: 21+
- **Server**: Paper / Purpur (1.21.4+)
- **Folia**: Supported

### Dependencies
- **Required**: NightCore 2.7.18+
- **Optional**: PlaceholderAPI, EconomyBridge
- **Packets**: PacketEvents (runtime on server; no ProtocolLib needed)

### Build
- Requires JDK 21.
- Build with Gradle Wrapper:
  ```bash
  ./gradlew build
  ```
  The shaded plugin jar is produced under `build/libs/`.

### Notable fork changes (summary)
- Folia: add helpers and fix scheduling across features.
- Packet layer: rely on PacketEvents; drop ProtocolLib integration.
- Performance: fix O(n) behavior in Redis sync; adopt bounded caches.
- Cross‑server: physical key delivery via Redis; improved lookups; on‑demand queries.
- Commands: optional world param for `giveall`.
- Versions: bumped plugin to `v6.4.9`; aligned dependencies.

### License and credits
- Licensed under the same terms as upstream (see `LICENSE`).
- All credit to NightExpress for the original ExcellentCrates. This fork is community‑maintained and not affiliated with the original author.

### Links
- Upstream documentation: https://nightexpressdev.com/excellentcrates/
- PacketEvents: https://spigotmc.org/resources/80279/
