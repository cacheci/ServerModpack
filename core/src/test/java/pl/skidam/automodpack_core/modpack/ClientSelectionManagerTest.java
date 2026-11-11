package pl.skidam.automodpack_core.modpack;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import pl.skidam.automodpack_core.config.Jsons;

class ClientSelectionManagerTest {

	@Test
	void completeModFingerprintDetectsNewRemovedAndChangedModsBeforeSelectionFiltering() {
		var original = new Jsons.ModpackContentFields();
		original.list = Set.of(item("/mods/a.jar", "mod", "hash-a"), item("/config/main.json", "config", "config-hash"));
		var selection = new Jsons.ClientSelectionManagerFields.ModpackSelection(Set.of("a"), ClientSelectionManager.modFingerprints(original));

		assertFalse(ClientSelectionManager.hasUnreviewedModChanges(original, selection));

		var added = new Jsons.ModpackContentFields();
		added.list = Set.of(item("/mods/a.jar", "mod", "hash-a"), item("/mods/b.jar", "mod", "hash-b"));
		assertTrue(ClientSelectionManager.hasUnreviewedModChanges(added, selection));

		var changed = new Jsons.ModpackContentFields();
		changed.list = Set.of(item("/mods/a.jar", "mod", "hash-a-v2"));
		assertTrue(ClientSelectionManager.hasUnreviewedModChanges(changed, selection));

		var removed = new Jsons.ModpackContentFields();
		removed.list = Set.of();
		assertTrue(ClientSelectionManager.hasUnreviewedModChanges(removed, selection));
	}

	@Test
	void selectionWithoutReviewedSnapshotReopensSelection() {
		var content = new Jsons.ModpackContentFields();
		content.list = Set.of(item("/mods/a.jar", "mod", "hash-a"));
		var selection = new Jsons.ClientSelectionManagerFields.ModpackSelection(Set.of("a"), null);
		assertTrue(ClientSelectionManager.hasUnreviewedModChanges(content, selection));
	}

	private static Jsons.ModpackContentFields.ModpackContentItem item(String file, String type, String sha1) {
		String modId = "mod".equals(type) ? file.substring(file.lastIndexOf('/') + 1, file.length() - ".jar".length()) : null;
		return new Jsons.ModpackContentFields.ModpackContentItem(file, "1", type, false, false, false, sha1, null, modId,
				modId == null ? null : "1.0.0");
	}
}
