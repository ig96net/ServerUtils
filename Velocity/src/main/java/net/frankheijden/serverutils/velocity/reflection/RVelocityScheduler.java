package net.frankheijden.serverutils.velocity.reflection;

import com.google.common.collect.Multimap;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import dev.frankheijden.minecraftreflection.MinecraftReflection;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RVelocityScheduler {

    private static final MinecraftReflection reflection = MinecraftReflection
            .of("com.velocitypowered.proxy.scheduler.VelocityScheduler");

    private RVelocityScheduler() {}

    /**
     * Retrieves tasks mapped by plugin instance.
     * @param scheduler The scheduler.
     * @return Multimap of tasks by plugin, or null if unavailable.
     */
    public static Multimap<Object, ScheduledTask> getTasksByPlugin(Scheduler scheduler) {
        try {
            return reflection.get(scheduler, "tasksByPlugin");
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Cancels all scheduled tasks associated with the given plugin container or instance.
     * @param scheduler The scheduler.
     * @param container The plugin container.
     * @param pluginInstance The plugin instance.
     */
    public static void cancelTasks(Scheduler scheduler, PluginContainer container, Object pluginInstance) {
        Multimap<Object, ScheduledTask> tasks = getTasksByPlugin(scheduler);
        if (tasks == null) {
            return;
        }

        ClassLoader pluginLoader = pluginInstance != null ? pluginInstance.getClass().getClassLoader() : null;
        List<Object> keysToRemove = new ArrayList<>();

        for (Object key : tasks.keySet()) {
            if (key == pluginInstance || key == container) {
                keysToRemove.add(key);
            } else if (pluginLoader != null && key != null && key.getClass().getClassLoader() == pluginLoader) {
                keysToRemove.add(key);
            }
        }

        for (Object key : keysToRemove) {
            for (ScheduledTask task : tasks.removeAll(key)) {
                try {
                    task.cancel();
                } catch (Exception ignored) {
                    // Task already cancelled or stopped
                }
            }
        }

        // Also check any remaining tasks whose task object or internal fields belong to this plugin
        List<Map.Entry<Object, ScheduledTask>> remainingToCancel = new ArrayList<>();
        for (Map.Entry<Object, ScheduledTask> entry : tasks.entries()) {
            ScheduledTask task = entry.getValue();
            if (task == null) {
                continue;
            }
            try {
                if (pluginLoader != null && task.getClass().getClassLoader() == pluginLoader) {
                    remainingToCancel.add(entry);
                } else {
                    for (Field f : task.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        Object val = f.get(task);
                        if (val != null && (val == pluginInstance || val == container
                                || (pluginLoader != null && val.getClass().getClassLoader() == pluginLoader))) {
                            remainingToCancel.add(entry);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Inaccessible field or security check
            }
        }
        for (Map.Entry<Object, ScheduledTask> entry : remainingToCancel) {
            tasks.remove(entry.getKey(), entry.getValue());
            try {
                entry.getValue().cancel();
            } catch (Exception ignored) {
                // Ignore failure on cancel
            }
        }
    }
}
