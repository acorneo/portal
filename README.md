# PORTAL
### Beyond the Screen
*One world. Multiple devices. Infinite possibilities.*

**Genre:** Atmospheric Puzzle Adventure / Physics-Based Exploration  
**Platforms:** macOS + iOS  
**Technologies:** Kotlin Multiplatform, Compose Multiplatform, Core Motion, Ktor, Procedural Generation, Machine Learning  
**Game Mode:** Single-player, cross-device experience  
**Working Title:** PORTAL

### Run the desktop maze

With a JDK installed, launch the native Compose application from the repository root:

```sh
./gradlew :desktopApp:run
```

Move with **WASD or arrow keys**. Click the maze to restore keyboard focus.
Switching windows pauses the simulation and clears held keys; press a direction
again after returning. The camera follows the sphere smoothly; resizing keeps
the view inside the world, or centers the world when it fits in the viewport.

The sphere reveals a limited area with wall occlusion. Lit geometry is visible
now; faint geometry has been explored before; unknown areas stay concealed.
Exploration persists until the application closes. Walk away and return to
check that remembered corridors remain faint while hidden paths stay dark.
Resize the window and switch away while holding a direction to check camera
bounds and focus behavior.

```sh
./gradlew :shared:jvmTest :desktopApp:test :desktopApp:compileKotlin
./gradlew :shared:compileKotlinIosSimulatorArm64
```

World models, the handmade map, movement, swept circle-wall collision, and the
120 Hz fixed-step clock live in `shared/src/commonMain/kotlin/de/acorn42/portal/world`, without
Compose or platform input dependencies. World distances are measured in tiles;
movement is 3 tiles per second, with normalized diagonal input. Catch-up is
limited to 8 ticks per frame and excess stalled time is discarded. Continuous
collision checks wall faces and rounded corners, then slides along contact
surfaces. Exploration advances with simulation steps, independently of the
camera. Shared visibility uses circular, tile-centered sight with closed
segment/wall intersection tests, including corner and thin-wall occlusion.
Explored geometry is remembered; dynamic entities require an exact visible
point within the radius and a clear sight line.

`view/Camera.kt` contains pure camera bounds, transforms, and analytic smoothing
for stationary or linearly moving targets. `view/MazeCanvas.kt` renders supplied
world and visibility state at 64 dp per tile, with a soft radial light edge and
modest sphere glow. Camera motion follows display frames; only `desktopApp`
owns keyboard events, window focus, and simulation frame timing. The iOS entry
point displays the dedicated phone puzzle, with offline and paired modes.


### Nearby connection POC — peer-to-peer Wi-Fi

This separate **Nearby POC** mode tests native Mac/iPhone discovery, explicit
pairing consent and acknowledged ping/pong. It uses Apple's Network framework
with `includePeerToPeer` on browsing, listening and connections; it enables the
system's peer-to-peer Wi-Fi paths and does **not require a shared router or
hotspot**. The system may choose infrastructure Wi-Fi when available, so a
successful shared-LAN test alone does not prove router-free operation. Actual
router-free discovery/connection and privacy prompts require a physical-device test.

The game/offline phone room and existing manual-address LAN portal remain under
**Game / LAN portal**. Gameplay does not use this POC transport. Mode switches
pause the game transport/motion; returning keeps the remembered game controller.
The earlier reported real-phone LAN connection failure has not been diagnosed
as fixed by this separate POC.

From **this exact worktree**, with full Xcode installed:

```sh
cd /Users/maksimraklo/.ao/data/worktrees/portal/portal-maze
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
./gradlew :desktopApp:prepareNearbyHelper :desktopApp:run --args='--nearby-poc'
```

`run` also prepares the helper automatically. Do not rebuild JVM JARs while a
previous development run remains open: close that window first. For an actual
app bundle and its own macOS privacy identity, build and open the packaged app:

```sh
./gradlew :desktopApp:createDistributable
open desktopApp/build/compose/binaries/main/app/de.acorn42.portal.app --args --nearby-poc
```

Use either development run or packaged app, not both at once. The bundle includes
the native helper at `Contents/app/resources/nearby/PortalNearbyHelper`, so it does
not need Swift/Xcode installed at run time. Local builds are not notarized for
internet distribution. The helper is built for the Mac's current architecture;
this checkout was compiled on Apple Silicon. Native minimums are macOS 13 and
iOS 18.2; the packaged Java runtime also imposes its own OS requirements. Current
validation uses installed macOS 27, Xcode 27.1 and Swift 6.4, not older-OS hardware.

