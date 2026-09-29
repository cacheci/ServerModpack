package pl.skidam.automodpack_core.modpack;

import static pl.skidam.automodpack_core.Constants.LOGGER;
import static pl.skidam.automodpack_core.Constants.clientSelectionFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import pl.skidam.automodpack_core.config.ConfigTools;
import pl.skidam.automodpack_core.config.Jsons;

/**
 * Remembers which groups the player picked per modpack, so downloads are filtered to that choice
 * and the "Optional Mods" screen reopens with the previous answer already ticked. The selection is
 * only ever changed by the player through that screen; it is never prompted automatically. This
 * class also turns a raw pick into a consistent set of groups by applying
 * requires/breaksWith. The legacy required flag is retained in manifests for compatibility, but
 * does not force a client selection.
 * The resolution half is static and free of I/O so it can be exercised without a game or a disk.
 */
public class ClientSelectionManager {

	private static final ClientSelectionManager INSTANCE = new ClientSelectionManager(clientSelectionFile);

	private final Path selectionFile;
	private final Jsons.ClientSelectionManagerFields selections;

	public static ClientSelectionManager getManager() {
		return INSTANCE;
	}

	ClientSelectionManager(Path selectionFile) {
		this.selectionFile = selectionFile;
		this.selections = ConfigTools.readOrCreate(selectionFile, Jsons.ClientSelectionManagerFields.class, Jsons.ClientSelectionManagerFields::new);
		if (this.selections.selections == null) this.selections.selections = new HashMap<>();
	}

	// =================================================================================
	// PERSISTENCE
	// =================================================================================

	public Optional<Jsons.ClientSelectionManagerFields.ModpackSelection> getSelection(String modpackId) {
		if (modpackId == null || modpackId.isBlank()) return Optional.empty();
		return Optional.ofNullable(selections.selections.get(modpackId));
	}

	/** Records the player's pick for a modpack, replacing any previous choice. */
	public void saveSelection(String modpackId, Set<String> selectedGroups) {
		saveSelection(modpackId, selectedGroups, null);
	}

	/** Records both selected groups and the individual mods selected inside them. */
	public void saveSelection(String modpackId, Set<String> selectedGroups, Map<String, Set<String>> selectedModsByGroup) {
		saveSelection(modpackId, selectedGroups, selectedModsByGroup, null);
	}

	/** Records the selection together with the complete server mod list the player reviewed. */
	public void saveSelection(String modpackId, Set<String> selectedGroups, Map<String, Set<String>> selectedModsByGroup,
			Map<String, String> knownModFingerprints) {
		if (modpackId == null || modpackId.isBlank()) {
			LOGGER.warn("Refusing to save a group selection without a modpack ID.");
			return;
		}

		Map<String, Set<String>> copiedMods = null;
		if (selectedModsByGroup != null) {
			copiedMods = new HashMap<>();
			for (var entry : selectedModsByGroup.entrySet()) {
				copiedMods.put(entry.getKey(), entry.getValue() == null ? new HashSet<>() : new HashSet<>(entry.getValue()));
			}
		}
		Map<String, String> copiedFingerprints = knownModFingerprints == null ? null : new HashMap<>(knownModFingerprints);
		selections.selections.put(modpackId,
				new Jsons.ClientSelectionManagerFields.ModpackSelection(new HashSet<>(selectedGroups), copiedMods, copiedFingerprints));
		save();
	}

	public void save() {
		try {
			ConfigTools.writeAtomic(selectionFile, selections);
		} catch (IOException e) {
			LOGGER.error("Failed to save client group selection", e);
		}
	}

	// =================================================================================
	// RESOLUTION (pure)
	// =================================================================================

	/** Pre-ticked state for a player who has not chosen yet: everything recommended. */
	public static Set<String> defaultSelection(Map<String, Jsons.ModpackContentFields.ModpackGroupFields> groups) {
		if (groups == null) return Set.of();
		Set<String> chosen = new LinkedHashSet<>();
		groups.forEach((id, group) -> {
			if (group != null && group.recommended) chosen.add(id);
		});
		return resolve(groups, chosen);
	}

