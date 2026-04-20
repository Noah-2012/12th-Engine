package net.twelfthengine.scripting;

import net.twelfthengine.core.logger.Logger;
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

  private static final String TAG = "LuaSystem";
  private final Globals globals;

  public LuaSystem(World world) {
    Logger.info(TAG, "Initializing Lua scripting environment...");
    this.globals = JsePlatform.standardGlobals();

    // Redirect Lua's print() to our engine's Logger
    globals.STDOUT =
        new java.io.PrintStream(
            new java.io.OutputStream() {
              private StringBuilder buffer = new StringBuilder();

              @Override
              public void write(int b) {
                if (b == '\n') {
                  Logger.info("LUA", buffer.toString());
                  buffer.setLength(0);
                } else {
                  buffer.append((char) b);
                }
              }
            });

    bind("world", world);

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
    globals.set(
        "TwelfthPackage",
        CoerceJavaToLua.coerce(net.twelfthengine.core.resources.TwelfthPackage.class));
    globals.set(
        "ResourceExtractor",
        CoerceJavaToLua.coerce(net.twelfthengine.core.resources.ResourceExtractor.class));

    Logger.info(TAG, "Lua environment ready.");
  }

  /**
   * Executes a raw string of Lua code. Useful for console commands or dynamic snippets.
   *
   * @param code The Lua code to execute.
   */
  public void run(String code) {
    if (code == null || code.trim().isEmpty()) return;

    try {
      // globals.load(code) compiles the string into an executable chunk
      LuaValue chunk = globals.load(code);
      chunk.call();
      Logger.debug(TAG, "Executed Lua snippet.");
    } catch (Exception e) {
      Logger.error(TAG, "Error executing Lua string: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public void bind(String name, Object obj) {
    if (obj == null) {
      globals.set(name, LuaValue.NIL);
    } else {
      globals.set(name, CoerceJavaToLua.coerce(obj));
    }
  }

  public void runScript(String internalPath) {
    try {
      LuaValue chunk = globals.loadfile(internalPath);
      if (chunk == null || chunk.isnil()) return;
      chunk.call();
    } catch (Exception e) {
      Logger.error(TAG, "Error executing script '" + internalPath + "': " + e.getMessage());
    }
  }

  public void call(String functionName, Object... args) {
    LuaValue func = globals.get(functionName);
    if (func.isnil() || !func.isfunction()) return;

    LuaValue[] luaArgs = new LuaValue[args.length];
    for (int i = 0; i < args.length; i++) {
      luaArgs[i] = CoerceJavaToLua.coerce(args[i]);
    }

    try {
      func.invoke(luaArgs);
    } catch (Exception e) {
      Logger.error(TAG, "Runtime error in Lua function '" + functionName + "': " + e.getMessage());
    }
  }

  public Globals getGlobals() {
    return globals;
  }
}
