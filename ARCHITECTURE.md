## 12th Engine Physics Puzzle Game - Architecture Diagram

### Class Hierarchy

```
BasicEntity (Abstract)
    │
    ├── PushableEntity
    │   └── Physics-enabled movable blocks
    │       - Mass and drag properties
    │       - Gravity affected
    │       - Force-based movement
    │
    ├── TargetEntity
    │   └── Goal objects (non-physics)
    │       - Tracks hit state
    │       - Visual feedback
    │       - Level completion
    │
    ├── BasicPlaneEntity
    │   └── Static ground and obstacles
    │       - Collision surfaces
    │       - Non-moving geometry
    │
    ├── CameraEntity
    │   └── Player view controller
    │       - Position and rotation
    │       - View transform
    │
    └── ModelEntity (OBJ Model based)
        └── Textured 3D models
            - File-based geometry
            - Rendering integration
```

### Game System Architecture

```
┌─────────────────────────────────────────────────────┐
│          TwelfthEngine (Main Entry Point)            │
│  - Window setup and input initialization            │
│  - Game loop management                             │
│  - Rendering pipeline                               │
└─────────────────────────────────────────────────────┘
                      ↓
    ┌─────────────────────────────────────┐
    │    Engine Core Systems              │
    ├─────────────────────────────────────┤
    │ - Renderer3D (OpenGL)               │
    │ - InputManager (WASD, Mouse)        │
    │ - World (Entity container)          │
    │ - TickManager (Physics ticks)       │
    └─────────────────────────────────────┘
                      ↓
    ┌─────────────────────────────────────┐
    │    Game Management Systems          │
    ├─────────────────────────────────────┤
    │ · GameManager                       │
    │   ├─ loadLevel(1|2|3)               │
    │   ├─ getGameState()                 │
    │   └─ onObjectHit()                  │
    │                                     │
    │ · InteractionSystem                 │
    │   ├─ handlePlayerPush()             │
    │   ├─ checkObjectCollisions()        │
    │   └─ applyForces()                  │
    │                                     │
    │ · GameHUD                           │
    │   ├─ updateDisplay()                │
    │   ├─ showInstructions()             │
    │   └─ displayStatus()                │
    └─────────────────────────────────────┘
                      ↓
    ┌─────────────────────────────────────┐
    │    Game Objects (Entities)          │
    ├─────────────────────────────────────┤
    │ · World                             │
    │   ├─ PushableEntity[]               │
    │   ├─ TargetEntity[]                 │
    │   ├─ BasicPlaneEntity[] (ground)    │
    │   ├─ PlayerCameraEntity             │
    │   └─ BasicPlaneEntity[] (walls)     │
    └─────────────────────────────────────┘
```

### Data Flow Diagram

```
User Input (WASD, Mouse)
    │
    ↓
InputManager.update()
    │
    ↓
InteractionSystem.update()
    ├→ handlePlayerPush()     [Apply force to nearby objects]
    └→ checkObjectCollisions() [Detect target hits]
    │
    ↓
World.update(deltaTime)
    │
    ├→ PushableEntity.update() × N [Apply physics]
    │   ├─ Apply gravity
    │   ├─ Update velocity
    │   ├─ Update position
    │   └─ Apply drag
    │
    ├→ TargetEntity.update() × M [Track hit state]
    │
    └→ handleCollisions()     [Resolve physics collisions]
    │
    ↓
GameManager.update()
    └→ Check level completion
    │
    ↓
Renderer3D.render()
    ├→ Render all entities
    ├→ Apply lighting
    └→ Swap buffers
    │
    ↓
GameHUD.update()
    └→ Display game status
    │
    ↓
Window.update()
    └→ Poll events, swap buffers
```

### Level Architecture (Design Pattern)

```
Level = Ground + Objects + Obstacles + Targets

Level 1: Introduction
├── Ground Plane (BasicPlaneEntity)
├── Objects
│   ├── PushableEntity (2, 1, 0) - Block 1
│   └── PushableEntity (2, 1, 0) - Block 2
├── Targets
│   └── TargetEntity (10, 0, 0) - Target 1
└── Obstacles
    └── (none)

Level 2: Multi-Target
├── Ground Plane (BasicPlaneEntity)
├── Objects
│   ├── PushableEntity (-5, 1, 0) - Block 1
│   └── PushableEntity (0, 1, 5) - Block 2
├── Targets
│   ├── TargetEntity (5, 0, 5) - Target 1
│   └── TargetEntity (15, 0, 5) - Target 2
└── Obstacles
    └── PushableEntity (5, 2, 2) - Wall

Level 3: Advanced
├── Ground Plane (BasicPlaneEntity)
├── Objects
│   ├── PushableEntity (0, 1, 0) - Block 1
│   ├── PushableEntity (1, 1, 0) - Block 2
│   ├── PushableEntity (2, 1, 0) - Block 3
│   └── PushableEntity (3, 1, 0) - Block 4
├── Targets
│   └── TargetEntity (20, 0, 10) - Target 1
└── Obstacles
    ├── PushableEntity (5, 3, 5) - Obstacle 1
    └── PushableEntity (12, 5, 8) - Obstacle 2
```