	/**
	 * Turns a raw pick into a consistent set: pulls in dependencies and breaks up conflicts. All
	 * groups are optional, including groups whose manifest retains the legacy required flag. When
	 * selected groups conflict, the group declared first wins.
	 */
	public static Set<String> resolve(Map<String, Jsons.ModpackContentFields.ModpackGroupFields> groups, Set<String> chosen) {
		if (groups == null || groups.isEmpty()) return Set.of();

		Map<String, Jsons.ModpackContentFields.ModpackGroupFields> usable = new LinkedHashMap<>();
		groups.forEach((id, group) -> {
			if (group != null) usable.put(id, group);
		});

		Set<String> wanted = new LinkedHashSet<>();
		usable.forEach((id, group) -> {
			if (chosen != null && chosen.contains(id)) wanted.add(id);
		});

		addRequiredDependencies(usable, wanted);

		List<String> byPriority = new ArrayList<>();
		usable.keySet().stream().filter(wanted::contains).forEach(byPriority::add);

		Set<String> kept = new LinkedHashSet<>();
		for (String id : byPriority) {
			String clash = kept.stream().filter(other -> conflicts(usable, other, id)).findFirst().orElse(null);
			if (clash == null) {
				kept.add(id);
			} else {
				LOGGER.info("Group {} conflicts with {}, leaving it out", id, clash);
			}
		}

		dropUnsatisfied(usable, kept);
		return kept;
	}

	private static void addRequiredDependencies(Map<String, Jsons.ModpackContentFields.ModpackGroupFields> usable, Set<String> wanted) {
		boolean changed = true;
		while (changed) {
			changed = false;
			for (String id : new ArrayList<>(wanted)) {
				var group = usable.get(id);
				if (group == null || group.requires == null) continue;
				for (String dependency : group.requires) {
					if (dependency == null) continue;
					if (!usable.containsKey(dependency)) continue;
					if (wanted.add(dependency)) changed = true;
				}
			}
		}
	}

	/** A group whose dependency was never available, or lost a conflict, cannot stay selected. */
	private static void dropUnsatisfied(Map<String, Jsons.ModpackContentFields.ModpackGroupFields> usable, Set<String> kept) {
		boolean changed = true;
		while (changed) {
			changed = false;
			for (String id : new ArrayList<>(kept)) {
				var group = usable.get(id);
				if (group == null || group.requires == null) continue;
				for (String dependency : group.requires) {
					if (dependency == null || dependency.isBlank()) continue;
					if (kept.contains(dependency)) continue;
					kept.remove(id);
					LOGGER.info("Group {} needs {}, which is not selected; leaving it out", id, dependency);
					changed = true;
					break;
				}
			}
		}
	}

	public static boolean conflicts(Map<String, Jsons.ModpackContentFields.ModpackGroupFields> groups, String a, String b) {
		if (Objects.equals(a, b)) return false;
		return breaksWith(groups.get(a), b) || breaksWith(groups.get(b), a);
	}

	private static boolean breaksWith(Jsons.ModpackContentFields.ModpackGroupFields group, String other) {
		return group != null && group.breaksWith != null && group.breaksWith.contains(other);
	}

	/**
	 * Whether the server's complete mod list differs from the list the player last reviewed. This
	 * check intentionally happens before applying selectedGroups/selectedModsByGroup: otherwise a
	 * newly added mod would be filtered out and could never reopen the selection screen.
	 */
	public static boolean hasUnreviewedModChanges(Jsons.ModpackContentFields content) {
		if (content == null || content.groups == null || content.groups.isEmpty()) return false;
		return getManager().getSelection(content.modpackId).map(selection -> hasUnreviewedModChanges(content, selection)).orElse(true);
	}

	static boolean hasUnreviewedModChanges(Jsons.ModpackContentFields content, Jsons.ClientSelectionManagerFields.ModpackSelection selection) {
		if (selection == null || selection.knownModFingerprints == null) return true;
		return !selection.knownModFingerprints.equals(modFingerprints(content));
	}

	/** Stable path-to-hash snapshot of every mod in the complete server manifest. */
	public static Map<String, String> modFingerprints(Jsons.ModpackContentFields content) {
		Map<String, String> fingerprints = new TreeMap<>();
		if (content == null || content.list == null) return fingerprints;
		content.list.forEach(item -> {
			if (item != null && "mod".equals(item.type) && item.file != null) fingerprints.put(item.file, item.sha1 == null ? "" : item.sha1);
		});
		return fingerprints;
	}

