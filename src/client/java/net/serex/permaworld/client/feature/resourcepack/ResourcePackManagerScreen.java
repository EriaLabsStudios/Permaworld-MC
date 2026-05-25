package net.serex.permaworld.client.feature.resourcepack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ResourcePackManagerScreen extends Screen {

    private static final int ROWS_PER_PAGE = 7;

    private final Screen parent;
    private final ResourcePackProfileStore profiles = new ResourcePackProfileStore();

    private ResourcePackFileManager fileManager;
    private Mode mode = Mode.PACKS;
    private int installedPage;
    private int archivedPage;
    private int profilePage;
    private int selectedInstalled = -1;
    private int selectedArchived = -1;
    private int selectedProfile = -1;
    private String profileName = "";
    private Component status = Component.empty();

    public ResourcePackManagerScreen(Screen parent) {
        super(Component.translatable("permaworld.resourcepack.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft client = Minecraft.getInstance();
        this.fileManager = new ResourcePackFileManager(client.getResourcePackDirectory());

        int panelWidth = Math.min(420, this.width - 32);
        int x = this.width / 2 - panelWidth / 2;
        int y = 42;

        addTab(x, y, 0, Mode.PACKS, "permaworld.resourcepack.tab.packs");
        addTab(x, y, 1, Mode.ARCHIVED, "permaworld.resourcepack.tab.archived");
        addTab(x, y, 2, Mode.PROFILES, "permaworld.resourcepack.tab.profiles");

        if (mode == Mode.PACKS) {
            initInstalled(x, y + 30, panelWidth);
        } else if (mode == Mode.ARCHIVED) {
            initArchived(x, y + 30, panelWidth);
        } else {
            initProfiles(x, y + 30, panelWidth);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta) {
        extractMenuBackground(extractor);
        extractor.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        if (status != null && !status.getString().isBlank()) {
            extractor.centeredText(this.font, status, this.width / 2, this.height - 42, 0xE0E0E0);
        }
        super.extractRenderState(extractor, mouseX, mouseY, tickDelta);
    }

    @Override
    public void onClose() {
        parent.resize(this.width, this.height);
        this.minecraft.setScreen(parent);
    }

    private void initInstalled(int x, int y, int width) {
        PackRepository repository = this.minecraft.getResourcePackRepository();
        List<ResourcePackFileManager.PackFile> packs = fileManager.listInstalled(repository);
        selectedInstalled = clampSelection(selectedInstalled, packs.size());

        addRows(packs, selectedInstalled, installedPage, x, y, width, (index, pack) -> {
            selectedInstalled = index;
            rebuildWidgets();
        }, true);

        int controlsY = y + ROWS_PER_PAGE * 22 + 8;
        Button archive = Button.builder(Component.translatable("permaworld.resourcepack.archive"), button -> {
            ResourcePackFileManager.PackFile pack = selected(packs, selectedInstalled);
            if (pack != null) {
                confirm("permaworld.resourcepack.archive.confirm.title",
                        "permaworld.resourcepack.archive.confirm.message",
                        () -> archive(pack));
            }
        }).bounds(x, controlsY, 100, 20).build();
        archive.active = selectedInstalled >= 0;
        addRenderableWidget(archive);

        Button delete = Button.builder(Component.translatable("permaworld.resourcepack.delete"), button -> {
            ResourcePackFileManager.PackFile pack = selected(packs, selectedInstalled);
            if (pack != null) {
                confirm("permaworld.resourcepack.delete.confirm.title",
                        "permaworld.resourcepack.delete.confirm.message",
                        () -> delete(pack));
            }
        }).bounds(x + 108, controlsY, 100, 20).build();
        delete.active = selectedInstalled >= 0;
        addRenderableWidget(delete);

        addPaging(x + width - 108, controlsY, packs.size(), installedPage,
                page -> installedPage = page);
    }

    private void initArchived(int x, int y, int width) {
        List<ResourcePackFileManager.PackFile> packs = fileManager.listArchived();
        selectedArchived = clampSelection(selectedArchived, packs.size());

        addRows(packs, selectedArchived, archivedPage, x, y, width, (index, pack) -> {
            selectedArchived = index;
            rebuildWidgets();
        }, false);

        int controlsY = y + ROWS_PER_PAGE * 22 + 8;
        Button restore = Button.builder(Component.translatable("permaworld.resourcepack.restore"), button -> {
            ResourcePackFileManager.PackFile pack = selected(packs, selectedArchived);
            if (pack != null) {
                restore(pack);
            }
        }).bounds(x, controlsY, 100, 20).build();
        restore.active = selectedArchived >= 0;
        addRenderableWidget(restore);

        Button delete = Button.builder(Component.translatable("permaworld.resourcepack.delete"), button -> {
            ResourcePackFileManager.PackFile pack = selected(packs, selectedArchived);
            if (pack != null) {
                confirm("permaworld.resourcepack.delete.confirm.title",
                        "permaworld.resourcepack.delete.confirm.message",
                        () -> delete(pack));
            }
        }).bounds(x + 108, controlsY, 100, 20).build();
        delete.active = selectedArchived >= 0;
        addRenderableWidget(delete);

        addPaging(x + width - 108, controlsY, packs.size(), archivedPage,
                page -> archivedPage = page);
    }

    private void initProfiles(int x, int y, int width) {
        EditBox nameBox = new EditBox(this.font, x, y, width - 108, 20,
                Component.translatable("permaworld.resourcepack.profile.name"));
        nameBox.setHint(Component.translatable("permaworld.resourcepack.profile.name"));
        nameBox.setMaxLength(48);
        nameBox.setValue(profileName);
        nameBox.setResponder(value -> profileName = value);
        addRenderableWidget(nameBox);

        Button save = Button.builder(Component.translatable("permaworld.resourcepack.profile.save"), button -> saveCurrentProfile())
                .bounds(x + width - 100, y, 100, 20)
                .build();
        save.active = !profileName.trim().isEmpty();
        addRenderableWidget(save);

        List<ResourcePackProfileStore.Profile> list = profiles.profiles();
        selectedProfile = clampSelection(selectedProfile, list.size());
        addProfileRows(list, x, y + 28, width);

        int controlsY = y + 28 + ROWS_PER_PAGE * 22 + 8;
        Button apply = Button.builder(Component.translatable("permaworld.resourcepack.profile.apply"), button -> {
            ResourcePackProfileStore.Profile profile = selected(list, selectedProfile);
            if (profile != null) {
                applyProfile(profile);
            }
        }).bounds(x, controlsY, 100, 20).build();
        apply.active = selectedProfile >= 0;
        addRenderableWidget(apply);

        Button delete = Button.builder(Component.translatable("permaworld.resourcepack.profile.delete"), button -> {
            ResourcePackProfileStore.Profile profile = selected(list, selectedProfile);
            if (profile != null) {
                profiles.delete(profile.name);
                profiles.save();
                selectedProfile = -1;
                status = Component.translatable("permaworld.resourcepack.profile.deleted");
                rebuildWidgets();
            }
        }).bounds(x + 108, controlsY, 100, 20).build();
        delete.active = selectedProfile >= 0;
        addRenderableWidget(delete);

        addPaging(x + width - 108, controlsY, list.size(), profilePage,
                page -> profilePage = page);
    }

    private void addRows(List<ResourcePackFileManager.PackFile> packs, int selectedIndex, int page, int x, int y,
                         int width, PackRowPress press, boolean showActive) {
        int start = page * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, packs.size());
        for (int index = start; index < end; index++) {
            int rowIndex = index;
            ResourcePackFileManager.PackFile pack = packs.get(index);
            Component label = packLabel(pack, selectedIndex == index, showActive);
            int rowY = y + (index - start) * 22;
            addRenderableWidget(Button.builder(label, button -> press.onPress(rowIndex, pack))
                    .bounds(x, rowY, width, 20)
                    .build());
        }
        if (packs.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("permaworld.resourcepack.empty"), button -> { })
                    .bounds(x, y, width, 20)
                    .build()).active = false;
        }
    }

    private void addProfileRows(List<ResourcePackProfileStore.Profile> list, int x, int y, int width) {
        int start = profilePage * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, list.size());
        for (int index = start; index < end; index++) {
            int rowIndex = index;
            ResourcePackProfileStore.Profile profile = list.get(index);
            String prefix = selectedProfile == index ? "> " : "";
            Component label = Component.literal(prefix + trim(profile.name, 42) + " (" + profile.packIds.size() + ")");
            int rowY = y + (index - start) * 22;
            addRenderableWidget(Button.builder(label, button -> {
                selectedProfile = rowIndex;
                profileName = profile.name;
                rebuildWidgets();
            }).bounds(x, rowY, width, 20).build());
        }
        if (list.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("permaworld.resourcepack.profile.empty"), button -> { })
                    .bounds(x, y, width, 20)
                    .build()).active = false;
        }
    }

    private Component packLabel(ResourcePackFileManager.PackFile pack, boolean selected, boolean showActive) {
        String prefix = selected ? "> " : "";
        String active = showActive && pack.packId()
                .map(id -> this.minecraft.getResourcePackRepository().getSelectedIds().contains(id))
                .orElse(false) ? "* " : "";
        return Component.literal(prefix + active + trim(pack.name(), 52));
    }

    private void addTab(int x, int y, int index, Mode tab, String key) {
        Button button = Button.builder(Component.translatable(key), ignored -> {
            mode = tab;
            status = Component.empty();
            rebuildWidgets();
        }).bounds(x + index * 104, y, 100, 20).build();
        button.active = mode != tab;
        addRenderableWidget(button);
    }

    private void addPaging(int x, int y, int total, int page, PageSetter setter) {
        int maxPage = Math.max(0, (total - 1) / ROWS_PER_PAGE);
        Button previous = Button.builder(Component.literal("<"), button -> {
            setter.set(Math.max(0, page - 1));
            rebuildWidgets();
        }).bounds(x, y, 48, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), button -> {
            setter.set(Math.min(maxPage, page + 1));
            rebuildWidgets();
        }).bounds(x + 56, y, 48, 20).build();
        next.active = page < maxPage;
        addRenderableWidget(next);
    }

    private void archive(ResourcePackFileManager.PackFile pack) {
        try {
            fileManager.archive(pack);
            sanitizeSelectedPacks();
            selectedInstalled = -1;
            status = Component.translatable("permaworld.resourcepack.archived");
        } catch (IOException e) {
            status = Component.translatable("permaworld.resourcepack.error", e.getMessage());
        }
        this.minecraft.setScreen(this);
    }

    private void restore(ResourcePackFileManager.PackFile pack) {
        try {
            fileManager.restore(pack);
            reloadRepository();
            selectedArchived = -1;
            status = Component.translatable("permaworld.resourcepack.restored");
        } catch (IOException e) {
            status = Component.translatable("permaworld.resourcepack.error", e.getMessage());
        }
        rebuildWidgets();
    }

    private void delete(ResourcePackFileManager.PackFile pack) {
        try {
            fileManager.delete(pack);
            sanitizeSelectedPacks();
            selectedInstalled = -1;
            selectedArchived = -1;
            status = Component.translatable("permaworld.resourcepack.deleted");
        } catch (IOException e) {
            status = Component.translatable("permaworld.resourcepack.error", e.getMessage());
        }
        this.minecraft.setScreen(this);
    }

    private void saveCurrentProfile() {
        String name = profileName.trim();
        if (name.isEmpty()) {
            status = Component.translatable("permaworld.resourcepack.profile.name_required");
            rebuildWidgets();
            return;
        }
        profiles.upsert(name, new ArrayList<>(this.minecraft.getResourcePackRepository().getSelectedIds()));
        profiles.save();
        status = Component.translatable("permaworld.resourcepack.profile.saved");
        rebuildWidgets();
    }

    private void applyProfile(ResourcePackProfileStore.Profile profile) {
        PackRepository repository = this.minecraft.getResourcePackRepository();
        repository.reload();
        List<String> available = profile.packIds.stream()
                .filter(repository::isAvailable)
                .toList();
        repository.setSelected(available);
        this.minecraft.options.updateResourcePacks(repository);
        status = Component.translatable("permaworld.resourcepack.profile.applied");
        rebuildWidgets();
    }

    private void sanitizeSelectedPacks() {
        PackRepository repository = this.minecraft.getResourcePackRepository();
        repository.reload();
        List<String> selected = repository.getSelectedIds().stream()
                .filter(repository::isAvailable)
                .toList();
        repository.setSelected(selected);
        this.minecraft.options.updateResourcePacks(repository);
    }

    private void reloadRepository() {
        this.minecraft.getResourcePackRepository().reload();
    }

    private void confirm(String titleKey, String messageKey, Runnable action) {
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                action.run();
            } else {
                this.minecraft.setScreen(this);
            }
        }, Component.translatable(titleKey), Component.translatable(messageKey)));
    }

    private static int clampSelection(int selected, int size) {
        return selected >= 0 && selected < size ? selected : -1;
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private static <T> T selected(List<T> list, int index) {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private enum Mode {
        PACKS,
        ARCHIVED,
        PROFILES
    }

    @FunctionalInterface
    private interface PackRowPress {
        void onPress(int index, ResourcePackFileManager.PackFile pack);
    }

    @FunctionalInterface
    private interface PageSetter {
        void set(int page);
    }
}
