package net.serex.permaworld.client.feature.resourcepack;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class ResourcePackFileManager {

    private static final String ARCHIVE_FOLDER = ".permaworld-archive";

    private final Path packDirectory;
    private final Path archiveDirectory;

    public ResourcePackFileManager(Path packDirectory) {
        this.packDirectory = packDirectory.toAbsolutePath().normalize();
        this.archiveDirectory = this.packDirectory.resolve(ARCHIVE_FOLDER).normalize();
    }

    public List<PackFile> listInstalled(PackRepository repository) {
        return listFiles(packDirectory).stream()
                .filter(path -> !path.equals(archiveDirectory))
                .map(path -> new PackFile(path, matchPackId(repository, path)))
                .sorted(Comparator.comparing(pack -> pack.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public List<PackFile> listArchived() {
        return listFiles(archiveDirectory).stream()
                .map(path -> new PackFile(path, Optional.empty()))
                .sorted(Comparator.comparing(pack -> pack.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public void archive(PackFile pack) throws IOException {
        Files.createDirectories(archiveDirectory);
        move(safeChild(pack.path(), packDirectory), uniqueTarget(archiveDirectory, pack.path().getFileName().toString()));
    }

    public void restore(PackFile pack) throws IOException {
        move(safeChild(pack.path(), archiveDirectory), uniqueTarget(packDirectory, pack.path().getFileName().toString()));
    }

    public void delete(PackFile pack) throws IOException {
        Path source = safeChild(pack.path(), pack.path().startsWith(archiveDirectory) ? archiveDirectory : packDirectory);
        if (Files.isDirectory(source)) {
            try (Stream<Path> walk = Files.walk(source)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
            return;
        }
        Files.deleteIfExists(source);
    }

    private List<Path> listFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(this::isResourcePack)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private boolean isResourcePack(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.isRegularFile(path)) {
            return name.endsWith(".zip");
        }
        return Files.isDirectory(path) && Files.isRegularFile(path.resolve("pack.mcmeta"));
    }

    private Optional<String> matchPackId(PackRepository repository, Path path) {
        String fileName = path.getFileName().toString();
        for (Pack pack : repository.getAvailablePacks()) {
            String id = pack.getId();
            if (id.equals(fileName) || id.equals("file/" + fileName)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    private void move(Path source, Path target) throws IOException {
        Files.move(source.toAbsolutePath().normalize(), target.toAbsolutePath().normalize());
    }

    private Path uniqueTarget(Path directory, String fileName) {
        Path target = directory.resolve(fileName).normalize();
        if (!Files.exists(target)) {
            return target;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        int index = 2;
        do {
            target = directory.resolve(base + "-" + index + extension).normalize();
            index++;
        } while (Files.exists(target));
        return target;
    }

    private Path safeChild(Path path, Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot) || normalizedPath.equals(normalizedRoot)) {
            throw new IOException("Ruta fuera del directorio permitido: " + normalizedPath);
        }
        return normalizedPath;
    }

    public record PackFile(Path path, Optional<String> packId) {
        public String name() {
            return path.getFileName().toString();
        }
    }
}
