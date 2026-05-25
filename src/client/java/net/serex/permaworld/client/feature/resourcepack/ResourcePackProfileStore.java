package net.serex.permaworld.client.feature.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.serex.permaworld.Permaworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ResourcePackProfileStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "permaworld-resource-pack-profiles.json";

    private final Path path;
    private Data data = new Data();

    public ResourcePackProfileStore() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        load();
    }

    public List<Profile> profiles() {
        return data.profiles;
    }

    public String activeProfileName() {
        return data.activeProfileName == null ? "" : data.activeProfileName;
    }

    public void setActiveProfileName(String name) {
        data.activeProfileName = name == null ? "" : name;
    }

    public void upsert(String name, List<String> packIds) {
        Profile existing = find(name);
        if (existing != null) {
            existing.packIds = new ArrayList<>(packIds);
            return;
        }
        Profile profile = new Profile();
        profile.name = name;
        profile.packIds = new ArrayList<>(packIds);
        data.profiles.add(profile);
    }

    public void delete(String name) {
        data.profiles.removeIf(profile -> profile.name.equals(name));
        if (activeProfileName().equals(name)) {
            data.activeProfileName = "";
        }
    }

    public Profile find(String name) {
        for (Profile profile : data.profiles) {
            if (profile.name.equals(name)) {
                return profile;
            }
        }
        return null;
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            Permaworld.LOGGER.error("No se pudieron guardar los perfiles de resource packs", e);
        }
    }

    private void load() {
        try {
            if (Files.exists(path)) {
                try (var reader = Files.newBufferedReader(path)) {
                    data = GSON.fromJson(reader, Data.class);
                }
            }
            if (data == null) {
                data = new Data();
            }
            if (data.profiles == null) {
                data.profiles = new ArrayList<>();
            }
            for (Profile profile : data.profiles) {
                if (profile.packIds == null) {
                    profile.packIds = new ArrayList<>();
                }
            }
        } catch (IOException e) {
            Permaworld.LOGGER.error("No se pudieron cargar los perfiles de resource packs", e);
            data = new Data();
        }
    }

    private static final class Data {
        private String activeProfileName = "";
        private List<Profile> profiles = new ArrayList<>();
    }

    public static final class Profile {
        public String name = "";
        public List<String> packIds = new ArrayList<>();
    }
}
