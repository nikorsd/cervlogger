package net.cervlogger.util;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Minimal tick-based task scheduler, replacing the BukkitScheduler usage of the
 * original plugin for delayed main-thread tasks (e.g. reading sign text after edit).
 */
public final class Scheduler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final class Task {
        long runAtTick;
        final Runnable runnable;

        Task(long runAtTick, Runnable runnable) {
            this.runAtTick = runAtTick;
            this.runnable = runnable;
        }
    }

    private static final ConcurrentLinkedQueue<Task> TASKS = new ConcurrentLinkedQueue<>();
    private static volatile long currentTick = 0;

    public static void schedule(int delayTicks, Runnable runnable) {
        TASKS.add(new Task(currentTick + Math.max(1, delayTicks), runnable));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        currentTick++;
        Iterator<Task> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.runAtTick <= currentTick) {
                iterator.remove();
                try {
                    task.runnable.run();
                }
                catch (Exception e) {
                    LOGGER.error("[CervLogger] Scheduled task failed", e);
                }
            }
        }
    }
}
