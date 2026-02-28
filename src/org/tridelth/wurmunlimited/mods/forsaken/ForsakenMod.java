package org.tridelth.wurmunlimited.mods.forsaken;


import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ForsakenMod implements WurmServerMod, Initable, PreInitable, Configurable, ServerPollListener, ServerStartedListener, PlayerLoginListener {
    // Forcing build
    private static final Logger logger = Logger.getLogger(ForsakenMod.class.getName());

    static {
        logger.info("Forsaken Mod loading...");
    }

    private static int hookedCount = 0;
    private static boolean hooksApplied = false;
    private static boolean serverStartedHandled = false;

    @Override
    public void configure(java.util.Properties properties) {
        try {
            if (ForsakenConfig.debug) logger.info("[" + ForsakenConfig.MOD_VERSION + "] configure() called with " + properties.size() + " properties.");
            ForsakenConfig.load(properties);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error in configure(): " + t.getMessage(), t);
        }
    }

    @Override
    public void preInit() {
        applyHooks();
    }

    @Override
    public void init() {
        applyHooks();
    }

    private synchronized void applyHooks() {
        if (hooksApplied) {
            return;
        }
    
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();
            if (classPool == null) {
                logger.severe("CRITICAL: ClassPool is NULL!");
                return;
            }
        
            // Ensure our own classes are in the pool
            try {
                classPool.appendClassPath(new javassist.ClassClassPath(this.getClass()));
            } catch (Throwable t) {
            }

            ForsakenConfig.load();
        
            // Core death hooks
            hookMethods(classPool, "com.wurmonline.server.creatures.Creature", "die", true);
            hookMethods(classPool, "com.wurmonline.server.players.Player", "die", true);
        
            // Creatures manager death hooks
            hookMethods(classPool, "com.wurmonline.server.creatures.Creatures", "setCreatureDead", true);
        
            // AI/Movement Enhancement Hooks
            hookMethods(classPool, "com.wurmonline.server.creatures.Creature", "getAggressivity", false);
            hookMethods(classPool, "com.wurmonline.server.creatures.Creature", "isAggHuman", false);
            hookMethods(classPool, "com.wurmonline.server.creatures.Creature", "setTarget", false);
            hookMethods(classPool, "com.wurmonline.server.creatures.Creature", "setOpponent", false);
            hookMethods(classPool, "com.wurmonline.server.creatures.Creature", "getMaxHuntDistance", false);

            // Npc overrides some of these or calls them in ways that need specific hooks
            hookMethods(classPool, "com.wurmonline.server.creatures.Npc", "isAggHuman", false);
            hookMethods(classPool, "com.wurmonline.server.creatures.Npc", "getMaxHuntDistance", false);

            // Intercept player chat to handle /forsaken commands
            try {
                CtClass ctCommunicator = classPool.get("com.wurmonline.server.creatures.Communicator");
                ctCommunicator.getDeclaredMethod("reallyHandle").instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("reallyHandle_CMD_MESSAGE")) {
                            m.replace("java.nio.ByteBuffer tempBuffer = $1.duplicate();" +
                                    "if(!org.tridelth.wurmunlimited.mods.forsaken.ForsakenManager.handleForsakenCommand($1, this.player)) {" +
                                    "  $_ = $proceed(tempBuffer);" +
                                    "}");
                        }
                    }
                });
                if (ForsakenConfig.debug) logger.info("SUCCESS: Hooked Communicator.reallyHandle for /forsaken commands");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could not hook Communicator for /forsaken commands: " + t.getMessage());
            }

            // Hook for Npc movement crash
            try {
                CtClass ctNpc = classPool.get("com.wurmonline.server.creatures.Npc");
                ctNpc.getDeclaredMethod("getMoveTarget").instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("nextInt")) {
                            // If random.nextInt(0) is about to be called, it means there are no move targets.
                            // Return null from getMoveTarget() to avoid ArrayIndexOutOfBoundsException.
                            m.replace("if ($1 > 0) { $_ = $proceed($1); } else { return null; }");
                        }
                    }
                });
                if (ForsakenConfig.debug) logger.info("SUCCESS: Hooked Npc.getMoveTarget for safety");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could not hook Npc.getMoveTarget: " + t.getMessage());
            }

            // Hook for colors removed as per user request

            try {
                CtClass ctCreatureStatus = classPool.get("com.wurmonline.server.creatures.CreatureStatus");
                ctCreatureStatus.getDeclaredMethod("getSizeMod").insertAfter("$_ = org.tridelth.wurmunlimited.mods.forsaken.ForsakenManager.getForsakenSizeMod(this, $_);");
                if (ForsakenConfig.debug) logger.info("SUCCESS: Hooked CreatureStatus.getSizeMod for visual sizing");
                
                try {
                    CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
                    ctCreature.getDeclaredMethod("getBaseCombatRating").insertAfter("$_ = org.tridelth.wurmunlimited.mods.forsaken.ForsakenManager.getForsakenBCRMod(this, $_);");
                    if (ForsakenConfig.debug) logger.info("SUCCESS: Hooked Creature.getBaseCombatRating for combat power");
                    
                    ctCreature.getDeclaredMethod("getArmourMod").insertAfter("$_ = org.tridelth.wurmunlimited.mods.forsaken.ForsakenManager.getForsakenArmourMod(this, $_);");
                    if (ForsakenConfig.debug) logger.info("SUCCESS: Hooked Creature.getArmourMod for natural armour");
                    
                    ctCreature.getDeclaredMethod("getSpeed").insertAfter("$_ = org.tridelth.wurmunlimited.mods.forsaken.ForsakenManager.getForsakenSpeedMod(this, $_);");
                    if (ForsakenConfig.debug) logger.info("SUCCESS: Hooked Creature.getSpeed for movement speed");
                    
                    try {
                        CtClass ctItems = classPool.get("com.wurmonline.server.Items");
                        ctItems.getDeclaredMethod("destroyItem", new CtClass[]{CtClass.longType}).insertBefore(
                            "if (org.tridelth.wurmunlimited.mods.forsaken.ForsakenManager.shouldCancelItemDestruction($1)) {" +
                            "  return;" +
                            "}" +
                            "if (org.tridelth.wurmunlimited.mods.forsaken.ForsakenConfig.debug && org.tridelth.wurmunlimited.mods.forsaken.ForsakenManager.isDebugItem($1)) {" +
                            "  java.util.logging.Logger.getLogger(\"ForsakenMod\").info(\"FORSAKEN_DEBUG: Item \" + $1 + \" is being destroyed by \" + org.tridelth.wurmunlimited.mods.forsaken.ForsakenManager.getShortStackTrace());" +
                            "}"
                        );
                        if (ForsakenConfig.debug) logger.info("SUCCESS: Hooked Items.destroyItem for diagnostics and safeguards");
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Could not hook Items.destroyItem: " + t.getMessage());
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Could not hook Creature stats: " + t.getMessage());
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could not hook CreatureStatus for sizing: " + t.getMessage());
            }

            hooksApplied = true;
            if (ForsakenConfig.debug) logger.info("applyHooks() completed. Total hooks: " + hookedCount);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "CRITICAL ERROR IN applyHooks: " + t.getMessage(), t);
        }
    }

    private void hookMethods(ClassPool classPool, String className, String methodName, boolean checkSlayer) {
        try {
            CtClass ctClass;
            try {
                ctClass = classPool.get(className);
            } catch (NotFoundException e) {
                return;
            }
        
            if (ctClass.isFrozen()) {
                ctClass.defrost();
            }
        
            CtMethod[] declaredMethods = ctClass.getDeclaredMethods();
            List matchingMethods = new ArrayList();
            for (int i = 0; i < declaredMethods.length; i++) {
                if (declaredMethods[i].getName().equals(methodName)) {
                    // Skip abstract methods
                    if ((declaredMethods[i].getModifiers() & javassist.Modifier.ABSTRACT) != 0) {
                        continue;
                    }
                    matchingMethods.add(declaredMethods[i]);
                }
            }
        
            if (matchingMethods.isEmpty() && !className.contains("Db")) {
                // Check public methods if no declared ones found
                CtMethod[] allMethods = ctClass.getMethods();
                for (int i = 0; i < allMethods.length; i++) {
                    if (allMethods[i].getName().equals(methodName)) {
                        // Only hook if it's declared in this class or we really need to
                        if (allMethods[i].getDeclaringClass() == ctClass) {
                            if ((allMethods[i].getModifiers() & javassist.Modifier.ABSTRACT) == 0) {
                                matchingMethods.add(allMethods[i]);
                            }
                        }
                    }
                }
            }
        
            if (matchingMethods.isEmpty()) {
                return;
            }
        
            CtClass ctCreature = null;
            try {
                ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
            } catch (Throwable t) {}
        
            String managerClass = "org.tridelth.wurmunlimited.mods.forsaken.ForsakenManager";
        
            for (int j = 0; j < matchingMethods.size(); j++) {
                CtMethod method = (CtMethod) matchingMethods.get(j);
                String sig = method.getSignature();
            
                StringBuilder code = new StringBuilder();
                code.append("{ ");
                code.append("  try { ");
            
                if (methodName.equals("die")) {
                    CtClass[] params = method.getParameterTypes();
                    int slayerIdx = -1;
                    if (checkSlayer && ctCreature != null) {
                        for (int i = 0; i < params.length; i++) {
                            if (params[i].subtypeOf(ctCreature)) {
                                slayerIdx = i + 1;
                                break;
                            }
                        }
                    }
                    if (slayerIdx != -1) {
                        code.append("    " + managerClass + ".checkForsaken(this, $" + slayerIdx + "); ");
                    } else if (className.contains("Status")) {
                        code.append("    com.wurmonline.server.creatures.Creature cret = (com.wurmonline.server.creatures.Creature) org.gotti.wurmunlimited.modloader.ReflectionUtil.getPrivateField(this, org.gotti.wurmunlimited.modloader.ReflectionUtil.getField(this.getClass(), \"creature\")); ");
                        code.append("    if (cret != null) " + managerClass + ".checkForsaken(cret); ");
                    } else {
                        code.append("    " + managerClass + ".checkForsaken(this); ");
                    }
                } else if (methodName.equals("setDead")) {
                    code.append("    com.wurmonline.server.creatures.Creature cret = (com.wurmonline.server.creatures.Creature) org.gotti.wurmunlimited.modloader.ReflectionUtil.getPrivateField(this, org.gotti.wurmunlimited.modloader.ReflectionUtil.getField(this.getClass(), \"creature\")); ");
                    code.append("    if (cret != null) " + managerClass + ".checkForsaken(cret); ");
                } else if (methodName.equals("setCreatureDead")) {
                    code.append("    " + managerClass + ".checkForsaken($1); ");
                } else if (methodName.equals("setKillEffects")) {
                    code.append("    " + managerClass + ".checkForsaken($2, $1); ");
                } else if (methodName.equals("addKill")) {
                    code.append("    long kid = ((Long)org.gotti.wurmunlimited.modloader.ReflectionUtil.getPrivateField(this, org.gotti.wurmunlimited.modloader.ReflectionUtil.getField(this.getClass(), \"wurmid\"))).longValue(); ");
                    code.append("    " + managerClass + ".checkForsaken($1, kid); ");
                } else if (methodName.equals("getAggressivity")) {
                    code.append("    if (" + managerClass + ".isForsaken(this)) return 100; ");
                } else if (methodName.equals("isAggHuman")) {
                    code.append("    if (" + managerClass + ".isForsaken(this)) return true; ");
                } else if (methodName.equals("getMaxHuntDistance")) {
                    code.append("    if (" + managerClass + ".isForsaken(this)) { ");
                    code.append("      int mhd = org.tridelth.wurmunlimited.mods.forsaken.ForsakenConfig.maxHuntDistance; ");
                    code.append("      return mhd > 0 ? mhd : 1000; ");
                    code.append("    } ");
                } else if (methodName.equals("setTarget")) {
                    code.append("    if (" + managerClass + ".isForsaken(this)) { ");
                    code.append("      com.wurmonline.server.players.Player p = com.wurmonline.server.Players.getInstance().getPlayerOrNull($1); ");
                    code.append("      if (p != null && p.getPower() > 1) return; ");
                    code.append("    } ");
                } else if (methodName.equals("setOpponent")) {
                    code.append("    if (" + managerClass + ".isForsaken(this)) { ");
                    code.append("      if ($1 instanceof com.wurmonline.server.players.Player && ((com.wurmonline.server.players.Player)$1).getPower() > 1) return; ");
                    code.append("    } ");
                }
            
                code.append("  } catch (Throwable t) { ");
                code.append("    java.util.logging.Logger.getLogger(\"").append(ForsakenMod.class.getName()).append("\").log(java.util.logging.Level.SEVERE, \"Error in hook\", t); ");
                code.append("  } ");
                code.append("}");
            
                try {
                    if (method.getDeclaringClass().isFrozen()) {
                        method.getDeclaringClass().defrost();
                    }
                    method.insertBefore(code.toString());
                    if (methodName.equals("die")) {
                        method.insertAfter(managerClass + ".handleSkillRetention(this);");
                    }
                    hookedCount++;
                } catch (Throwable t) {
                }
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error in hookMethods: " + t.getMessage(), t);
        }
    }

    @Override
    public void onServerPoll() {
        ForsakenManager.poll();
    }

    @Override
    public void onServerStarted() {
        if (serverStartedHandled) {
            return;
        }
        if (ForsakenConfig.debug) logger.info("[" + ForsakenConfig.MOD_VERSION + "] ForsakenMod.onServerStarted() - Finalizing initialization.");
        try {
            ForsakenConfig.load();
            ForsakenDatabase.init();
            ForsakenManager.load();
            serverStartedHandled = true;
            logger.info("[" + ForsakenConfig.MOD_VERSION + "] Forsaken Mod is now active. Hooks: " + hookedCount);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[" + ForsakenConfig.MOD_VERSION + "] Error in onServerStarted(): " + e.getMessage(), e);
        }
    }

    @Override
    public void onPlayerLogin(com.wurmonline.server.players.Player player) {
        try {
            ForsakenManager.sendForsakenVisuals(player);
        } catch (Throwable t) {
            logger.warning("Error sending visuals on login for " + player.getName() + ": " + t.getMessage());
        }
    }

    @Override
    public void onPlayerLogout(com.wurmonline.server.players.Player player) {
        try {
            ForsakenManager.onPlayerLogout(player);
        } catch (Throwable t) {}
    }
}