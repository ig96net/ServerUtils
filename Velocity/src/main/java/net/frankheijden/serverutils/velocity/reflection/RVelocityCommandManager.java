package net.frankheijden.serverutils.velocity.reflection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.frankheijden.minecraftreflection.MinecraftReflection;
import dev.frankheijden.minecraftreflection.Reflection;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.frankheijden.serverutils.common.utils.ReflectionUtils;
import net.frankheijden.serverutils.velocity.ServerUtils;

public class RVelocityCommandManager {

    private static final MinecraftReflection reflection = MinecraftReflection
            .of("com.velocitypowered.proxy.command.VelocityCommandManager");

    private RVelocityCommandManager() {}

    public static CommandDispatcher<CommandSource> getDispatcher(CommandManager manager) {
        return reflection.get(manager, "dispatcher");
    }

    /**
     * Retrieves all command aliases belonging to the given plugin from Velocity's CommandManager.
     * Checks VelocityCommandManager#commandMetas and Brigadier dispatcher root children.
     *
     * @param commandManager The command manager.
     * @param container The plugin container.
     * @param pluginInstance The plugin instance.
     * @return Set of command aliases.
     */
    public static Set<String> getCommandsForPlugin(
            CommandManager commandManager,
            PluginContainer container,
            Object pluginInstance
    ) {
        Set<String> aliases = new LinkedHashSet<>();
        ClassLoader pluginLoader = pluginInstance != null ? pluginInstance.getClass().getClassLoader() : null;

        Map<String, CommandMeta> commandMetas = null;
        try {
            commandMetas = reflection.get(commandManager, "commandMetas");
        } catch (Exception ignored) {
            // Field not found or accessible
        }

        if (commandMetas != null) {
            for (Map.Entry<String, CommandMeta> entry : commandMetas.entrySet()) {
                String alias = entry.getKey();
                CommandMeta meta = entry.getValue();
                if (meta != null && meta.getPlugin() != null) {
                    Object p = meta.getPlugin();
                    if (p == pluginInstance || p == container
                            || (pluginLoader != null && p.getClass().getClassLoader() == pluginLoader)) {
                        aliases.add(alias);
                        aliases.addAll(meta.getAliases());
                    }
                }
            }
        }

        CommandDispatcher<CommandSource> dispatcher = getDispatcher(commandManager);
        if (dispatcher != null && dispatcher.getRoot() != null) {
            for (CommandNode<CommandSource> node : dispatcher.getRoot().getChildren()) {
                String nodeName = node.getName();
                if (isPluginNode(node, pluginInstance, container, pluginLoader)) {
                    aliases.add(nodeName);
                    if (commandMetas != null) {
                        CommandMeta meta = commandMetas.get(nodeName);
                        if (meta != null) {
                            aliases.addAll(meta.getAliases());
                        }
                    }
                }
            }
        }

        return aliases;
    }

