package pl.skidam.automodpack_core.modpack;

import static pl.skidam.automodpack_core.Constants.LOGGER;
import java.util.*;

import pl.skidam.automodpack_core.config.Jsons;
import pl.skidam.automodpack_core.utils.ModpackContentTools;

/** Applies mod choices for the current game session. */
public class ClientSelectionManager {

	private static final ClientSelectionManager INSTANCE = new ClientSelectionManager();
	private final Map<String, Jsons.ClientSelectionManagerFields.ModpackSelection> selections = new HashMap<>();

	public static ClientSelectionManager getManager() {
		return INSTANCE;
	}

	public Optional<Jsons.ClientSelectionManagerFields.ModpackSelection> getSelection(String modpackId) {
		if (modpackId == null || modpackId.isBlank()) return Optional.empty();
		return Optional.ofNullable(selections.get(modpackId));
	}

	/** Applies the current selection until the game exits. */
	public void saveSelection(String modpackId, Set<String> selectedModIds, Map<String, String> knownModFingerprints) {
		if (modpackId == null || modpackId.isBlank()) return;
		selections.put(modpackId, new Jsons.ClientSelectionManagerFields.ModpackSelection(new HashSet<>(selectedModIds),
				new HashMap<>(knownModFingerprints)));
	}

	/** Applies the default selection when no mod needs a choice. */
	public void saveSelectionWithoutVisibleMods(Jsons.ModpackContentFields content) {
		Map<String, String> current = modFingerprints(content);
		saveSelection(content.modpackId, defaultSelectedModIds(content), current);
	}

	public static Set<String> defaultSelectedModIds(Jsons.ModpackContentFields content) {
		Set<String> selected = new HashSet<>();
		if (content == null || content.list == null) return selected;
		List<ModpackContentTools.ClientModInfo> localMods = ModpackContentTools.clientMods();
		for (var item : content.list) {
			if (!"mod".equals(item.type) || item.modId == null || item.modVersion == null) continue;
			Optional<String> localVersion = localMods.stream().filter(local -> item.modId.equals(local.modId()))
					.map(ModpackContentTools.ClientModInfo::version).max(String::compareTo);
			if (localVersion.isPresent() && item.modVersion.compareTo(localVersion.get()) > 0)
				selected.add(item.modId);
		}
		return selected;
	}

	/** Whether the server's complete mod list differs from the list the player last reviewed. */
	public static boolean hasUnreviewedModChanges(Jsons.ModpackContentFields content) {
		if (content == null || content.list == null || content.list.stream().noneMatch(item -> "mod".equals(item.type))) return false;
		return getManager().getSelection(content.modpackId).map(selection -> hasUnreviewedModChanges(content, selection)).orElse(true);
	}

	static boolean hasUnreviewedModChanges(Jsons.ModpackContentFields content, Jsons.ClientSelectionManagerFields.ModpackSelection selection) {
		if (selection == null || selection.knownModFingerprints == null) return true;
		return !selection.knownModFingerprints.equals(modFingerprints(content));
	}

	/** Stable mod ID to version and hash snapshot of every mod in the complete server manifest. */
	public static Map<String, String> modFingerprints(Jsons.ModpackContentFields content) {
		Map<String, String> fingerprints = new TreeMap<>();
		if (content == null || content.list == null) return fingerprints;
		content.list.forEach(item -> {
			if (item != null && "mod".equals(item.type) && item.modId != null)
				fingerprints.put(item.modId, item.modVersion + ":" + item.sha1);
		});
		return fingerprints;
	}

	/** Limits downloads and update checks to the selected mods while retaining every config file. */
	public static Jsons.ModpackContentFields filterToSelection(Jsons.ModpackContentFields content) {
		if (content == null || content.list == null) return content;
		Set<String> selectedModIds = getManager().getSelection(content.modpackId)
				.map(selection -> selection.selectedModIds).orElseGet(() -> defaultSelectedModIds(content));

		Set<Jsons.ModpackContentFields.ModpackContentItem> kept = new HashSet<>();
		content.list.forEach(item -> {
			if (!"mod".equals(item.type) || selectedModIds.contains(item.modId)) kept.add(item);
		});
		if (kept.size() == content.list.size()) return content;
		LOGGER.info("Mod selection covers {} of {} modpack files", kept.size(), content.list.size());

		var filtered = new Jsons.ModpackContentFields(kept);
		filtered.modpackId = content.modpackId;
		filtered.modpackName = content.modpackName;
		filtered.automodpackVersion = content.automodpackVersion;
		filtered.loader = content.loader;
		filtered.loaderVersion = content.loaderVersion;
		filtered.mcVersion = content.mcVersion;
		filtered.nonModpackFilesToDelete = content.nonModpackFilesToDelete;
		return filtered;
	}
}
