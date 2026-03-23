# 🎮 12th Engine Physics Puzzle Game - Developer Quick Reference

## 📁 Project File Structure

```
12th-Engine/
├── src/main/java/net/twelfthengine/
│   │
│   ├── TwelfthEngine.java ✨ MODIFIED
│   │   └─ Main game entry point + game loop integration
│   │
│   ├── game/ ✨ NEW PACKAGE
│   │   ├── GameManager.java ✨ NEW
│   │   │   ├─ 3 level designs
│   │   │   ├─ Game state management
│   │   │   └─ Level progression
│   │   │
│   │   ├── PushableEntity.java ✨ NEW
│   │   │   ├─ Physics-enabled blocks
│   │   │   ├─ Mass & drag control
│   │   │   └─ Push mechanics
│   │   │
│   │   ├── TargetEntity.java ✨ NEW
│   │   │   ├─ Goal objects
│   │   │   ├─ Hit detection
│   │   │   └─ Level completion
│   │   │
│   │   ├── InteractionSystem.java ✨ NEW
│   │   │   ├─ Player pushing
│   │   │   ├─ Collision detection
│   │   │   └─ Force application
│   │   │
│   │   └── GameHUD.java ✨ NEW
│   │       ├─ Status display
│   │       ├─ Instructions
│   │       └─ HUD updates
│   │
│   ├── core/ (existing)
│   ├── entity/ (existing)
│   ├── physics/ (existing)
│   ├── renderer/ (existing)
│   ├── math/ (existing)
│   ├── window/ (existing)
│   ├── controls/ (existing)
│   └── world/ (existing)
│
├── GAME_README.md ✨ NEW
│   └─ Complete game documentation
│
├── GAME_STRUCTURE.txt ✨ NEW
│   └─ Quick start guide
│
├── CREATION_SUMMARY.md ✨ NEW
│   └─ What was built summary
│
├── ARCHITECTURE.md ✨ NEW
│   └─ System architecture diagrams
│
├── ENHANCEMENT_GUIDE.md ✨ NEW
│   └─ Future development roadmap
│
├── build.gradle.kts (unchanged)
├── settings.gradle.kts (unchanged)
└── gradlew/gradlew.bat (unchanged)
```

## 🔑 Key Classes Reference

### 1. GameManager (156 lines)
**Purpose:** Control game flow and level management

**Key Methods:**
```java
loadLevel(int levelNum)           // Load specific level
loadLevel1/2/3()                  // Level design implementations
update(float deltaTime)           // Update game state
onObjectHit(BasicEntity obj)      // Handle target hits
nextLevel()                       // Progress to next level
restart()                         // Restart current level
isLevelComplete()                 // Check completion
getCurrentLevel()                 // Get current level number
```

**Usage:**
```java
GameManager manager = new GameManager(world);
manager.loadLevel(1);
manager.update(deltaTime);
if (manager.isLevelComplete()) {
    manager.nextLevel();
}
```

### 2. PushableEntity (52 lines)
**Purpose:** Represent interactive physics objects

**Key Methods:**
```java
push(Vec3 direction, float force)   // Apply push force
enableRigidbody()                   // Activate physics
setMass(float mass)                 // Set object mass
setDrag(float drag)                 // Set friction
update(float deltaTime)             // Update physics
```

**Properties:**
```java
width, height, depth               // Object dimensions
originalY                           // Ground level
affectedByGravity = true            // Physics enabled
rigidBodyEnabled                    // Physics activation flag
```

**Usage:**
```java
PushableEntity block = new PushableEntity(0, 1, 0, 2, 1, 2);
block.enableRigidbody();
block.setMass(2.0f);
block.setDrag(0.98f);
world.addEntity(block);
```

### 3. TargetEntity (50 lines)
**Purpose:** Define level objectives/goals

**Key Methods:**
```java
hit()                              // Mark target as hit
isHit()                            // Check hit status
isRecentlyHit()                    // Check recent hit
getRadius()                        // Get target size
getHitScale()                      // Get visual scale
```

**Usage:**
```java
TargetEntity target = new TargetEntity(10, 0, 0, 1.0f);
target.hit();  // Mark as hit
if (target.isHit()) {
    // Handle completion
}
world.addEntity(target);
```

### 4. InteractionSystem (95 lines)
**Purpose:** Handle player interactions with objects

**Key Methods:**
```java
update(float deltaTime)            // Main update loop
handlePlayerPush()                 // Check and push objects
checkObjectCollisions()            // Detect target hits
setPushForce(float force)          // Configure push strength
setPushRange(float range)          // Configure detection range
```

**Usage:**
```java
InteractionSystem interaction = new InteractionSystem(world, camera, gameManager);
interaction.update(deltaTime);
interaction.setPushForce(15.0f);
interaction.setPushRange(8.0f);
```

### 5. GameHUD (50 lines)
**Purpose:** Display game information

**Key Methods:**
```java
update(float deltaTime)            // Update HUD display
getLevelInfo()                     // Get level text
getObjectiveInfo()                 // Get objective text
getStatusInfo()                    // Get status text
displayInstructions()              // Show instructions
```

