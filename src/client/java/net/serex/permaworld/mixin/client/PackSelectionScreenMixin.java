package net.serex.permaworld.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.serex.permaworld.client.feature.resourcepack.ResourcePackFileManager;
import net.serex.permaworld.client.feature.resourcepack.ResourcePackProfileStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;
import java.util.List;

@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen {

    @Shadow
    private TransferableSelectionList availablePackList;

    @Shadow
    private TransferableSelectionList selectedPackList;

    @Shadow
    private EditBox search;

    @Shadow
    private PackSelectionModel model;

    @Shadow
    private Path packDir;

    @Unique
    private final ResourcePackProfileStore permaworld$profiles = new ResourcePackProfileStore();

    @Unique
    private ResourcePackFileManager permaworld$fileManager;

    @Unique
    private EditBox permaworld$profileNameBox;

    @Unique
    private String permaworld$profileDraft = "";

    @Unique
    private String permaworld$selectedProfile = "";

    @Unique
    private boolean permaworld$profileDropdownOpen;

    @Unique
    private String permaworld$activeProfile = "";

    @Unique
    private boolean permaworld$customProfile = true;

    @Unique
    private String permaworld$draggingPackId = "";

    @Unique
    private Component permaworld$draggingPackTitle = Component.empty();

    @Unique
    private int permaworld$dragSourceIndex = -1;

    @Unique
    private DragList permaworld$dragSourceList = DragList.NONE;

    @Unique
    private DragList permaworld$dragTargetList = DragList.NONE;

    @Unique
    private int permaworld$dragTargetIndex = -1;

    @Unique
    private int permaworld$dragMouseX;

    @Unique
    private int permaworld$dragMouseY;

    @Unique
    private DragList permaworld$dropFlashList = DragList.NONE;

    @Unique
    private int permaworld$dropFlashIndex = -1;

    @Unique
    private int permaworld$dropFlashTicks;

    @Unique
    private Component permaworld$status = Component.empty();

    protected PackSelectionScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void permaworld$addProfileControls(CallbackInfo ci) {
        permaworld$fileManager = new ResourcePackFileManager(packDir);
        permaworld$restoreActiveProfile();

        int listLeft = availablePackList.getRowLeft();
        int panelX = 24;
        int panelWidth = Math.max(176, Math.min(430, listLeft - 48));
        int y = availablePackList.getY() + 34;
        int buttonWidth = Math.min(86, Math.max(64, panelWidth / 4));
        int inputWidth = panelWidth - buttonWidth - 8;

        permaworld$profileNameBox = new EditBox(this.font, panelX, y, inputWidth, 20,
                Component.translatable("permaworld.resourcepack.profile.name"));
        permaworld$profileNameBox.setHint(Component.translatable("permaworld.resourcepack.profile.name"));
        permaworld$profileNameBox.setMaxLength(48);
        permaworld$profileNameBox.setValue(permaworld$profileDraft);
        permaworld$profileNameBox.setResponder(value -> permaworld$profileDraft = value);
        ((ScreenAccessor) this).permaworld$addRenderableWidget(permaworld$profileNameBox);

        ((ScreenAccessor) this).permaworld$addRenderableWidget(Button.builder(
                Component.translatable("permaworld.resourcepack.profile.save"),
                button -> permaworld$saveProfile()
        ).bounds(panelX + inputWidth + 8, y, buttonWidth, 20).build());

        int comboY = y + 30;
        int applyWidth = Math.min(92, buttonWidth + 14);
        int comboWidth = panelWidth - applyWidth - 8;
        ((ScreenAccessor) this).permaworld$addRenderableWidget(Button.builder(
                permaworld$comboLabel(),
                button -> {
                    permaworld$profileDropdownOpen = !permaworld$profileDropdownOpen;
                    rebuildWidgets();
                }
        ).bounds(panelX, comboY, comboWidth, 20).build());

        Button apply = Button.builder(Component.translatable("permaworld.resourcepack.profile.apply"),
                button -> permaworld$applyProfile()
        ).bounds(panelX + comboWidth + 8, comboY, applyWidth, 20).build();
        apply.active = permaworld$profiles.find(permaworld$selectedProfile) != null;
        ((ScreenAccessor) this).permaworld$addRenderableWidget(apply);

        if (permaworld$profileDropdownOpen) {
            permaworld$addProfileDropdown(panelX, comboY + 22, comboWidth);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta) {
        super.extractRenderState(extractor, mouseX, mouseY, tickDelta);
        if (availablePackList == null || selectedPackList == null) {
            return;
        }

        int panelX = 24;
        int panelWidth = Math.max(176, Math.min(430, availablePackList.getRowLeft() - 48));
        int titleY = permaworld$profileNameBox == null ? Math.max(availablePackList.getY(), 72)
                : Math.max(8, permaworld$profileNameBox.getY() - 34);
        extractor.fill(panelX - 2, titleY - 4, panelX + panelWidth, titleY + 25, 0x88000000);
        extractor.outline(panelX - 2, titleY - 4, panelWidth + 2, 29, 0x55FFFFFF);
        extractor.text(this.font, Component.translatable("permaworld.resourcepack.profile.title"), panelX, titleY, 0xFFFFFFFF);
        extractor.text(this.font, permaworld$activeProfileLabel(), panelX, titleY + 12, 0xFFA0FFAA);
        if (permaworld$status != null && !permaworld$status.getString().isBlank()) {
            int statusY = permaworld$profileNameBox == null ? titleY + 106 : permaworld$profileNameBox.getY() + 58;
            extractor.textWithWordWrap(this.font, permaworld$status, panelX, statusY, panelWidth, 0xFFE0E0E0);
        }
        if (permaworld$isDraggingPack()) {
            extractor.text(this.font, Component.translatable("permaworld.resourcepack.dragging"),
                    selectedPackList.getRowLeft(), selectedPackList.getY() - 12, 0xFFFFFF99);
            permaworld$renderDropTarget(extractor);
            permaworld$renderDraggedPack(extractor);
        } else {
            permaworld$renderDropFlash(extractor);
        }

        permaworld$renderTrashIcons(extractor, mouseX, mouseY, availablePackList);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void permaworld$tickDropFlash(CallbackInfo ci) {
        if (permaworld$dropFlashTicks > 0) {
            permaworld$dropFlashTicks--;
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void permaworld$mouseClickedHead(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (permaworld$handleTrashClick(event)) {
            cir.setReturnValue(true);
            return;
        }
        if (permaworld$preparePackDrag(event)) {
            permaworld$playPickupSound();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void permaworld$mouseClickedReturn(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && permaworld$isInPackLists(event.x(), event.y())) {
            permaworld$markCustomProfile();
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (permaworld$isDraggingPack()) {
            permaworld$dragMouseX = (int) event.x();
            permaworld$dragMouseY = (int) event.y();
            permaworld$updateDropTarget(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (permaworld$isDraggingPack()) {
            permaworld$applyDragDrop();
            permaworld$clearDrag();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Invoker("updateFilteredEntries")
    protected abstract void permaworld$updateFilteredEntries(String search);

    @Unique
    private void permaworld$addProfileDropdown(int x, int y, int width) {
        List<ResourcePackProfileStore.Profile> profiles = permaworld$profiles.profiles();
        int maxRows = Math.min(6, profiles.size());
        for (int i = 0; i < maxRows; i++) {
            ResourcePackProfileStore.Profile profile = profiles.get(i);
            int rowY = y + i * 22;
            ((ScreenAccessor) this).permaworld$addRenderableWidget(Button.builder(
                    Component.literal(permaworld$trim(profile.name, 34)),
                    button -> {
                        permaworld$selectedProfile = profile.name;
                        permaworld$profileDropdownOpen = false;
                        permaworld$status = Component.translatable("permaworld.resourcepack.profile.ready", profile.name);
                        rebuildWidgets();
                    }
            ).bounds(x, rowY, width, 20).build());
        }
        if (profiles.isEmpty()) {
            Button empty = Button.builder(Component.translatable("permaworld.resourcepack.profile.empty"), button -> { })
                    .bounds(x, y, width, 20)
                    .build();
            empty.active = false;
            ((ScreenAccessor) this).permaworld$addRenderableWidget(empty);
        }
    }

    @Unique
    private Component permaworld$comboLabel() {
        if (permaworld$selectedProfile.isBlank()) {
            return Component.translatable("permaworld.resourcepack.profile.select");
        }
        return Component.literal(permaworld$trim(permaworld$selectedProfile, 28));
    }

    @Unique
    private void permaworld$saveProfile() {
        String name = permaworld$profileNameBox == null ? "" : permaworld$profileNameBox.getValue().trim();
        if (name.isEmpty()) {
            permaworld$status = Component.translatable("permaworld.resourcepack.profile.name_required");
            rebuildWidgets();
            return;
        }

        List<String> packIds = selectedPackList.children().stream()
                .map(TransferableSelectionList.Entry::getPackId)
                .toList();
        permaworld$profiles.upsert(name, packIds);
        permaworld$selectedProfile = name;
        permaworld$activeProfile = name;
        permaworld$customProfile = false;
        permaworld$profiles.setActiveProfileName(name);
        permaworld$profiles.save();
        permaworld$profileDropdownOpen = false;
        permaworld$status = Component.translatable("permaworld.resourcepack.profile.saved");
        rebuildWidgets();
    }

    @Unique
    private void permaworld$applyProfile() {
        ResourcePackProfileStore.Profile profile = permaworld$profiles.find(permaworld$selectedProfile);
        if (profile == null) {
            permaworld$status = Component.translatable("permaworld.resourcepack.profile.name_required");
            rebuildWidgets();
            return;
        }

        PackSelectionModelAccessor accessor = (PackSelectionModelAccessor) model;
        PackRepository repository = accessor.permaworld$getRepository();
        repository.reload();

        List<Pack> selected = accessor.permaworld$getSelectedPacks();
        selected.clear();
        for (String packId : profile.packIds) {
            Pack pack = repository.getPack(packId);
            if (pack != null) {
                selected.add(pack);
            }
        }

        List<Pack> unselected = accessor.permaworld$getUnselectedPacks();
        unselected.clear();
        unselected.addAll(repository.getAvailablePacks());
        unselected.removeAll(selected);

        permaworld$status = Component.translatable("permaworld.resourcepack.profile.applied");
        permaworld$activeProfile = profile.name;
        permaworld$customProfile = false;
        permaworld$profiles.setActiveProfileName(profile.name);
        permaworld$profiles.save();
        permaworld$profileDropdownOpen = false;
        permaworld$updateFilteredEntries(search == null ? "" : search.getValue());
        rebuildWidgets();
    }

    @Unique
    private void permaworld$renderTrashIcons(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                             TransferableSelectionList list) {
        PackRepository repository = ((PackSelectionModelAccessor) model).permaworld$getRepository();
        List<TransferableSelectionList.Entry> entries = list.children();
        for (int i = 0; i < entries.size(); i++) {
            TransferableSelectionList.Entry entry = entries.get(i);
            if (permaworld$fileManager.findInstalledByPackId(repository, entry.getPackId()).isEmpty()) {
                continue;
            }
            int rowTop = list.getRowTop(i);
            int rowBottom = list.getRowBottom(i);
            if (rowBottom < list.getY() || rowTop > list.getBottom()) {
                continue;
            }
            int iconX = list.getRowRight() - 24;
            int iconY = rowTop + 6;
            boolean hovered = permaworld$isInTrash(mouseX, mouseY, iconX, iconY);
            permaworld$drawTrash(extractor, iconX, iconY, hovered);
            if (hovered) {
                extractor.setTooltipForNextFrame(this.font,
                        Component.translatable("permaworld.resourcepack.delete"), mouseX, mouseY);
            }
        }
    }

    @Unique
    private void permaworld$renderDropTarget(GuiGraphicsExtractor extractor) {
        TransferableSelectionList list = permaworld$targetListWidget();
        if (list == null || permaworld$dragTargetIndex < 0) {
            return;
        }
        int y = permaworld$dropLineY(list, permaworld$dragTargetIndex);
        int left = list.getRowLeft() + 8;
        int right = list.getRowRight() - 8;
        extractor.fill(left, y - 2, right, y + 2, 0xFFFFFF55);
        extractor.fill(left, y - 1, right, y + 1, 0xFFFFDD55);
    }

    @Unique
    private void permaworld$renderDropFlash(GuiGraphicsExtractor extractor) {
        if (permaworld$dropFlashTicks <= 0 || permaworld$dropFlashList == DragList.NONE) {
            return;
        }
        TransferableSelectionList list = permaworld$dropFlashList == DragList.SELECTED ? selectedPackList : availablePackList;
        int y = permaworld$dropLineY(list, permaworld$dropFlashIndex);
        int left = list.getRowLeft() + 8;
        int right = list.getRowRight() - 8;
        int alpha = 0x22 + permaworld$dropFlashTicks * 0x11;
        extractor.fill(left, y - 3, right, y + 3, (alpha << 24) | 0x00FFAA);
        extractor.fill(left, y - 1, right, y + 1, 0xFFFFFFFF);
    }

    @Unique
    private int permaworld$dropLineY(TransferableSelectionList list, int index) {
        int count = list.children().size();
        if (count == 0) {
            return list.getY() + 26;
        }
        if (index >= count) {
            return list.getRowBottom(count - 1);
        }
        return list.getRowTop(Math.max(0, index));
    }

    @Unique
    private void permaworld$renderDraggedPack(GuiGraphicsExtractor extractor) {
        int width = 188;
        int height = 28;
        int x = permaworld$dragMouseX + 12;
        int y = permaworld$dragMouseY + 12;
        extractor.fill(x, y, x + width, y + height, 0xCC101010);
        extractor.outline(x, y, width, height, 0xFFFFDD55);
        extractor.text(this.font, permaworld$draggingPackTitle, x + 8, y + 10, 0xFFFFFFFF);
    }

    @Unique
    private boolean permaworld$handleTrashClick(MouseButtonEvent event) {
        if (event.button() != 0 || permaworld$fileManager == null) {
            return false;
        }
        TransferableSelectionList.Entry entry = permaworld$trashEntryAt(event.x(), event.y(), availablePackList);
        if (entry == null) {
            return false;
        }

        String packId = entry.getPackId();
        PackRepository repository = ((PackSelectionModelAccessor) model).permaworld$getRepository();
        ResourcePackFileManager.PackFile pack = permaworld$fileManager.findInstalledByPackId(repository, packId).orElse(null);
        if (pack == null) {
            permaworld$status = Component.translatable("permaworld.resourcepack.delete.unavailable");
            rebuildWidgets();
            return true;
        }

        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                permaworld$deletePack(pack);
            }
            this.minecraft.setScreen((Screen) (Object) this);
        }, Component.translatable("permaworld.resourcepack.delete.confirm.title"),
                Component.translatable("permaworld.resourcepack.delete.confirm.message")));
        return true;
    }

    @Unique
    private TransferableSelectionList.Entry permaworld$trashEntryAt(double mouseX, double mouseY,
                                                                    TransferableSelectionList list) {
        if (list == null) {
            return null;
        }
        PackRepository repository = ((PackSelectionModelAccessor) model).permaworld$getRepository();
        List<TransferableSelectionList.Entry> entries = list.children();
        for (int i = 0; i < entries.size(); i++) {
            TransferableSelectionList.Entry entry = entries.get(i);
            if (permaworld$fileManager.findInstalledByPackId(repository, entry.getPackId()).isEmpty()) {
                continue;
            }
            int iconX = list.getRowRight() - 24;
            int iconY = list.getRowTop(i) + 6;
            if (permaworld$isInTrash(mouseX, mouseY, iconX, iconY)) {
                return entry;
            }
        }
        return null;
    }

    @Unique
    private void permaworld$deletePack(ResourcePackFileManager.PackFile pack) {
        try {
            permaworld$fileManager.delete(pack);
            model.findNewPacks();
            permaworld$markCustomProfile();
            permaworld$status = Component.translatable("permaworld.resourcepack.deleted");
            permaworld$updateFilteredEntries(search == null ? "" : search.getValue());
        } catch (Exception e) {
            permaworld$status = Component.translatable("permaworld.resourcepack.error", e.getMessage());
        }
    }

    @Unique
    private void permaworld$drawTrash(GuiGraphicsExtractor extractor, int x, int y, boolean hovered) {
        int color = hovered ? 0xFFFF7777 : 0xFFFF4444;
        extractor.fill(x + 3, y + 1, x + 13, y + 3, color);
        extractor.fill(x + 5, y - 1, x + 11, y + 1, color);
        extractor.outline(x + 4, y + 4, 10, 12, color);
        extractor.fill(x + 6, y + 6, x + 7, y + 14, color);
        extractor.fill(x + 10, y + 6, x + 11, y + 14, color);
    }

    @Unique
    private boolean permaworld$isInTrash(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX <= x + 16 && mouseY >= y - 2 && mouseY <= y + 18;
    }

    @Unique
    private Component permaworld$activeProfileLabel() {
        if (permaworld$customProfile || permaworld$activeProfile.isBlank()) {
            return Component.translatable("permaworld.resourcepack.profile.active",
                    Component.translatable("permaworld.resourcepack.profile.custom"));
        }
        return Component.translatable("permaworld.resourcepack.profile.active", permaworld$activeProfile);
    }

    @Unique
    private void permaworld$restoreActiveProfile() {
        if (!permaworld$activeProfile.isBlank() || !permaworld$customProfile) {
            return;
        }
        String storedName = permaworld$profiles.activeProfileName();
        ResourcePackProfileStore.Profile profile = permaworld$profiles.find(storedName);
        if (profile == null) {
            return;
        }
        List<String> currentPackIds = selectedPackList.children().stream()
                .map(TransferableSelectionList.Entry::getPackId)
                .toList();
        if (!profile.packIds.equals(currentPackIds)) {
            permaworld$profiles.setActiveProfileName("");
            permaworld$profiles.save();
            return;
        }
        permaworld$selectedProfile = profile.name;
        permaworld$activeProfile = profile.name;
        permaworld$customProfile = false;
    }

    @Unique
    private void permaworld$markCustomProfile() {
        permaworld$customProfile = true;
        permaworld$activeProfile = "";
        permaworld$profiles.setActiveProfileName("");
        permaworld$profiles.save();
    }

    @Unique
    private boolean permaworld$isInPackLists(double mouseX, double mouseY) {
        return permaworld$isInList(mouseX, mouseY, availablePackList) || permaworld$isInList(mouseX, mouseY, selectedPackList);
    }

    @Unique
    private boolean permaworld$isInList(double mouseX, double mouseY, TransferableSelectionList list) {
        return list != null
                && mouseX >= list.getX()
                && mouseX <= list.getRight()
                && mouseY >= list.getY()
                && mouseY <= list.getBottom();
    }

    @Unique
    private boolean permaworld$preparePackDrag(MouseButtonEvent event) {
        permaworld$clearDrag();
        if (event.button() != 0) {
            return false;
        }
        if (permaworld$prepareSelectedDrag(event)) {
            return true;
        }
        return permaworld$prepareAvailableDrag(event);
    }

    @Unique
    private boolean permaworld$prepareSelectedDrag(MouseButtonEvent event) {
        if (!permaworld$isInList(event.x(), event.y(), selectedPackList)
                || event.x() < selectedPackList.getRowLeft() + 40
                || event.x() > selectedPackList.getRowRight() - 40) {
            return false;
        }
        int index = permaworld$selectedRowIndexAt(event.x(), event.y());
        if (index < 0) {
            return false;
        }
        List<TransferableSelectionList.Entry> entries = selectedPackList.children();
        if (index >= entries.size()) {
            return false;
        }
        permaworld$draggingPackId = entries.get(index).getPackId();
        permaworld$draggingPackTitle = permaworld$packTitle(permaworld$draggingPackId);
        permaworld$dragSourceIndex = index;
        permaworld$dragSourceList = DragList.SELECTED;
        permaworld$dragMouseX = (int) event.x();
        permaworld$dragMouseY = (int) event.y();
        permaworld$updateDropTarget(event.x(), event.y());
        return true;
    }

    @Unique
    private boolean permaworld$prepareAvailableDrag(MouseButtonEvent event) {
        if (!permaworld$isInList(event.x(), event.y(), availablePackList)
                || event.x() < availablePackList.getRowLeft() + 40
                || event.x() > availablePackList.getRowRight() - 40) {
            return false;
        }
        int index = permaworld$rowIndexAt(event.y(), availablePackList);
        List<TransferableSelectionList.Entry> entries = availablePackList.children();
        if (index < 0 || index >= entries.size()) {
            return false;
        }
        permaworld$draggingPackId = entries.get(index).getPackId();
        permaworld$draggingPackTitle = permaworld$packTitle(permaworld$draggingPackId);
        permaworld$dragSourceIndex = index;
        permaworld$dragSourceList = DragList.AVAILABLE;
        permaworld$dragMouseX = (int) event.x();
        permaworld$dragMouseY = (int) event.y();
        permaworld$updateDropTarget(event.x(), event.y());
        return true;
    }

    @Unique
    private int permaworld$selectedRowIndexAt(double mouseX, double mouseY) {
        if (!permaworld$isInList(mouseX, mouseY, selectedPackList)) {
            return -1;
        }
        List<TransferableSelectionList.Entry> entries = selectedPackList.children();
        for (int i = 0; i < entries.size(); i++) {
            if (mouseY >= selectedPackList.getRowTop(i) && mouseY <= selectedPackList.getRowBottom(i)) {
                return i;
            }
        }
        return -1;
    }

    @Unique
    private int permaworld$selectedInsertIndexAt(double mouseX, double mouseY) {
        return permaworld$insertIndexAt(mouseX, mouseY, selectedPackList);
    }

    @Unique
    private int permaworld$availableInsertIndexAt(double mouseX, double mouseY) {
        return permaworld$insertIndexAt(mouseX, mouseY, availablePackList);
    }

    @Unique
    private int permaworld$insertIndexAt(double mouseX, double mouseY, TransferableSelectionList list) {
        if (!permaworld$isInList(mouseX, mouseY, list)) {
            return -1;
        }
        List<TransferableSelectionList.Entry> entries = list.children();
        if (entries.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < entries.size(); i++) {
            int rowTop = list.getRowTop(i);
            int rowBottom = list.getRowBottom(i);
            if (mouseY >= rowTop && mouseY <= rowBottom) {
                return mouseY > rowTop + (rowBottom - rowTop) / 2 ? i + 1 : i;
            }
        }
        return mouseY > list.getRowBottom(entries.size() - 1) ? entries.size() : -1;
    }

    @Unique
    private int permaworld$rowIndexAt(double mouseY, TransferableSelectionList list) {
        List<TransferableSelectionList.Entry> entries = list.children();
        for (int i = 0; i < entries.size(); i++) {
            if (mouseY >= list.getRowTop(i) && mouseY <= list.getRowBottom(i)) {
                return i;
            }
        }
        return -1;
    }

    @Unique
    private boolean permaworld$moveSelectedPack(String packId, int visualTargetIndex) {
        PackSelectionModelAccessor accessor = (PackSelectionModelAccessor) model;
        List<Pack> selected = accessor.permaworld$getSelectedPacks();
        int fromIndex = permaworld$packIndexIn(selected, packId);
        int toIndex = permaworld$modelInsertIndex(selected, selectedPackList, visualTargetIndex);
        if (fromIndex < 0 || toIndex < 0 || toIndex > selected.size()) {
            return false;
        }
        Pack pack = selected.remove(fromIndex);
        if (toIndex > fromIndex) {
            toIndex--;
        }
        if (toIndex < 0 || toIndex > selected.size()) {
            selected.add(fromIndex, pack);
            return false;
        }
        selected.add(toIndex, pack);
        return true;
    }

    @Unique
    private boolean permaworld$moveAvailablePackToSelected(String packId, int visualTargetIndex) {
        PackSelectionModelAccessor accessor = (PackSelectionModelAccessor) model;
        List<Pack> selected = accessor.permaworld$getSelectedPacks();
        List<Pack> unselected = accessor.permaworld$getUnselectedPacks();
        int targetIndex = permaworld$modelInsertIndex(selected, selectedPackList, visualTargetIndex);
        if (targetIndex < 0 || targetIndex > selected.size()) {
            return false;
        }
        Pack pack = null;
        for (Pack candidate : unselected) {
            if (candidate.getId().equals(packId)) {
                pack = candidate;
                break;
            }
        }
        if (pack == null) {
            return false;
        }
        unselected.remove(pack);
        selected.add(targetIndex, pack);
        return true;
    }

    @Unique
    private boolean permaworld$moveSelectedPackToAvailable(String packId, int visualTargetIndex) {
        PackSelectionModelAccessor accessor = (PackSelectionModelAccessor) model;
        List<Pack> selected = accessor.permaworld$getSelectedPacks();
        List<Pack> unselected = accessor.permaworld$getUnselectedPacks();
        int targetIndex = permaworld$modelInsertIndex(unselected, availablePackList, visualTargetIndex);
        if (targetIndex < 0 || targetIndex > unselected.size()) {
            return false;
        }
        Pack pack = null;
        for (Pack candidate : selected) {
            if (candidate.getId().equals(packId)) {
                pack = candidate;
                break;
            }
        }
        if (pack == null) {
            return false;
        }
        selected.remove(pack);
        unselected.add(targetIndex, pack);
        return true;
    }

    @Unique
    private int permaworld$modelInsertIndex(List<Pack> modelPacks, TransferableSelectionList visibleList, int visualTargetIndex) {
        List<TransferableSelectionList.Entry> visibleEntries = visibleList.children();
        if (visibleEntries.isEmpty()) {
            return modelPacks.size();
        }
        if (visualTargetIndex < visibleEntries.size()) {
            return permaworld$packIndexIn(modelPacks, visibleEntries.get(visualTargetIndex).getPackId());
        }
        return permaworld$packIndexIn(modelPacks, visibleEntries.get(visibleEntries.size() - 1).getPackId()) + 1;
    }

    @Unique
    private int permaworld$packIndexIn(List<Pack> packs, String packId) {
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).getId().equals(packId)) {
                return i;
            }
        }
        return -1;
    }

    @Unique
    private void permaworld$refreshPackLists() {
        permaworld$updateFilteredEntries(search == null ? "" : search.getValue());
    }

    @Unique
    private void permaworld$updateDropTarget(double mouseX, double mouseY) {
        int selectedIndex = permaworld$selectedInsertIndexAt(mouseX, mouseY);
        if (selectedIndex >= 0) {
            permaworld$dragTargetList = DragList.SELECTED;
            permaworld$dragTargetIndex = selectedIndex;
            return;
        }
        int availableIndex = permaworld$availableInsertIndexAt(mouseX, mouseY);
        if (availableIndex >= 0) {
            permaworld$dragTargetList = DragList.AVAILABLE;
            permaworld$dragTargetIndex = availableIndex;
            return;
        }
        permaworld$dragTargetList = DragList.NONE;
        permaworld$dragTargetIndex = -1;
    }

    @Unique
    private void permaworld$applyDragDrop() {
        boolean changed = false;
        if (permaworld$dragTargetList == DragList.SELECTED && permaworld$dragSourceList == DragList.SELECTED) {
            changed = permaworld$moveSelectedPack(permaworld$draggingPackId, permaworld$dragTargetIndex);
        } else if (permaworld$dragTargetList == DragList.SELECTED && permaworld$dragSourceList == DragList.AVAILABLE) {
            changed = permaworld$moveAvailablePackToSelected(permaworld$draggingPackId, permaworld$dragTargetIndex);
        } else if (permaworld$dragTargetList == DragList.AVAILABLE && permaworld$dragSourceList == DragList.SELECTED) {
            changed = permaworld$moveSelectedPackToAvailable(permaworld$draggingPackId, permaworld$dragTargetIndex);
        }
        if (changed) {
            permaworld$markCustomProfile();
            permaworld$dropFlashList = permaworld$dragTargetList;
            permaworld$dropFlashIndex = permaworld$dragTargetIndex;
            permaworld$dropFlashTicks = 8;
            permaworld$refreshPackLists();
            permaworld$playDropSound();
        }
    }

    @Unique
    private TransferableSelectionList permaworld$targetListWidget() {
        if (permaworld$dragTargetList == DragList.SELECTED) {
            return selectedPackList;
        }
        if (permaworld$dragTargetList == DragList.AVAILABLE) {
            return availablePackList;
        }
        return null;
    }

    @Unique
    private boolean permaworld$isDraggingPack() {
        return permaworld$dragSourceList != DragList.NONE;
    }

    @Unique
    private Component permaworld$packTitle(String packId) {
        Pack pack = ((PackSelectionModelAccessor) model).permaworld$getRepository().getPack(packId);
        return pack == null ? Component.literal(packId) : pack.getTitle();
    }

    @Unique
    private void permaworld$playPickupSound() {
        if (this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUNDLE_INSERT, 1.25F));
        }
    }

    @Unique
    private void permaworld$playDropSound() {
        if (this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUNDLE_REMOVE_ONE, 0.85F));
        }
    }

    @Unique
    private void permaworld$clearDrag() {
        permaworld$draggingPackId = "";
        permaworld$draggingPackTitle = Component.empty();
        permaworld$dragSourceIndex = -1;
        permaworld$dragSourceList = DragList.NONE;
        permaworld$dragTargetList = DragList.NONE;
        permaworld$dragTargetIndex = -1;
    }

    @Unique
    private static String permaworld$trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    @Unique
    private enum DragList {
        NONE,
        AVAILABLE,
        SELECTED
    }
}