### Physics Pipeline

```
┌─────────────────────────────────────┐
│    Input Force Application          │
│  (InteractionSystem.push())          │
└────────────┬──────────────────────────┘
             │
             ↓
┌─────────────────────────────────────┐
│    RigidBody.applyForce()           │
│  - Accumulates forces               │
│  - Calculates acceleration          │
└────────────┬──────────────────────────┘
             │
             ↓
┌─────────────────────────────────────┐
│    RigidBody.calculateNewVelocity() │
│  - F = ma (Newton's 2nd law)        │
│  - v = v + a*dt                     │
│  - Apply drag                       │
│  - Apply gravity                    │
└────────────┬──────────────────────────┘
             │
             ↓
┌─────────────────────────────────────┐
│    PushableEntity.update()          │
│  - position += velocity * deltaTime │
│  - Clamp ground level               │
│  - Apply friction                   │
└────────────┬──────────────────────────┘
             │
             ↓
┌─────────────────────────────────────┐
│    World.handleCollisions()         │
│  - Detect intersections             │
│  - Resolve collisions               │
│  - Check target hits                │
└─────────────────────────────────────┘
```

### Game State Transitions

```
┌────────────┐
│   START    │
│ Load Lvl 1 │
└─────┬──────┘
      │
      ↓
┌─────────────────────┐
│  PLAYING_LEVEL_X    │
│  - Accept input     │
│  - Update physics   │
│  - Render scene     │
└────────┬──────┬─────┘
         │      │
    [Ret]│      │[Targets Hit]
         │      │
         ↓      ↓
   ┌─────────┐ ┌──────────────┐
   │ RESTART │ │ LEVEL_COMPLETE
   └────┬────┘ └────┬─────────┘
        │           │
        └─────┬─────┘
              │
         [next]│[last level]
              │
              ↓
        ┌──────────┐
        │   END    │
        │  GAME    │
        └──────────┘
```

### Component Interaction Sequence

```
Frame: n
───────────────────────────────────────

1. InputManager.update()
   └─ Reads WASD keys
   └─ Updates camera position

2. InteractionSystem.update()
   ├─ getCameraForwardDirection()
   ├─ Check nearby PushableEntity objects
   ├─ Apply push forces
   └─ Check object-target collisions

3. World.update(deltaTime)
   ├─ Update all entities
   │  ├─ PushableEntity.update()
   │  │  ├─ RigidBody.calculateNewVelocity()
   │  │  ├─ position += velocity * dt
   │  │  └─ Apply friction
   │  ├─ TargetEntity.update()
   │  └─ [Other entities]
   └─ handleCollisions()

4. GameManager.update(deltaTime)
   └─ Check if all targets hit

5. Renderer3D.render()
   ├─ Clear screen
   ├─ Render all entities
   └─ Swap buffers

6. GameHUD.update(deltaTime)
   └─ Update display info
```

### Input → Output Flow

```
User Presses Keys
        │
        ↓
   InputManager
   (Keyboard State)
        │
        ↓
   Camera Position
   & Direction
        │
        ├─────────────────────┐
        │                     │
        ↓                     ↓
   View Matrix          Forward Vector
        │                    │
        ├────────┬───────────┤
        │        │           │
        ↓        ↓           ↓
   Renderer3D  InteractionSystem
   (Rendering)  (Physics)
        │           │
        ├──────┬────┤
        │      │    │
        ↓      ↓    ↓
    Display  Object  Target
    Updates  Movement  Hits
    Game State

Final: 3D Game World Rendered to Screen
```

### Physics Object States

```
PushableEntity State Machine

┌──────────────┐
│   AT_REST    │ (velocity ≈ 0)
│ - No forces  │
│ - Static     │
└────┬─────────┘
     │ apply_force()
     │
     ↓
┌──────────────┐
│   MOVING     │ (velocity > 0)
│ - Gravity on │
│ - Drag acts  │
└────┬─────────┘
     │◄──┐ push_force continue
     │   │
     ├─→ apply_additional_forces()
     │
     │ velocity → 0
     ↓
┌──────────────┐
│   STOPPED    │ (settled)
└──────────────┘
```

---

This architecture provides:
- **Modularity**: Each system is independent
- **Extensibility**: Easy to add new entity types
- **Maintainability**: Clear separation of concerns
- **Scalability**: Level system can support many levels
- **Reusability**: Systems can be used in other games