**Usage:**
```java
GameHUD hud = new GameHUD(gameManager);
hud.displayInstructions();
hud.update(deltaTime);
```

## 🎮 Game Controls Reference

| Key | Action |
|-----|--------|
| **W** | Move camera forward |
| **A** | Strafe left |
| **S** | Move camera backward |
| **D** | Strafe right |
| **Mouse** | Look around (locked) |
| **R** | Restart level |
| **N** | Next level |

## 📊 Physics Constants

```java
// From BasicEntity (inherited)
gravity = 9.18f                    // Gravity acceleration
speed = 1.0f                       // Movement speed

// From RigidBody
mass = 1.0f                        // Default object mass
drag = 0.98f                       // Default friction
restitution = 0.5f                 // Bounciness

// From InteractionSystem
pushForce = 15.0f                  // Push strength
pushRange = 8.0f                   // Interaction distance
```

## 🚀 Quick Development Tasks

### Add New Level
1. In `GameManager.java`:
```java
case 4:
    loadLevel4();
    break;
```

2. Add method:
```java
private void loadLevel4() {
    setupGround();
    
    // Add targets
    TargetEntity target = new TargetEntity(x, y, z, radius);
    world.addEntity(target);
    totalTargets = 1;
    
    // Add objects
    PushableEntity block = new PushableEntity(x, y, z, w, h, d);
    block.enableRigidbody();
    block.setMass(mass);
    world.addEntity(block);
}
```

### Adjust Physics
1. Object mass:
```java
block.setMass(5.0f);  // Heavier = harder to push
```

2. Friction/drag:
```java
block.setDrag(0.95f);  // Lower = more slippery
```

3. Push force:
```java
interactionSystem.setPushForce(20.0f);  // Stronger push
```

### Change Level Difficulty
1. Add more targets
2. Increase object mass
3. Add obstacles (non-moving blocks)
4. Increase distance between objects and targets
5. Place targets at higher elevations

## 🧪 Testing Checklist

Once you run the game, verify:

- [ ] **Level Loading**
  - Game starts with Level 1
  - Can press N to go to Level 2
  - Can press N to go to Level 3

- [ ] **Physics Simulation**
  - Objects fall with gravity
  - Objects respond to push forces
  - Objects slide on ground
  - Velocity decreases over time (drag)

- [ ] **Hit Detection**
  - Targets show as hit when objects collide
  - Level completes when all targets are hit
  - Status updates in console

- [ ] **Player Control**
  - WASD moves camera
  - Mouse looks around
  - Push force pushes nearby objects
  - R restarts level correctly

- [ ] **Game State**
  - Game HUD displays level info
  - Instructions displayed at startup
  - Status messages appear in console
  - Level counter updates correctly

## 📈 Performance Tips

For smoother gameplay:

1. **Limit objects per level** - Keep it under 20 objects
2. **Use appropriate physics** - Only enable where needed
3. **Optimize ranges** - Adjust push range to prevent unnecessary checks
4. **Monitor frame rate** - Use Java profiler if needed

## 🐛 Common Issues & Fixes

**Issue:** Objects fall through ground
- **Fix:** Ensure ground plane is properly positioned at y=-3

**Issue:** Can't push objects
- **Fix:** Check InteractionSystem is initialized and objects are enabled

**Issue:** Targets won't register hits
- **Fix:** Verify TargetEntity objects are in world

**Issue:** Camera can't move
- **Fix:** Check InputManager is initialized

**Issue:** Game doesn't compile
- **Fix:** Use `/` in paths (not `\`), check record syntax in Vec3

## 📚 Documentation Map

| File | Purpose |
|------|---------|
| [GAME_README.md](GAME_README.md) | Full game documentation |
| [GAME_STRUCTURE.txt](GAME_STRUCTURE.txt) | Code structure + quick start |
| [CREATION_SUMMARY.md](CREATION_SUMMARY.md) | What was built |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System diagrams |
| [ENHANCEMENT_GUIDE.md](ENHANCEMENT_GUIDE.md) | Future roadmap |
| This file | Developer reference |

## 🤝 Code Style Guide

### Class Naming
- Game entities: `EntityType + Entity` (e.g., `PushableEntity`)
- Systems: `SystemType + System` (e.g., `InteractionSystem`)
- Managers: `EntityType + Manager` (e.g., `GameManager`)

### Method Naming
- Getters: `get + PropertyName()` 
- Setters: `set + PropertyName()`
- Checkers: `is + Status()`
- Actions: `verb + Noun()`

### Variable Naming
- Constants: `UPPER_CASE`
- Fields: `camelCase`
- Local: `camelCase`
- Booleans: `is/can + Adjective`

## 🎯 Next Steps for Development

1. **Play the game** - Test all 3 levels
2. **Add Level 4** - Practice level creation
3. **Adjust difficulty** - Tweak physics constants
4. **Add new features** - Follow ENHANCEMENT_GUIDE.md
5. **Optimize performance** - Profile and improve

---

**Happy developing! 🚀**

*Remember: The best way to learn is by experimenting. Try modifying values, adding entities, and testing different configurations!*
