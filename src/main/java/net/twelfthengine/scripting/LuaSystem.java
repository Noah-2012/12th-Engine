package net.twelfthengine.scripting;

import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.world.World;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * High-performance Lua Bridge for TwelfthEngine. Handles Java-Lua coercion and global object
 * binding.
 */
public class LuaSystem {
  private final Globals globals;

  public LuaSystem(World world) {
    this.globals = JsePlatform.standardGlobals();

    // 1. Bind the world instance so Lua can do world:addEntity(...)
    bind("world", world);

    // 2. Expose Class Types so Lua can call constructors (.new)
    globals.set("Vec3", CoerceJavaToLua.coerce(net.twelfthengine.math.Vec3.class));
    globals.set("ModelEntity", CoerceJavaToLua.coerce(net.twelfthengine.entity.ModelEntity.class));
    globals.set(
        "LightEntity", CoerceJavaToLua.coerce(net.twelfthengine.entity.world.LightEntity.class));
    globals.set(
        "BasicPlaneEntity",
        CoerceJavaToLua.coerce(net.twelfthengine.entity.world.BasicPlaneEntity.class));
    globals.set(
        "TextureEntity",
        CoerceJavaToLua.coerce(net.twelfthengine.entity.world.TextureEntity.class));
    globals.set("CollisionShape", CoerceJavaToLua.coerce(BasicEntity.CollisionShape.class));

    // Since your bomb uses TwelfthPackage, we should expose that too if you want to keep the
    // archive loading
    globals.set(
        "TwelfthPackage",
        CoerceJavaToLua.coerce(net.twelfthengine.core.resources.TwelfthPackage.class));
    globals.set(
        "ResourceExtractor",
        CoerceJavaToLua.coerce(net.twelfthengine.core.resources.ResourceExtractor.class));
  }

  /** Binds a Java object to a global variable name in Lua. */
  public void bind(String name, Object obj) {
    if (obj == null) {
      globals.set(name, LuaValue.NIL);
    } else {
      globals.set(name, CoerceJavaToLua.coerce(obj));
    }
  }

  /**
   * Executes a Lua script from the resources folder.
   *
   * @param internalPath e.g., "scripts/init.lua"
   */
  public void runScript(String internalPath) {
    try {
      LuaValue chunk = globals.loadfile(internalPath);
      chunk.call();
    } catch (Exception e) {
      System.err.println("[LuaSystem] Error loading script: " + internalPath);
      e.printStackTrace();
    }
  }

  /** Calls a global Lua function (like onTick) with optional arguments. */
  public void call(String functionName, Object... args) {
    LuaValue func = globals.get(functionName);

    if (!func.isnil() && func.isfunction()) {
      LuaValue[] luaArgs = new LuaValue[args.length];
      for (int i = 0; i < args.length; i++) {
        // We coerce every Java argument to a LuaValue
        luaArgs[i] = CoerceJavaToLua.coerce(args[i]);
      }

      try {
        func.invoke(luaArgs);
      } catch (Exception e) {
        System.err.println("[LuaSystem] Runtime error in function: " + functionName);
        e.printStackTrace();
      }
    }
  }

  /** Optional: Access the raw globals if you need custom Luaj manipulation. */
  public Globals getGlobals() {
    return globals;
  }
}