    private static boolean isPluginNode(
            CommandNode<CommandSource> node,
            Object pluginInstance,
            PluginContainer container,
            ClassLoader pluginLoader
    ) {
        if (pluginLoader == null) {
            return false;
        }

        com.mojang.brigadier.Command<CommandSource> command = node.getCommand();
        if (command != null && matchesPlugin(command, pluginInstance, container, pluginLoader)) {
            return true;
        }

        Predicate<CommandSource> req = node.getRequirement();
        if (req != null && matchesPlugin(req, pluginInstance, container, pluginLoader)) {
            return true;
        }

        if (node.getRedirect() != null && node.getRedirect() != node) {
            if (isPluginNode(node.getRedirect(), pluginInstance, container, pluginLoader)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matchesPlugin(
            Object target,
            Object pluginInstance,
            PluginContainer container,
            ClassLoader pluginLoader
    ) {
        if (target == null) {
            return false;
        }
        if (target == pluginInstance || target == container) {
            return true;
        }
        if (target.getClass().getClassLoader() == pluginLoader) {
            return true;
        }

        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (pluginLoader != null && field.getType().getClassLoader() == pluginLoader) {
                    return true;
                }
                try {
                    field.setAccessible(true);
                    Object val = field.get(target);
                    if (val != null) {
                        if (val == pluginInstance || val == container) {
                            return true;
                        }
                        if (pluginLoader != null && val.getClass().getClassLoader() == pluginLoader) {
                            return true;
                        }
                    }
                } catch (Throwable ignored) {
                    // Inaccessible field
                }
            }
            current = current.getSuperclass();
        }

        return false;
    }

    /**
     * Proxies the registrars.
     */
    @SuppressWarnings({"rawtypes", "removal"})
    public static void proxyRegistrars(
            ProxyServer proxy,
            ClassLoader loader,
            BiConsumer<PluginContainer, CommandMeta> registrationConsumer
    ) {
        List<Object> proxiedRegistrars = new ArrayList<>();

        Class<?> commandRegistrarClass;
        try {
            commandRegistrarClass = Class.forName("com.velocitypowered.proxy.command.registrar.CommandRegistrar");
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            return;
        }

        for (Object registrar : (List) reflection.get(proxy.getCommandManager(), "registrars")) {
            proxiedRegistrars.add(Proxy.newProxyInstance(
                    loader,
                    new Class[]{ commandRegistrarClass },
                    new CommandRegistrarInvocationHandler(
                            proxy,
                            registrar,
                            registrationConsumer
                    )
            ));
        }

        Field registrarsField = Reflection.getAccessibleField(reflection.getClazz(), "registrars");
        ReflectionUtils.doPrivilegedWithUnsafe(unsafe -> {
            long offset = unsafe.objectFieldOffset(registrarsField);
            unsafe.putObject(proxy.getCommandManager(), offset, proxiedRegistrars);
        });
    }

    public static final class CommandRegistrarInvocationHandler implements InvocationHandler {

        private final ProxyServer proxy;
        private final Object commandRegistrar;
        private final BiConsumer<PluginContainer, CommandMeta> registrationConsumer;

        /**
         * Constructs  a new {@link CommandRegistrarInvocationHandler}.
         */
        public CommandRegistrarInvocationHandler(
                ProxyServer proxy,
                Object commandRegistrar,
                BiConsumer<PluginContainer, CommandMeta> registrationConsumer
        ) {
            this.proxy = proxy;
            this.commandRegistrar = commandRegistrar;
            this.registrationConsumer = registrationConsumer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object obj = method.invoke(commandRegistrar, args);
            if (method.getName().equals("register")) {
                handleRegisterMethod((CommandMeta) args[0]);
            }
            return obj;
        }

        private void handleRegisterMethod(CommandMeta commandMeta) {
            if (commandMeta == null) {
                return;
            }

            if (commandMeta.getPlugin() != null) {
                Object pluginObj = commandMeta.getPlugin();
                if (pluginObj instanceof PluginContainer) {
                    registrationConsumer.accept((PluginContainer) pluginObj, commandMeta);
                    return;
                }
                Optional<PluginContainer> fromInst = proxy.getPluginManager().fromInstance(pluginObj);
                if (fromInst.isPresent()) {
                    registrationConsumer.accept(fromInst.get(), commandMeta);
                    return;
                }
            }

            StackTraceElement[] elements = Thread.currentThread().getStackTrace();

            // Skip the first four elements, which is our overhead here
            for (int i = 4; i < elements.length; i++) {
                String className = elements[i].getClassName();
                if (className.startsWith("java.") || className.startsWith("jdk.")
                        || className.startsWith("com.velocitypowered.")
                        || className.startsWith("cloud.commandframework.")
                        || className.startsWith("net.frankheijden.serverutils.velocity.reflection.")) {
                    continue;
                }

                if (className.startsWith("net.frankheijden.serverutils.")) {
                    PluginContainer serverUtilsContainer = ServerUtils.getInstance() != null
                            ? ServerUtils.getInstance().getPluginContainer() : null;
                    if (serverUtilsContainer == null) {
                        serverUtilsContainer = proxy.getPluginManager().getPlugin("serverutils").orElse(null);
                    }
                    if (serverUtilsContainer != null) {
                        registrationConsumer.accept(serverUtilsContainer, commandMeta);
                        return;
                    }
                }

                for (PluginContainer container : proxy.getPluginManager().getPlugins()) {
                    Optional<?> instanceOpt = container.getInstance();
                    if (!instanceOpt.isPresent()) {
                        continue;
                    }
                    ClassLoader classLoader = instanceOpt.get().getClass().getClassLoader();
                    try {
                        Class<?> clazz = Class.forName(className, false, classLoader);
                        if (clazz.getClassLoader() == classLoader) {
                            registrationConsumer.accept(container, commandMeta);
                            return;
                        }
                    } catch (Throwable ignored) {
                        // Class not in this plugin's loader
                    }
                }
            }

            ServerUtils.getInstance().getLogger().warn(
                    "Couldn't find the registering plugin for the following aliases: {}",
                    commandMeta.getAliases()
            );
        }
    }
}