	/**
	 * Narrows a server manifest to the files the player's current selection keeps. Every part of the
	 * client that reasons about "what should be on disk" - the update check, the downloader, and the
	 * removal of files that are no longer wanted - must run on this filtered view rather than the
	 * server's full manifest, otherwise unselected files look perpetually missing and trigger an
	 * endless update loop. Returns the input untouched when there is nothing to filter.
	 */
	public static Jsons.ModpackContentFields filterToSelection(Jsons.ModpackContentFields content) {
		if (content == null || content.groups == null || content.groups.isEmpty() || content.list == null) return content;

		Optional<Jsons.ClientSelectionManagerFields.ModpackSelection> savedSelection = getManager().getSelection(content.modpackId);
		Set<String> chosen = savedSelection.map(selection -> selection.selectedGroups).orElseGet(() -> defaultSelection(content.groups));
		Set<String> resolved = resolve(content.groups, chosen);
		Map<String, Set<String>> selectedMods = savedSelection.map(selection -> selection.selectedModsByGroup).orElse(null);
		Set<String> allowedFiles = selectedFiles(content, resolved, selectedMods);

		if (allowedFiles.size() == content.list.size()) return content;
		LOGGER.info("Group selection covers {} of {} modpack files (groups: {})", allowedFiles.size(), content.list.size(), resolved);

		Set<Jsons.ModpackContentFields.ModpackContentItem> keptItems = new HashSet<>();
		content.list.forEach(item -> {
			if (allowedFiles.contains(item.file)) keptItems.add(item);
		});

		var filtered = new Jsons.ModpackContentFields(keptItems);
		filtered.modpackId = content.modpackId;
		filtered.modpackName = content.modpackName;
		filtered.automodpackVersion = content.automodpackVersion;
		filtered.loader = content.loader;
		filtered.loaderVersion = content.loaderVersion;
		filtered.mcVersion = content.mcVersion;
		filtered.nonModpackFilesToDelete = content.nonModpackFilesToDelete;
		filtered.groups = content.groups;
		return filtered;
	}

	/**
	 * Relative paths the given groups cover. Files outside every group stay included, so a client
	 * talking to a server that never declared groups still receives the whole modpack.
	 */
	public static Set<String> selectedFiles(Jsons.ModpackContentFields content, Set<String> selectedGroups) {
		return selectedFiles(content, selectedGroups, null);
	}

	/**
	 * Relative paths covered by the selected groups, with mod files optionally narrowed to the
	 * per-group picks. A missing per-group entry means all of that group's mods, which preserves old
	 * group-only selections and groups pulled in as dependencies. An explicit empty set means no mods
	 * from that group.
	 */
	public static Set<String> selectedFiles(Jsons.ModpackContentFields content, Set<String> selectedGroups,
			Map<String, Set<String>> selectedModsByGroup) {
		if (content == null || content.list == null) return Set.of();
		if (content.groups == null || content.groups.isEmpty()) {
			Set<String> everything = new HashSet<>();
			content.list.forEach(item -> everything.add(item.file));
			return everything;
		}

		Set<String> grouped = new HashSet<>();
		Set<String> allowed = new HashSet<>();
		Set<String> modFiles = new HashSet<>();
		content.list.forEach(item -> {
			if ("mod".equals(item.type)) modFiles.add(item.file);
		});
		content.groups.forEach((id, group) -> {
			if (group == null || group.files == null) return;
			grouped.addAll(group.files);
			if (selectedGroups == null || !selectedGroups.contains(id)) return;
			Set<String> selectedMods = selectedModsByGroup == null ? null : selectedModsByGroup.get(id);
			for (String file : group.files) {
				if (!modFiles.contains(file) || selectedMods == null || selectedMods.contains(file)) allowed.add(file);
			}
		});

		content.list.forEach(item -> {
			if (!grouped.contains(item.file)) allowed.add(item.file);
		});

		return allowed;
	}

	/** Mod file paths grouped by their manifest group, in manifest declaration order. */
	public static Map<String, Set<String>> modFilesByGroup(Jsons.ModpackContentFields content) {
		Map<String, Set<String>> result = new LinkedHashMap<>();
		if (content == null || content.groups == null || content.list == null) return result;

		Set<String> modFiles = new HashSet<>();
		content.list.forEach(item -> {
			if ("mod".equals(item.type)) modFiles.add(item.file);
		});
		content.groups.forEach((groupId, group) -> {
			Set<String> files = new LinkedHashSet<>();
			if (group != null && group.files != null) group.files.stream().filter(modFiles::contains).sorted().forEach(files::add);
			result.put(groupId, files);
		});
		return result;
	}
}
