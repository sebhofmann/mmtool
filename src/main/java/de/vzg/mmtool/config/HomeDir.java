package de.vzg.mmtool.config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;

public class HomeDir {

    public static Path getHomeDirPath() {
        final String homeFolder = System.getProperty("user.home");
        final Path homeFolderPath = Paths.get(homeFolder);
        return homeFolderPath.resolve(".mmtool");
    }

    public static Path setUpHomeDir() throws IOException {
        Path path = getHomeDirPath();
        if (!Files.exists(path)) {
            System.out
                .println("Configuration Path " + path.toAbsolutePath().toString() + " does not exist. Create it..");
            path = Files.createDirectories(path);
        }
        return path;
    }

    public static Config getConfig() {
        try {
            final Path configPath = setUpHomeDir().resolve("config.json");

            if (!Files.exists(configPath)) {
                return new Config();
            } else {
                try (InputStreamReader isr = new InputStreamReader(Files.newInputStream(configPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                    return new Gson().fromJson(isr, Config.class);

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
            return null;
        }
    }

    public static void saveConfig(Config config) throws IOException {
        final Path configPath = setUpHomeDir().resolve("config.json");
        try (BufferedWriter osw = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
            new Gson().toJson(config, osw);
        }
    }
}
