print("--- Starting World Construction via Lua ---")

-- 1. Create the Ground Plane
-- Java: BasicPlaneEntity(x, y, z, width, depth)
local ground = BasicPlaneEntity.new(0, -3, 0, 50, 50)
world:addEntity(ground)

-- 2. Setup the Sunlight
local sun = LightEntity.new(28, 42, 18)
sun:setRotation(-48, -32, 0)
sun:setColor(1, 0.96, 0.88)
sun:setIntensity(1.2)
sun:setShadowOrthoHalfSize(60)
sun:setCastShadows(true)
world:addEntity(sun)

-- 3. Load the Encrypted Bomb Model
-- We use the static methods from ResourceExtractor we exposed
local twaBytes = ResourceExtractor:readBytes("/models/6ovcmof8fc56.twa")
local assetArchive = TwelfthPackage.new(twaBytes, "6ovcmof8fc56.twa")

local twmBytes = assetArchive:getFileData("6ovcmof8fc56.twm")
local modelArchive = TwelfthPackage.new(twmBytes, "6ovcmof8fc56.twm")

local twmBytes2 = assetArchive:getFileData("sphere.twm")
local modelArchive2 = TwelfthPackage.new(twmBytes2, "sphere.twm")

-- Create the ModelEntity
-- Java: ModelEntity(x, y, z, objPath, archive)
bomb = ModelEntity.new(-6, 600, 0, "6ovcmof8fc56.obj", modelArchive)
bomb:setSize(1.0)
bomb:enableRigidbody()
bomb:setMass(1.0)
bomb:setDrag(0.999)
bomb:setGravity(19.62)
bomb:setCollisionShape(CollisionShape.AABB) -- Assuming 1 maps to AABB in your Enum
bomb:setPushable(true)
bomb:setCollidable(true)

world:addEntity(bomb)

sphere = ModelEntity.new(-6, 6, 0, "sphere.obj", modelArchive2)
sphere:setSize(0.5)
sphere:enableRigidbody()
sphere:setCollisionShape(CollisionShape.AABB)
sphere:setPushable(true)
sphere:setCollidable(true)

world:addEntity(sphere)

-- 4. Tree Sprite
local tree = TextureEntity.new(-10, 2, 0, "/models/tree/DB2X2_L01.png", 2.0, 4.0)
world:addEntity(tree)

print("--- Lua World Setup Complete ---")

-- Keep your timer logic from before
local moveTimer = 0
function onTick(dt)
    moveTimer = moveTimer + dt
    if moveTimer >= 6.0 then
        if bomb ~= nil then
            local p = bomb:getPosition()
            bomb:setPosition(Vec3.new(p:x() + 0.5, p:y(), p:z()))
        end
        moveTimer = 0
    end
end