Open the same checkout's iOS project:

```sh
open /Users/maksimraklo/.ao/data/worktrees/portal/portal-maze/iosApp/iosApp.xcodeproj
```

Select **iosApp**, your physical iPhone, and your signing team, then Run. A developer
account/device provisioning is needed to install on hardware. The top picker offers
**Game / LAN portal** and **Nearby POC**. No host address or pairing code is entered
in Nearby POC.

Router-free acceptance test:

1. Leave the Wi-Fi radios **on** on Mac and iPhone, place them nearby, and keep
   both apps foreground. For proof without infrastructure, disconnect both from
   their joined Wi-Fi networks while keeping Wi-Fi enabled; do not enable Personal
   Hotspot or substitute a shared access point. No network settings are changed
   automatically by PORTAL. Airplane mode/Wi-Fi radio off is not this test.
2. On Mac choose **Nearby connection POC**, then **Advertise nearby Mac**. On iPhone
   choose **Nearby POC**; it lists named Macs. Allow Local Network access if asked.
   A permission prompt can pause discovery/advertising: after allowing it, tap
   **Retry discovery** on iPhone or **Advertise nearby Mac** again on Mac.
3. Tap exactly one Mac. Both screens show the peer's name/ephemeral ID and a
   matching fingerprint. Compare them; on Mac choose **Accept iPhone** or **Reject**.
   Other incoming connections are refused while one request/connection is active.
4. After acceptance, both show connected identity. Use **Send ping** on each;
   the sender's **Acknowledged pings** and receiver's **received** counts increase.
   An unmatched/stale pong cannot acknowledge a current ping. No gameplay state
   or LAN pairing credentials cross this transport.
5. **Disconnect**, then **Retry discovery** on iPhone, select again and approve
   again. Repeat with **Reject**. Background either app or leave this mode:
   discovery/connections stop and retry is explicit. On Mac focus loss stops the
   POC; refocus and advertise again. A ping without an acknowledgement times out
   after five seconds; pending pairing times out after 25 seconds.
6. Test denied Local Network access, then enable PORTAL in system Settings >
   Privacy & Security > Local Network and retry explicitly. Keep firewall
   protections enabled and allow the app if prompted. A helper failure requires
   leaving/reopening Nearby POC; helper stdin closure/app exit terminates it.

The simulator cannot prove AWDL/radio behavior or local-network privacy.
`includePeerToPeer` is an API configuration, not a measured hardware success.
If discovery stays empty, verify both radios, foreground state, selected app's
Local Network access, physical proximity and supported Apple Wi-Fi hardware;
report each app's status. Do not disable a firewall or silently fall back to a
router/hotspot and count that as success.

A project-owned local Swift package in `native/nearby` is shared by iOS and a
small Mac helper. The JVM bridge uses bounded JSON over private stdin/stdout;
networking remains native. Wire messages are versioned, length-prefixed and
bounded, with per-connection nonces, session/peer checks, explicit consent and
unique ping acknowledgements. Names/IDs are self-reported. The fingerprint only
correlates this request; **raw TCP is plaintext, without cryptographic device
authentication**. This proximity POC is not a production secure channel and must
not carry sensitive data. Apple selects the actual network path. Bonjour and
usage declarations are included in iOS, Mac app and helper metadata; no global
ATS bypass, multicast entitlement or Bluetooth implementation is added.

