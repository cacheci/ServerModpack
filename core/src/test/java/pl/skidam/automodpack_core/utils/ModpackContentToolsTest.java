package pl.skidam.automodpack_core.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pl.skidam.automodpack_core.config.Jsons;

class ModpackContentToolsTest {
	@TempDir
	Path modpackDir;

	@Test
	void comparesCompleteFileHashesWithoutSelectionOrReviewState() {
		var installed = content(item("/mods/a.jar", "abc"), item("/config/main.json", "123"));

		assertFalse(ModpackContentTools.hasSha1Differences(content(item("/mods/a.jar", "ABC"), item("/config/main.json", "123")), installed));
		assertTrue(ModpackContentTools.hasSha1Differences(content(item("/mods/a.jar", "def"), item("/config/main.json", "123")), installed));
		assertTrue(ModpackContentTools.hasSha1Differences(content(item("/mods/a.jar", "abc"), item("/config/main.json", "456")), installed));
		assertTrue(ModpackContentTools.hasSha1Differences(content(item("/mods/a.jar", "abc"), item("/config/main.json", "123"),
				item("/mods/new.jar", "789")), installed));
		assertTrue(ModpackContentTools.hasSha1Differences(content(item("/mods/a.jar", "abc")), installed));
		assertTrue(ModpackContentTools.hasSha1Differences(installed, null));
	}

	@Test
	void detectsMissingAndChangedLocalFilesEvenWhenInstalledManifestMatches() throws IOException {
		var mod = item("/mods/a.jar", "a9993e364706816aba3e25717850c26c9cd0d89d");
		var config = item("/config/main.json", "config", "40bd001563085fc35165329ea1ff5c5ecbdbbeef");
		var server = content(mod, config);
		Files.createDirectories(modpackDir.resolve("mods"));
		Files.createDirectories(modpackDir.resolve("config"));
		Files.writeString(modpackDir.resolve("mods/a.jar"), "abc");

		assertTrue(ModpackContentTools.hasLocalFileDifferences(server, server, modpackDir));
		Files.writeString(modpackDir.resolve("config/main.json"), "123");
		assertFalse(ModpackContentTools.hasLocalFileDifferences(server, server, modpackDir));
		Files.writeString(modpackDir.resolve("mods/a.jar"), "changed");
		assertTrue(ModpackContentTools.hasLocalFileDifferences(server, server, modpackDir));
		assertTrue(ModpackContentTools.hasLocalFileDifferences(server, content(mod), modpackDir));
	}

	private static Jsons.ModpackContentFields content(Jsons.ModpackContentFields.ModpackContentItem... items) {
		return new Jsons.ModpackContentFields(Set.of(items));
	}

	private static Jsons.ModpackContentFields.ModpackContentItem item(String path, String sha1) {
		return item(path, "mod", sha1);
	}

	private static Jsons.ModpackContentFields.ModpackContentItem item(String path, String type, String sha1) {
		return new Jsons.ModpackContentFields.ModpackContentItem(path, "1", type, false, false, false, sha1, null);
	}
}
