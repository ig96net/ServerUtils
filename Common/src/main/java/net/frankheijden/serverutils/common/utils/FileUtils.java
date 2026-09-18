package net.frankheijden.serverutils.common.utils;

import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.DigestInputStream;

public class FileUtils {

    private FileUtils() {}

    /**
     * Parses an InputStream into a JsonElement.
     */
    public static JsonElement parseJson(InputStream in) throws IOException {
        if (in == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return com.google.gson.JsonParser.parseReader(reader);
        }
    }

    /**
     * Saves an InputStream to a file.
     */
    public static boolean saveResource(InputStream in, File target) throws IOException {
        if (target.exists()) return false;
        if (in == null) throw new IOException("Resource stream is missing");
        Path targetPath = target.toPath();
        Path parent = targetPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (in) {
            Files.copy(in, targetPath);
            return true;
        }
    }

    /**
     * Get the Hash of a file at given path.
     *
     * @param path The path
     * @return The file's hash
     */
    public static String getHash(Path path) {
        try (InputStream stream = Files.newInputStream(path);
                DigestInputStream digestStream = new DigestInputStream(stream, MessageDigest.getInstance("SHA-256"))) {
            digestStream.transferTo(OutputStream.nullOutputStream());
            return StringUtils.bytesToHex(digestStream.getMessageDigest().digest());
        } catch (IOException | java.security.NoSuchAlgorithmException ex) {
            return null;
        }
    }
}