Native checks:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcrun swift test --package-path native/nearby
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :desktopApp:test :shared:jvmTest :desktopApp:createDistributable
```

See Apple's [peer-to-peer networking guidance](https://developer.apple.com/documentation/network/nwparameters/includepeertopeer),
[custom protocol sample](https://developer.apple.com/documentation/network/building-a-custom-peer-to-peer-protocol),
[device discovery explanation](https://developer.apple.com/videos/play/wwdc2019/713/)
and [local-network privacy guidance](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy).

### Play the first cross-device portal

This local prototype supports one Mac and one iPhone on the **same trusted Wi-Fi**.
Keep both apps foreground; it does not wake or switch apps. Use a private IPv4
address (`10.x.x.x`, `172.16–31.x.x`, or `192.168.x.x`), not a guest network with
client isolation. Session progress and pairing credentials exist only in memory;
keep both processes running during recovery tests. Restart both to begin a new
session or replace the paired phone.

1. Run `./gradlew :desktopApp:run` from this checkout. Click **Start phone session**.
   The session bar shows the Mac address, port `8765`, and a six-digit code valid
   for five minutes. **New code** refreshes it before pairing. Allow incoming
   connections/local-network access if macOS asks. Do not open the port to the internet.
2. Open `iosApp/iosApp.xcodeproj` in Xcode, select scheme **iosApp**, choose your
   physical iPhone, configure your signing team if necessary, and Run. The existing
   **offline test room** remains the initial screen. Tap **Pair with Mac**, enter
   one displayed Mac Wi-Fi address followed by `:8765` and the code, then **Pair**.
   Allow **Local Network** access. If denied, enable PORTAL in Settings > Privacy
   & Security > Local Network, then retry. No Bonjour discovery is used.
3. Keep the Mac game window focused. Move right from the start with **D / Right**
   to the violet ring at `(4.5, 1.5)`. The Mac sphere disappears while the phone
   prepares and acknowledges the room. Only the active device can move it.
4. Hold the phone fairly flat and tap **Calibrate**. Tilt gently through the existing
   pocket room: pass the first barrier on its right, the second on its left, then
   reach the violet exit near the bottom right. Touch fallback remains available.
   The completed room is acknowledged, the sphere returns to `(5.5, 3.5)` on the Mac,
   and the orange gate above it opens. Explore through it; fog memory is retained.
5. To test recovery, briefly turn off phone Wi-Fi or background either app during
   phone play or a transition. Gameplay pauses once the connection/foreground loss
   is detected (network failure detection takes several seconds). Restore Wi-Fi and
   foreground both apps. The phone reconnects automatically or offers **Reconnect**;
   recalibrate after resuming. Latest local progress, transfer identity and one-time
   unlock survive reconnect; an acknowledged completion cannot duplicate the sphere.
   **Offline mode** disconnects without abandoning the retained paired session.

Native desktop launch does not use a web preview. Simulator runs can use
`127.0.0.1:8765` with touch fallback, but cannot verify Core Motion, physical haptics,
or Apple's local-network permission prompt. Physical two-device playtesting is
required for those behaviors and real Wi-Fi/firewall conditions.

The wire protocol is JSON over Ktor 3.6 WebSockets at `/portal/v1`, version `1`,
with 16 KiB packet/frame limits. The Mac serializes session state, emits revisioned
snapshots, assigns unique outbound/return IDs, and waits for acknowledgements before
changing ownership. Reconnecting rotates the connection generation, invalidating old
inputs and disconnect callbacks. Phone checkpoints carry increasing sequences and
stay in the client outbox until acknowledged; duplicate/stale events cannot reapply
return/unlock. A short-lived code pairs one phone; a random resume token restores it.

This is a cooperative LAN demo using plaintext `ws://`, **not an authenticated,
encrypted transport against a hostile network**. A network observer can read pairing
and resume credentials. The Mac validates session, generation, transfer, room,
sequence, finite values, speed, collision clearance and completion-at-exit. It trusts
the phone's local trajectory for responsive tilt; it does not replay inputs to prove
a puzzle was solved fairly. Clients cannot choose the desktop exit or unlock value.
Disk persistence, anti-cheat and TLS provisioning are outside this milestone.

