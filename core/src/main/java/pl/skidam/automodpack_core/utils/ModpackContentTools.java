package pl.skidam.automodpack_core.utils;

import static pl.skidam.automodpack_core.Constants.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import pl.skidam.automodpack_core.config.ConfigTools;
import pl.skidam.automodpack_core.config.Jsons;

public class ModpackContentTools {
	public static Jsons.ModpackContentFields read(Path path) {
		return ConfigTools.read(path, Jsons.ModpackContentFields.class).filter(ModpackContentTools::isValid).orElse(null);
	}

	/** Compares every server file with the installed manifest, independent of client selections. */
	public static boolean hasSha1Differences(Jsons.ModpackContentFields server, Jsons.ModpackContentFields installed) {
		if (server == null || server.list == null) throw new IllegalArgumentException("Server modpack content list is null");
		if (installed == null || installed.list == null) return true;
		return !fileHashes(server).equals(fileHashes(installed));
	}

	/** Checks the complete server manifest against both the installed manifest and files on disk. */
	public static boolean hasLocalFileDifferences(Jsons.ModpackContentFields server, Jsons.ModpackContentFields installed, Path modpackDir) {
		if (hasSha1Differences(server, installed)) return true;
		for (var item : server.list) {
			Path path = SmartFileUtils.getPath(modpackDir, item.file);
			if (!Files.isRegularFile(path) || item.sha1 == null || !item.sha1.equalsIgnoreCase(HashUtils.getHash(path))) return true;
		}
		return false;
	}

	private static Map<String, String> fileHashes(Jsons.ModpackContentFields content) {
		Map<String, String> hashes = new HashMap<>();
		for (var item : content.list) {
			hashes.put(item.file, item.sha1 == null ? null : item.sha1.toLowerCase(Locale.ROOT));
		}
		return hashes;
	}

	public static void write(Path path, Jsons.ModpackContentFields content) throws IOException {
		if (!isValid(content)) throw new ConfigTools.ConfigException("Invalid modpack content");
		ConfigTools.writeAtomic(path, content);
	}

	private static boolean isValid(Jsons.ModpackContentFields content) {
		return content != null && content.list != null;
	}

	public static String getFileType(String file, Jsons.ModpackContentFields list) {
		for (Jsons.ModpackContentFields.ModpackContentItem item : list.list) {
			if (item.file.contains(file)) { // compare file absolute path if it contains item.file
				return item.type;
			}
		}
		return "other";
	}

	public static Optional<Path> getModpackDir(String modpack) {
		if (modpack == null || modpack.isEmpty()) return Optional.empty();

		// eg. modpack = /automodpack/modpacks/TestPack `directory`

		return Optional.of(modpacksDir.resolve(modpack));
	}

	public static Optional<Path> getModpackContentFile(Path modpackDir) {
		if (!Files.exists(modpackDir)) return Optional.empty();

		Path path = modpackDir.getParent().resolve(hostModpackContentFile.getFileName()); // server
		if (!Files.exists(path)) {
			path = modpackDir.resolve(hostModpackContentFile.getFileName()); // client
			if (!Files.exists(path)) return Optional.empty();
		}

		return Optional.of(path);
	}
}
