package pl.skidam.automodpack.client.ui;

import static pl.skidam.automodpack_core.Constants.LOGGER;
import static pl.skidam.automodpack_core.Constants.clientConfig;
import static pl.skidam.automodpack_core.Constants.hostModpackContentFile;

import java.nio.file.Path;
import java.util.*;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.MutableComponent;

import pl.skidam.automodpack.client.ui.versioned.VersionedMatrices;
import pl.skidam.automodpack.client.ui.versioned.VersionedScreen;
import pl.skidam.automodpack.client.ui.versioned.VersionedText;
import pl.skidam.automodpack_core.config.Jsons;
import pl.skidam.automodpack_core.modpack.ClientSelectionManager;
import pl.skidam.automodpack_core.utils.ModpackContentTools;
import pl.skidam.automodpack_loader_core.client.ModpackUtils;

/** Lets the player select individual mods for the current game session. */
public class ModpackSelectionScreen extends VersionedScreen {

	private static final int ROW_HEIGHT = 22;
	private static final int ROW_WIDTH = 280;

	private final Screen parent;
	private final String modpackId;
	private final String modpackName;
	private final List<Jsons.ModpackContentFields.ModpackContentItem> mods = new ArrayList<>();
	private final List<Jsons.ModpackContentFields.ModpackContentItem> visibleMods = new ArrayList<>();
	private final Map<String, ModpackContentTools.ClientModInfo> localModsById = new HashMap<>();
	private final Map<String, String> modFingerprints;
	private final Runnable savedAction;
	private final Runnable canceledAction;
	private final Set<String> selectedModIds = new LinkedHashSet<>();

	private int page = 0;
	private int rowsPerPage = 1;

	public ModpackSelectionScreen(Screen parent, Jsons.ModpackContentFields content) {
		this(parent, content, null, null);
	}

	public ModpackSelectionScreen(Screen parent, Jsons.ModpackContentFields content, Runnable savedAction, Runnable canceledAction) {
		super(VersionedText.translatable("automodpack.selection.title"));
		this.parent = parent;
		this.modpackId = content == null ? "" : content.modpackId;
		this.modpackName = content == null || content.modpackName == null ? "" : content.modpackName;
		if (content != null && content.list != null) {
			content.list.stream().filter(item -> "mod".equals(item.type)).sorted(Comparator.comparing(item -> item.modId))
					.forEach(mods::add);
			List<ModpackContentTools.ClientModInfo> clientMods = ModpackContentTools.clientMods();
			for (var item : mods) {
				boolean identical = clientMods.stream().anyMatch(local -> Objects.equals(local.modId(), item.modId)
						&& Objects.equals(local.version(), item.modVersion) && Objects.equals(local.sha1(), item.sha1));
				if (identical) continue;
				visibleMods.add(item);
				clientMods.stream().filter(local -> Objects.equals(local.modId(), item.modId))
						.min(Comparator.comparing(local -> !Objects.equals(local.version(), item.modVersion)))
						.ifPresent(local -> localModsById.put(item.modId, local));
			}
		}
		this.modFingerprints = ClientSelectionManager.modFingerprints(content);
		this.savedAction = savedAction;
		this.canceledAction = canceledAction;

		selectedModIds.addAll(ClientSelectionManager.defaultSelectedModIds(content));
	}

	public static Screen forSelectedModpack(Screen parent) {
		return forModpackId(parent, clientConfig == null ? null : clientConfig.selectedModpackId);
	}

	public static Screen forServerAddress(Screen parent, String serverAddress) {
		return forModpackId(parent, modpackIdForServer(serverAddress));
	}

	private static Screen forModpackId(Screen parent, String modpackId) {
		if (modpackId == null || modpackId.isBlank()) return parent;
		Path contentFile = ModpackUtils.getModpackPath(modpackId).resolve(hostModpackContentFile.getFileName());
		Jsons.ModpackContentFields content = ModpackContentTools.read(contentFile);
		if (!hasSelectableMods(content)) {
			LOGGER.info("Modpack {} has no selectable mods", modpackId);
			return parent;
		}
		return new ModpackSelectionScreen(parent, content);
	}

	public static boolean serverHasModsToConfigure(String serverAddress) {
		String modpackId = modpackIdForServer(serverAddress);
		if (modpackId == null || modpackId.isBlank()) return false;
		return hasSelectableMods(ModpackContentTools.read(ModpackUtils.getModpackPath(modpackId).resolve(hostModpackContentFile.getFileName())));
	}

	public static boolean hasSelectableMods(Jsons.ModpackContentFields content) {
		if (content == null || content.list == null) return false;
		Set<String> matchingLocalMods = ModpackContentTools.matchingClientModIds(content);
		return content.list.stream().anyMatch(item -> "mod".equals(item.type) && !matchingLocalMods.contains(item.modId));
	}

	private static String modpackIdForServer(String serverAddress) {
		if (serverAddress == null || clientConfig == null || clientConfig.modpackConnections == null) return null;
		String wanted = normalizeAddress(serverAddress);
		boolean addressHasPort = serverAddress.lastIndexOf(':') > 0;
		String wantedHost = normalizeAddress(hostOnly(serverAddress));
		for (var entry : clientConfig.modpackConnections.entrySet()) {
			var connection = entry.getValue();
			if (connection == null || connection.origin == null) continue;
			boolean exactMatch = normalizeAddress(connection.origin.getHostString() + ":" + connection.origin.getPort()).equals(wanted);
			boolean hostOnlyMatch = !addressHasPort && normalizeAddress(connection.origin.getHostString()).equals(wantedHost);
			if (exactMatch || hostOnlyMatch) return entry.getKey();
		}
		return null;
	}

