package net.frankheijden.serverutils.velocity.entities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.frankheijden.serverutils.common.config.ServerUtilsConfig;
import net.frankheijden.serverutils.common.providers.ResourceProvider;
import net.frankheijden.serverutils.velocity.ServerUtils;

public class VelocityResourceProvider implements ResourceProvider {

    private final ServerUtils plugin;

    public VelocityResourceProvider(ServerUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public InputStream getRawResource(String resource) {
        return plugin.getClass().getClassLoader().getResourceAsStream(resource);
    }

    @Override
    public ServerUtilsConfig load(InputStream is) {
        Path tmpFile = null;
        try (is) {
            tmpFile = Files.createTempFile("serverutils-", ".toml");
            Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            return new VelocityTomlConfig(tmpFile.toFile());
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException ex) {
                    plugin.getLogger().warn("Unable to delete temporary configuration file {}", tmpFile, ex);
                }
            }
        }
        return null;
    }

    @Override
    public ServerUtilsConfig load(File file) {
        return new VelocityTomlConfig(file);
    }

    @Override
    public String getResourceExtension() {
        return ".toml";
    }
}
