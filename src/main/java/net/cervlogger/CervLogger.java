package net.cervlogger;

import com.mojang.logging.LogUtils;
import net.cervlogger.command.CommandHandler;
import net.cervlogger.config.ConfigHandler;
import net.cervlogger.database.ConsumerThread;
import net.cervlogger.database.Database;
import net.cervlogger.listener.BlockListener;
import net.cervlogger.listener.EntityListener;
import net.cervlogger.listener.InventoryListener;
import net.cervlogger.listener.PlayerListener;
import net.cervlogger.util.Scheduler;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * CervLogger - block logging, lookup, rollback and restore
 * for NeoForge 1.21.1 servers.
 */
@Mod(CervLogger.MOD_ID)
public final class CervLogger {

    public static final String MOD_ID = "cervlogger";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static CervLogger instance;
    private static MinecraftServer server;

    public CervLogger(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new Scheduler());
        NeoForge.EVENT_BUS.register(new BlockListener());
        NeoForge.EVENT_BUS.register(new PlayerListener());
        NeoForge.EVENT_BUS.register(new EntityListener());
        NeoForge.EVENT_BUS.register(new InventoryListener());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        try {
            ConfigHandler.load(server);
            Database.init(server);
            ConsumerThread.start();
            LOGGER.info("[CervLogger] CervLogger {} enabled. Storage: {}", VERSION, Database.description());
        }
        catch (Exception e) {
            LOGGER.error("[CervLogger] Failed to initialize. Logging is DISABLED.", e);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[CervLogger] Flushing queue ({} entries) and shutting down...", ConsumerThread.size());
        ConsumerThread.shutdown();
        Database.close();
        server = null;
        LOGGER.info("[CervLogger] Disabled.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandHandler.register(event.getDispatcher());
    }

    public static CervLogger getInstance() {
        return instance;
    }

    public static MinecraftServer getServer() {
        return server;
    }
}
