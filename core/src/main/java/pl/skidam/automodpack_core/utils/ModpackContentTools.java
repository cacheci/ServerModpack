package pl.skidam.automodpack_core.utils;

import static pl.skidam.automodpack_core.Constants.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
		Set<String> localMods = localModFingerprints();
		for (var item : server.list) {
			if ("mod".equals(item.type) && item.modId != null && item.modVersion != null) {
				if (!localMods.contains(item.modId + "\0" + item.modVersion + "\0" + item.sha1)) return true;
				continue;
			}
			Path path = SmartFileUtils.getPath(modpackDir, item.file);
			if (!Files.isRegularFile(path) || item.sha1 == null || !item.sha1.equalsIgnoreCase(HashUtils.getHash(path))) return true;
		}
		return false;
	}

	/** Mod IDs whose ID, version, and SHA-1 match a JAR in the client's own mods directory. */
	public static Set<String> matchingClientModIds(Jsons.ModpackContentFields content) {
		Set<String> matches = new HashSet<>();
		if (content == null || content.list == null) return matches;
		Set<String> localMods = new HashSet<>();
		clientMods().forEach(mod -> localMods.add(mod.modId() + "\0" + mod.version() + "\0" + mod.sha1()));
		for (var item : content.list) {
			if ("mod".equals(item.type) && item.modId != null && item.modVersion != null && item.sha1 != null
					&& localMods.contains(item.modId + "\0" + item.modVersion + "\0" + item.sha1)) matches.add(item.modId);
		}
		return matches;
	}

	public record ClientModInfo(String modId, String version, String sha1) {}

	/** Mod metadata from JARs in the client's own mods directory. */
	public static List<ClientModInfo> clientMods() {
		return modsInDirectory(MODS_DIR == null ? SmartFileUtils.CWD.resolve("mods") : MODS_DIR);
	}

	private static Set<String> localModFingerprints() {
		return fingerprintsInDirectory(MODS_DIR == null ? SmartFileUtils.CWD.resolve("mods") : MODS_DIR);
	}

	private static Set<String> fingerprintsInDirectory(Path directory) {
		Set<String> fingerprints = new HashSet<>();
		modsInDirectory(directory).forEach(mod -> fingerprints.add(mod.modId() + "\0" + mod.version() + "\0" + mod.sha1()));
		return fingerprints;
	}

	private static List<ClientModInfo> modsInDirectory(Path directory) {
		List<ClientModInfo> mods = new ArrayList<>();
		if (!Files.isDirectory(directory)) return mods;
		try (Stream<Path> files = Files.list(directory)) {
			files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar")).sorted().forEach(file -> {
				String id = FileInspection.getModID(file);
				String version = FileInspection.getModVersion(file);
				String sha1 = HashUtils.getHash(file);
				if (id != null && version != null && sha1 != null) mods.add(new ClientModInfo(id, version, sha1));
			});
		} catch (IOException e) {
			LOGGER.warn("Failed to inspect local mods in {}", directory, e);
		}
		return mods;
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
		Path clientCopy = modpackDir.resolve(hostModpackContentFile.getFileName());
		return Files.isRegularFile(clientCopy) ? Optional.of(clientCopy) : Optional.empty();
	}
}
