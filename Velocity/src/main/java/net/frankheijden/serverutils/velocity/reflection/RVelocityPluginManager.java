package net.frankheijden.serverutils.velocity.reflection;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import dev.frankheijden.minecraftreflection.ClassObject;
import dev.frankheijden.minecraftreflection.MinecraftReflection;
import dev.frankheijden.minecraftreflection.exceptions.MinecraftReflectionException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class RVelocityPluginManager {

    private static final MinecraftReflection reflection = MinecraftReflection
            .of("com.velocitypowered.proxy.plugin.VelocityPluginManager");

    private RVelocityPluginManager() {}

    /**
     * Retrieves the plugin map. Key is the id of the plugin.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, PluginContainer> getPlugins(PluginManager manager) {
        try {
            Object obj = reflection.get(manager, "pluginsById");
            if (obj instanceof Map) {
                return (Map<String, PluginContainer>) obj;
            }
        } catch (MinecraftReflectionException ignored) {
            // Ignored: field does not exist in this Velocity version
        }

        try {
            Object obj = reflection.get(manager, "plugins");
            if (obj instanceof Map) {
                return (Map<String, PluginContainer>) obj;
            }
        } catch (MinecraftReflectionException ignored) {
            // Ignored: field does not exist or is not a Map in this Velocity version
        }

        return null;
    }

    /**
     * Retrieves the plugin instances map.
     * @param manager The plugin manager.
     * @return The plugin instances map, or null if unavailable.
     */
    public static Map<Object, PluginContainer> getPluginInstances(PluginManager manager) {
        try {
            return reflection.get(manager, "pluginInstances");
        } catch (MinecraftReflectionException ex) {
            return null;
        }
    }

    /**
     * Registers a plugin in Velocity's internal registries.
     * @param manager The plugin manager.
     * @param container The plugin container.
     */
    public static void registerPlugin(PluginManager manager, PluginContainer container) {
        try {
            reflection.invoke(manager, "registerPlugin", ClassObject.of(PluginContainer.class, container));
        } catch (MinecraftReflectionException ex) {
            Map<String, PluginContainer> pluginsMap = getPlugins(manager);
            if (pluginsMap != null) {
                pluginsMap.put(container.getDescription().getId(), container);
            }
            Map<Object, PluginContainer> pluginInstances = getPluginInstances(manager);
            if (pluginInstances != null && container.getInstance().isPresent()) {
                pluginInstances.put(container.getInstance().get(), container);
            }
            getIterablePlugins(manager).ifPresent(c -> c.add(container));
        }
    }

    /**
     * Removes a plugin from every registry used by Velocity, including the internal
     * {@code Set<PluginContainer>} that backs the public {@link PluginManager#getPlugins()},
     * which upstream's unload path leaves stale (the plugin keeps showing up as "loaded").
     */
    public static void unregisterPlugin(
            PluginManager manager,
            String pluginId,
            PluginContainer container,
            Object instance
    ) {
        Map<String, PluginContainer> pluginsMap = getPlugins(manager);
        if (pluginsMap != null) {
            pluginsMap.remove(pluginId);
        }
        Map<Object, PluginContainer> pluginInstances = getPluginInstances(manager);
        if (pluginInstances != null && instance != null) {
            pluginInstances.remove(instance);
        }
        getIterablePlugins(manager).ifPresent(containers -> containers.remove(container));
    }

    @SuppressWarnings("unchecked")
    private static Optional<Collection<PluginContainer>> getIterablePlugins(PluginManager manager) {
        try {
            Object obj = reflection.get(manager, "plugins");
            if (obj instanceof Collection) {
                return Optional.of((Collection<PluginContainer>) obj);
            }
            return Optional.empty();
        } catch (MinecraftReflectionException ex) {
            return Optional.empty();
        }
    }
}
