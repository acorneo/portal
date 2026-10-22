# PORTAL
### Beyond the Screen
*One world. Multiple devices. Infinite possibilities.*

**Genre:** Atmospheric Puzzle Adventure / Physics-Based Exploration  
**Platforms:** macOS + iOS  
**Technologies:** Kotlin Multiplatform, Compose Multiplatform, Core Motion, Ktor, Procedural Generation, Machine Learning  
**Game Mode:** Single-player, cross-device experience  
**Working Title:** PORTAL

### Run the first desktop maze

With a JDK installed, launch the native Compose application from the repository root:

```sh
./gradlew :desktopApp:run
```

Move with **WASD or arrow keys**. Click the maze to restore keyboard focus.
Switching windows pauses the simulation and clears held keys; press a direction
again after returning. The entire handmade maze is visible in this milestone.

```sh
./gradlew :shared:jvmTest :desktopApp:test :desktopApp:compileKotlin
```

World models, the handmade map, movement, swept circle-wall collision, and the
120 Hz fixed-step clock live in `shared/src/commonMain/.../world`, without
Compose or platform input dependencies. World distances are measured in tiles;
movement is 3 tiles per second, with normalized diagonal input. Catch-up is
limited to 8 ticks per frame and excess stalled time is discarded. Continuous
collision checks wall faces and rounded corners, then slides along contact
surfaces. `App.kt` renders state using Compose Canvas; only `desktopApp` owns
keyboard events, window focus, and the frame loop. The iOS entry point remains
compatible and displays the shared maze without desktop controls.

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