	private static String normalizeAddress(String address) {
		return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
	}

	private static String hostOnly(String address) {
		if (address == null) return "";
		int colon = address.lastIndexOf(':');
		return colon > 0 ? address.substring(0, colon) : address;
	}

	@Override
	protected void init() {
		super.init();
		int listTop = 50;
		int listBottom = this.height - 60;
		rowsPerPage = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
		int pageCount = Math.max(1, (int) Math.ceil((double) visibleMods.size() / rowsPerPage));
		if (page >= pageCount) page = pageCount - 1;
		int x = this.width / 2 - ROW_WIDTH / 2;
		int start = page * rowsPerPage;

		for (int i = start; i < Math.min(visibleMods.size(), start + rowsPerPage); i++) {
			var item = visibleMods.get(i);
			int y = listTop + (i - start) * ROW_HEIGHT;
			var button = buttonWidget(x, y, ROW_WIDTH, 20, modRowLabel(item), press -> {
				if (!selectedModIds.remove(item.modId)) selectedModIds.add(item.modId);
				rebuild();
			});
			setTooltip(button, modTooltip(item));
			this.addRenderableWidget(button);
		}

		if (pageCount > 1) {
			this.addRenderableWidget(buttonWidget(x, listBottom + 4, 60, 20, VersionedText.literal("< Prev"), press -> {
				if (page > 0) {
					page--;
					rebuild();
				}
			}));
			this.addRenderableWidget(buttonWidget(x + ROW_WIDTH - 60, listBottom + 4, 60, 20, VersionedText.literal("Next >"), press -> {
				if (page < pageCount - 1) {
					page++;
					rebuild();
				}
			}));
		}

		this.addRenderableWidget(buttonWidget(this.width / 2 - 155, this.height - 28, 100, 20,
				VersionedText.translatable(allVisibleSelected() ? "automodpack.selection.deselectAll" : "automodpack.selection.reset"),
				press -> {
					boolean deselect = allVisibleSelected();
					for (var item : visibleMods) {
						if (deselect) selectedModIds.remove(item.modId);
						else selectedModIds.add(item.modId);
					}
					rebuild();
				}));
		this.addRenderableWidget(buttonWidget(this.width / 2 - 50, this.height - 28, 100, 20, VersionedText.translatable("automodpack.selection.cancel"),
				press -> cancel()));
		this.addRenderableWidget(buttonWidget(this.width / 2 + 55, this.height - 28, 100, 20,
					VersionedText.translatable("automodpack.selection.save").withStyle(ChatFormatting.BOLD), press -> save()));
	}

	private MutableComponent modRowLabel(Jsons.ModpackContentFields.ModpackContentItem item) {
		String name = item.modId != null && item.modVersion != null ? item.modId + " " + item.modVersion : Path.of(item.file).getFileName().toString();
		if (selectedModIds.contains(item.modId)) return VersionedText.literal("[x] " + name).withStyle(ChatFormatting.GREEN);
		return VersionedText.literal("[ ] " + name).withStyle(ChatFormatting.GRAY);
	}

	private boolean allVisibleSelected() {
		return visibleMods.stream().allMatch(item -> selectedModIds.contains(item.modId));
	}

	private MutableComponent modTooltip(Jsons.ModpackContentFields.ModpackContentItem item) {
		var local = localModsById.get(item.modId);
		if (local == null) return VersionedText.literal(item.file).withStyle(ChatFormatting.GRAY);
		if (!Objects.equals(local.version(), item.modVersion))
			return VersionedText.literal(local.version() + " -> " + item.modVersion).withStyle(ChatFormatting.GRAY);
		if (!Objects.equals(local.sha1(), item.sha1))
			return VersionedText.literal(local.sha1() + " -> " + item.sha1).withStyle(ChatFormatting.GRAY);
		return VersionedText.literal(item.file).withStyle(ChatFormatting.GRAY);
	}

	private void rebuild() {
		/*? if >=1.19.2 {*/
		this.rebuildWidgets();
		/*?} else {*/
		/*
		this.init(this.minecraft, this.width, this.height);
		*//*?}*/
	}

	private void save() {
		ClientSelectionManager.getManager().saveSelection(modpackId, selectedModIds, modFingerprints);
		if (savedAction != null) {
			savedAction.run();
			return;
		}
		this.minecraft.gui.setScreen(parent);
	}

	private void cancel() {
		if (canceledAction != null) {
			canceledAction.run();
			return;
		}
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void versionedRender(VersionedMatrices matrices, int mouseX, int mouseY, float delta) {
		MutableComponent header = modpackName.isBlank()
				? VersionedText.translatable("automodpack.selection.title")
				: VersionedText.literal(modpackName + " – ").append(VersionedText.translatable("automodpack.selection.title"));
		drawCenteredTextWithShadow(matrices, this.font, header.withStyle(ChatFormatting.BOLD), this.width / 2, 18, TextColors.WHITE);
		drawCenteredTextWithShadow(matrices, this.font, VersionedText.translatable("automodpack.selection.description").withStyle(ChatFormatting.GRAY),
				this.width / 2, 32, TextColors.WHITE);
	}
}