Apple configuration scopes insecure HTTP exceptions to private IPv4 ranges and
loopback (supported by the project's iOS 18.2 minimum), alongside
`NSLocalNetworkUsageDescription` and `NSAllowsLocalNetworking`. No global ATS bypass,
Bonjour service declaration or multicast entitlement is added. See Apple's
[IP address exception guidance](https://developer.apple.com/documentation/bundleresources/information-property-list/nsapptransportsecurity/nsexceptiondomains)
and [local-network privacy guidance](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy).
Networking follows the official Ktor
[server WebSockets](https://ktor.io/docs/server-websockets.html) and
[client WebSockets](https://ktor.io/docs/client-websockets.html) APIs; iOS uses the
[Darwin engine](https://ktor.io/docs/client-engines.html#darwin).

Combined checks:

```sh
./gradlew :shared:jvmTest :desktopApp:test :desktopApp:compileKotlin :shared:compileKotlinIosSimulatorArm64
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/portal-ios-derived CODE_SIGNING_ALLOWED=NO build
```

---

## 01. The Vision — A World Beyond the Screen

Imagine a game where your computer screen is no longer the boundary of the virtual world.

You are exploring a mysterious maze. Darkness surrounds you. A small glowing sphere illuminates the space nearby, gradually revealing unknown corridors, hidden rooms, and strange mechanisms.

You turn a corner and discover a portal.

You enter it.

The sphere disappears from your MacBook screen.

At that exact moment, your iPhone lights up.

The sphere has entered an entirely different environment — a miniature physical puzzle room controlled not by a keyboard or touchscreen, but by the movement of the phone itself.

You tilt your iPhone to the right, and the sphere begins rolling. You tilt it to the left, and gravity shifts. Carefully, you guide the sphere through narrow passages, navigate obstacles, and reach the exit.

Your phone vibrates. The portal activates.

The sphere returns to your MacBook, appearing in another part of the original maze.

Your journey continues.

**This is PORTAL — an atmospheric puzzle adventure where multiple physical devices become interconnected parts of a single game universe.**

And this universe gradually learns how you explore it.

---

## 02. Core Gameplay — How the Game Works

PORTAL is built around three interconnected gameplay mechanics:

1. **Exploration:** Navigating mysterious mazes with limited visibility.
2. **Cross-Device Puzzles:** Traveling between physical devices, each offering a fundamentally different control system.
3. **Adaptive World:** Exploring a procedurally generated environment that evolves according to your playstyle.

Each mechanic reinforces the others, creating an experience that cannot be replicated by a conventional single-device game.

### The Main World — MacBook

The primary gameplay takes place on the computer.

The player controls a small glowing sphere traveling through a two-dimensional maze.

Movement is intentionally simple: WASD or arrow keys.

However, the maze is almost entirely concealed by darkness.

The player does not know where the exit is, how many paths exist, or what awaits around the next corner.

Only a limited area surrounding the sphere is visible.

This creates a constant sense of exploration, uncertainty, and discovery.

The maze contains:

- Corridors, intersections, and dead ends.
- Hidden passages and secret rooms.
- Doors, switches, and environmental mechanisms.
- Obstacles and environmental puzzles.
- Portals connecting the main world to other physical devices.
- Checkpoints and exits leading to new levels.

The maze should never feel like a meaningless collection of randomly generated corridors.

Every section should serve a purpose: presenting a choice, encouraging exploration, introducing a new mechanic, or surprising the player.

### Fog of War — Exploration Through Uncertainty

Fog of War is one of PORTAL's defining features.

A limited visibility radius surrounds the player. Everything beyond this radius is hidden by darkness.

The system distinguishes three states:

**Visible:** The area currently surrounding the player, fully illuminated.

**Explored:** Previously visited areas. Their geometry remains faintly visible, but dynamic objects outside the current visibility radius are hidden.

**Unexplored:** Completely unknown territory, concealed in darkness.

This encourages players to gradually construct their own mental map of the environment without revealing the entire level.

Lighting should feel soft and atmospheric: a glowing sphere, smooth gradients, subtle particles, reflections along walls, and faint illumination near portals.

Simply moving through the maze should be visually satisfying.

---

## 03. Cross-Device Portals — The Defining Mechanic

Portals are the heart of the game.

Each portal connects two physical devices.

Initially, these devices are a MacBook and an iPhone.

Unlike conventional games, where portals transport characters between virtual locations, PORTAL transfers **the gameplay itself between real devices**.

### Entering a Portal

**Step 1.** The player discovers a portal inside the MacBook maze.

**Step 2.** The sphere enters the portal, triggering a short transition animation on the computer.

**Step 3.** The iPhone receives the transition event and displays the sphere inside its own puzzle room.

**Step 4.** Control automatically switches from the computer keyboard to the physical movement of the phone.

**Step 5.** The player completes the iPhone puzzle and reaches its exit portal.

**Step 6.** The sphere returns to the MacBook. The camera moves to the corresponding exit location, and exploration continues.

The entire transition should feel like one continuous event.

No loading screens, manual application switching, or repeated device pairing.

The central illusion is that **the sphere genuinely travels from one physical screen into another**.

---

## 04. The Physical World — iPhone

On the iPhone, gameplay changes completely.

While the MacBook focuses on exploration, the iPhone becomes a physical puzzle.

A miniature maze appears on the phone, containing walls, narrow passages, obstacles, platforms, and an exit portal.

Traditional movement controls disappear.

Instead, the player controls the sphere by tilting the device.

### Physics-Based Controls

The game uses Apple's Core Motion framework to retrieve information about the phone's orientation and the direction of gravity relative to the device.

These measurements are translated into physical forces affecting the virtual sphere.

As a result:

- Tilting right accelerates the sphere to the right.
- Tilting left changes the direction of acceleration.
- Increasing the tilt angle changes the strength of acceleration.
- The sphere maintains momentum and responds to collisions.
- Haptic feedback makes collisions and important events physically noticeable.

Controls should feel precise enough to allow careful movement while preserving realistic inertia.

### Puzzle Types

**Precision Maze**

A classic maze with narrow passages requiring controlled, careful movement.

**Momentum Puzzle**

A room where the player must build sufficient momentum to overcome an obstacle.

**Gravity Switch**

A puzzle in which rotating the device changes the effective direction of gravity, allowing the sphere to reach previously inaccessible platforms.

**Timed Gates**

A room containing moving barriers and gates that remain open only briefly.

**Pressure Plates**

The player must roll the sphere onto a pressure plate to activate a mechanism in the main MacBook world.

**Portal Routing**

A room containing several possible exits. The exit selected on the iPhone determines where the sphere reappears on the MacBook.

The last two mechanics are particularly important.

Completing a puzzle on the iPhone should influence the larger game world rather than feeling like an unrelated minigame.

---

## 05. The Living Maze — A World That Learns About You

This is PORTAL's central AI-powered feature.

In most games, levels are either manually designed or generated using fixed procedural rules.

In PORTAL, the environment can adapt to the person exploring it.

**While you learn the maze, the maze learns you.**

Behind this experience is the AI Dungeon Director — a system that analyzes player behavior and influences the generation of future, unexplored sections of the world.

### What the AI Observes

The system collects gameplay metrics, including:

- Time spent navigating different sections.
- Number of dead ends encountered.
- Preference for exploration versus finding the shortest path.
- Time required to solve puzzles.
- Number of unsuccessful attempts.
- Precision and consistency of iPhone tilt controls.
- Frequency of choosing alternative routes.
- Success rates across puzzles of varying difficulty.

From these signals, the system develops a lightweight model of the player's behavior.

Some players prefer reaching the exit as quickly as possible.

Others enjoy examining every corridor.

Some particularly enjoy difficult physics-based challenges.

PORTAL can recognize these patterns and use them to shape the experience.

### How the World Adapts

Consider three different players.

**The Explorer**

This player carefully investigates every corridor, frequently backtracks, and actively searches for hidden rooms.

The AI recognizes this behavior and gradually introduces more optional paths, hidden areas, and exploration-based challenges.

**The Solver**

This player is highly skilled at iPhone physics puzzles but shows little interest in ordinary maze intersections.

The AI gradually introduces more demanding physical challenges and reduces the frequency of long, repetitive corridors.

**The Wanderer**

This player frequently encounters dead ends, struggles to navigate, and has difficulty controlling the sphere.

The AI responds by generating clearer maze structures and introducing puzzles with more gradual difficulty progression.

The purpose is not to make the game consistently easy.

**The goal is to maintain an engaging level of challenge for each individual player.**

### The Golden Rule of Adaptation

The AI must never modify areas the player has already seen or explored.

Only future, unexplored sections may be generated or adapted.

This ensures that the world remains internally consistent.

If a player remembers a particular corridor, that corridor will not suddenly disappear because of an algorithmic decision.

Returning to an explored location should always feel reliable.

Adaptation happens naturally, without breaking the player's understanding of the environment.

### Technical Approach to AI

The system does not require a large language model.

A more appropriate solution is a lightweight machine learning component that estimates suitable difficulty and content preferences from gameplay telemetry.

The architecture consists of three modules.

**Player Model**

Processes gameplay metrics and estimates player preferences, skill levels, and performance trends.

**Adaptive Director**

Determines the desired difficulty, maze complexity, number of optional paths, secret-room frequency, and puzzle types for upcoming sections.

**Procedural Generator**

Constructs actual maze geometry according to those parameters.

The generator must also validate that every generated section is solvable, that an exit exists, and that required portals remain accessible.

An initial version could use a simple online-learning model that updates its estimates based on completed challenges.

More sophisticated machine learning can be introduced after sufficient testing data has been collected.

Importantly, the AI must have a measurable influence on the generated experience rather than existing merely as a decorative feature.

---

## 06. Game Progression — The Journey

The game is structured into chapters or zones, each introducing new concepts and progressively combining existing mechanics.

### Chapter I — The Awakening

The player appears in an almost completely dark maze.

They learn how to move, explore, and understand the Fog of War system.

Eventually, they discover the first portal.

A simple physical puzzle appears on the iPhone, introducing motion-based controls.

After completing it, the player returns to the MacBook and discovers that a previously inaccessible path has opened.

### Chapter II — Two Worlds

Multiple portals, locked doors, and interconnected mechanisms are introduced.

Actions performed on the iPhone begin influencing the main maze.

The player realizes that both devices represent different parts of the same environment.

### Chapter III — The Architect

The maze becomes more complex and begins adapting to the player's behavior.

Alternative routes, hidden rooms, and increasingly varied physical puzzles appear.

The AI Dungeon Director becomes a more noticeable part of the experience without explicitly revealing its decisions.

### Chapter IV — Broken Connections

The player encounters portals with multiple possible exits.

Choosing a particular path on the iPhone determines where the journey continues on the MacBook.

Navigation becomes a puzzle involving not just physical movement but the structure of the connected worlds themselves.

### Chapter V — Beyond the Screen

The final zone combines everything the player has learned.

Successfully completing it requires exploration, solving interconnected cross-device puzzles, and choosing the correct sequence of portals.

The finale should feel like a culmination of the entire experience.

---

## 07. Visual Identity — Art Direction and Atmosphere

PORTAL should look and feel like a small but highly polished independent game.

The primary aesthetic is **minimalist science fiction, ambient darkness, and neon lighting**.

### Color Palette

- Near-black and deep navy blue for the environment.
- Cool turquoise for the player and active portals.
- Soft violet for mysterious mechanisms.
- Warm orange for hazards and interactive objects.

The interface should remain minimal.

The game world itself is always the center of attention.

### Visual Effects

The sphere leaves a subtle luminous trail.

Portals pulse gently, casting light onto nearby walls.

Cross-device transitions create particles suggesting a distortion of space.

Dark environments may contain subtle atmospheric effects.

Camera movement should be smooth and deliberate, avoiding sudden, distracting transitions.

### Sound Design

Audio should reinforce the feeling of exploring a mysterious, interconnected environment.

Soft ambient music, distant mechanical sounds, muted collisions, and subtle environmental effects.

Each portal has a distinctive sound.

When the sphere moves to the iPhone, the audio follows the transition: the MacBook becomes quiet while the phone plays the sphere's arrival sound.

iPhone haptic feedback adds a tactile dimension to physical puzzles.

---

## 08. Multiplayer and Future Expansion

Although the first version focuses on a single player using a MacBook and an iPhone, the underlying concept supports significantly broader possibilities.

### Multiple Phones

Several mobile devices can participate in the same virtual environment.

Each becomes a separate physical puzzle room.

The sphere travels between them through interconnected portals.

### Cooperative Puzzle Mode

Multiple players work together to complete challenges.

One player navigates the main world on a computer, while others control physical puzzle rooms on their phones.

Players must coordinate their actions, activate mechanisms, and transfer the sphere between devices.

### Community Levels

A level editor allows players to create their own mazes.

They can place walls, mechanisms, portals, and physical puzzle rooms, then share their creations with others.

### Challenge Mode

A dedicated competitive mode uses fixed procedural seeds, identical challenges, and disabled AI adaptation.

This allows players to compare completion times under consistent conditions.

---

## 09. Technology — Why Kotlin Multiplatform?

Kotlin Multiplatform is not merely a way to distribute the application across operating systems.

**Multiplatform interaction is a fundamental gameplay mechanic.**

PORTAL cannot fully deliver its defining experience on a single device.

### Shared Game Engine

The Kotlin Multiplatform shared codebase contains:

- Game world models.
- Movement and collision handling.
- Core sphere physics.
- Maze representation.
- Fog of War and visibility calculations.
- Game progression and persistent state.
- Procedural generation algorithms.
- AI Dungeon Director.
- Network message models and synchronization protocols.

One implementation of the core game logic can be reused across macOS and iOS.

### Desktop Application

The MacBook acts as the primary gameplay device and session host.

It manages the main maze, authoritative game state, session coordination, and rendering of the larger environment.

### iOS Application

The iPhone displays physical puzzle rooms.

Apple's Core Motion framework provides motion and gravity data, while native iOS APIs handle haptic feedback.

The remaining gameplay logic is reused from the shared Kotlin codebase wherever possible.

### Communication

Devices connect through a local Wi-Fi network.

Real-time events are exchanged using WebSocket connections implemented with Ktor.

The MacBook maintains the authoritative world state.

The iPhone sends player inputs and receives synchronized state updates.

When the player enters a portal, the system transfers the relevant room identifier, sphere position, velocity, and game events.

Tracking the phone's physical position relative to the MacBook is unnecessary: connections between worlds are defined by the game's portal system.

The architecture should support reconnecting devices, restoring interrupted sessions, and persisting player progress.

---

## 10. Development Strategy — From Prototype to Game

The guiding development principle is simple:

**Prove the magic first. Build the complete experience around it.**

### Milestone 1 — The First Maze

Create a macOS application containing a simple two-dimensional maze.

Implement sphere movement, wall collisions, camera tracking, and Fog of War.

**Deliverable:** A working maze-exploration prototype.

### Milestone 2 — The First Portal

Create an iOS application using the shared Kotlin Multiplatform codebase.

Connect both applications over a local network.

Implement the transfer of the sphere from the MacBook to the iPhone and back.

**Deliverable:** The first successful cross-device gameplay transition.

### Milestone 3 — Real Physics

Introduce tilt-based controls, inertia, collisions, physical obstacles, and haptic feedback.

**Deliverable:** A fully playable physics-based puzzle on the iPhone.

### Milestone 4 — The Actual Game

Add multiple room types, interconnected mechanisms, checkpoints, level progression, and procedural maze generation.

**Deliverable:** A short but complete game with a clear beginning, objective, and variety of challenges.

### Milestone 5 — The Living Maze

Implement gameplay telemetry, adaptive generation parameters, and an initial trainable player model.

Validate that adaptation changes future levels without compromising their solvability.

**Deliverable:** A maze that gradually adapts to how the player explores and solves puzzles.

### Milestone 6 — Polish and Presentation

Add lighting effects, atmospheric sound, transition animations, onboarding, and refined UI.

Prepare the open-source repository, technical documentation, and a demonstration video.

**Deliverable:** A polished competition-ready version of PORTAL.

---

## 11. The Demo — The Moment Everything Comes Together

The demonstration begins with a simple shot.

A MacBook and an iPhone sit on a desk.

On the MacBook, a dark maze fills the screen. A small turquoise sphere glows at its center.

The player starts moving through unknown corridors.

The camera follows the sphere as darkness gradually reveals the surrounding environment.

A strange light appears ahead.

A portal.

The sphere approaches and enters it.

The MacBook now shows an empty corridor.

At precisely that moment, the same sphere appears on the iPhone lying nearby.

The player picks up the phone.

Tilts it.

The sphere begins rolling through a miniature physics-based maze.

A few careful movements later, it reaches the exit.

The phone flashes.

The sphere returns to the MacBook.

But now, a previously inaccessible room has opened.

Exploration continues.

The demonstration then reveals the project's second defining feature: two players whose mazes evolved differently based on their individual playstyles.

One world contains demanding physical challenges.

The other contains alternative routes and hidden rooms.

Both were generated by the same underlying system.

The screen fades to black.

A title appears:

# PORTAL

*The world doesn't end at the edge of your screen.*

---

## 12. Why PORTAL Is Different

Most multiplatform applications aim to provide the same experience across different devices.

PORTAL takes the opposite approach.

**Different devices should offer fundamentally different forms of interaction.**

The MacBook provides precise controls and a large environment designed for exploration.

The iPhone becomes a physical game controller and puzzle space, where real-world movement directly influences virtual physics.

Together, they create a unified experience that cannot be fully replicated on a single screen.

Fog of War introduces mystery and discovery.

Procedural generation creates varied environments and encourages replayability.

The AI Dungeon Director allows the world to gradually adapt to the person exploring it.

PORTAL combines three core ideas:

**One World. Multiple Devices. A Maze That Learns.**

It is not simply a game that runs on both MacBook and iPhone.

It is a game where the connection between devices becomes the gameplay itself.

And above all:

**PORTAL transforms screens from the boundaries of virtual worlds into gateways between them.**
