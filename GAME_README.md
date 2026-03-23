# 🎮 12th Engine - Physics Puzzle Game

A cool 3D physics-based puzzle game built with the **12th Engine**, featuring interactive gameplay, dynamic physics, and multiple challenge levels.

## Game Overview

**12th Engine Physics Puzzle Game** is a first-person puzzle game where you navigate a 3D world and solve challenges by pushing physics-enabled blocks to hit target spheres. Each level increases in difficulty with new obstacles and puzzle configurations.

## How to Play

### Controls
- **WASD** - Move camera forward/backward and strafe left/right
- **Mouse** - Look around (mouse is locked to window)
- **R** - Restart the current level
- **N** - Advance to next level (only when level is complete)

### Objective
Your goal is to push all blocks into the target spheres. Each target is a glowing sphere that needs to be hit by moving the physics objects in the level.

**Tips:**
- Objects have physics - use momentum and gravity to your advantage
- Objects will slide across the ground and fall down due to gravity
- Push objects from behind for better control
- Some levels require careful positioning and timing

## Game Levels

### Level 1: Introduction
- **Objective:** Hit 1 target
- **Difficulty:** Easy
- **Description:** A simple introductory level with 2 movable blocks and 1 target. Learn how physics objects work!

### Level 2: Multi-Target Puzzle
- **Objective:** Hit 2 targets
- **Difficulty:** Medium
- **Description:** Two targets and multiple obstacles. You'll need to plan your moves carefully!

### Level 3: Advanced Challenge
- **Objective:** Hit the top target
- **Difficulty:** Hard
- **Description:** Multiple blocks at different heights with complex obstacles. This one requires skill!

## Engine Features Used

This game showcases the powerful features of the **12th Engine**:

- **3D Graphics Rendering** - OpenGL-based 3D rendering with anti-aliasing and multisampling
- **Physics Engine** - Realistic physics simulation with gravity, mass, drag, and forces
- **Entity System** - Flexible entity-based architecture for game objects
- **Input Management** - Real-time keyboard and mouse input handling
- **World Management** - 3D spatial world representation with collision detection
- **Camera System** - Smooth first-person camera with player control

## Building and Running

### Prerequisites
- Java 17+
- Gradle
- LWJGL 3.4.1 runtime natives

### Build
```bash
./gradlew build
```

### Run
```bash
./gradlew run
```

The game automatically starts with Level 1.

## Game Architecture

### Core Game Systems

#### GameManager
- Manages level loading and progression
- Tracks game state (current level, completion status)
- Handles level completion detection
- Manages object-target collision callbacks

#### PushableEntity
- Represents physics-enabled blocks in the world
- Extends BasicEntity with physics properties
- Supports mass, drag, and force application
- Automatically applies friction and gravity

#### TargetEntity
- Goal objects that need to be hit to complete levels
- Tracks hit state with visual feedback timer
- Non-physical objects that don't fall

#### InteractionSystem
- Handles player-to-object interaction
- Implements proximity-based pushing mechanics
- Detects collisions between objects and targets
- Manages push force and range

#### GameHUD
- Displays game information and status
- Shows level number, objectives, and completion state
- Logs game instructions for the player

## Game Flow

```
[Startup] 
   ↓
[Load Level 1 with GameManager] 
   ↓
[Main Game Loop]
   ├─ Update world physics
   ├─ Update game systems
   ├─ Check collision/hit detection
   ├─ Render 3D scene
   └─ Handle input for level progression
   ↓
[Level Complete?]
   ├─ Yes → Press N to load next level
   └─ No → Continue Level
```

## Technical Details

### Physics Simulation
- Gravity: 9.81 m/s²
- RigidBody system for realistic motion
- Force-based interaction (impulses and continuous forces)
- Drag/friction for realistic deceleration

### Rendering
- OpenGL 4.3 compatible backend
- Lighting enabled with single light source
- Depth testing for proper 3D occlusion
- Anti-aliasing and multisampling options

### Entity Management
- All game objects inherit from BasicEntity
- Tick-based update system for consistent physics
- Spatial world organization via IAB (Implicit Axis-Based) structure
- Collision shape support (SPHERE, AABB, CAPSULE)

## Future Enhancements

Potential features to expand the game:

- [ ] More levels with varied mechanics
- [ ] Power-ups and special objects
- [ ] Time limits and score tracking
- [ ] Multiplayer challenges
- [ ] Custom level editor
- [ ] Advanced physics (ragdoll, joints, constraints)
- [ ] Water and lava hazards
- [ ] Moving platforms and portals
- [ ] Advanced particle effects
- [ ] Sound effects and background music

## Credits

- **Engine:** 12th Engine - A custom Java/OpenGL 3D game engine
- **Physics:** Custom RigidBody and collision system
- **Rendering:** LWJGL 3 with OpenGL
- **Math:** Custom Vec3, Mat4 libraries

---

**Enjoy the game! 🚀**

*Created with the 12th Engine - Making 3D game development simple and fun!*
