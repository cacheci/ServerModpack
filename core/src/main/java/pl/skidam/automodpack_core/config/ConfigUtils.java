package pl.skidam.automodpack_core.config;

import static pl.skidam.automodpack_core.Constants.*;

import java.io.IOException;
import java.util.*;

import pl.skidam.automodpack_core.protocol.ModpackConnectionMode;

public class ConfigUtils {

	public static void normalizeServerConfig(Jsons.ServerConfigFields config, boolean saveAfter) {
		normalizeServerConfig(config);
		if (saveAfter) {
			try {
				ConfigTools.writeAtomic(serverConfigFile, config);
			} catch (IOException e) {
				throw new ConfigTools.ConfigException("Failed to save server configuration", e);
			}
		}
	}

	public static void normalizeServerConfig(Jsons.ServerConfigFields config) {
		if (config.connectionMode == null) config.connectionMode = ModpackConnectionMode.defaultFor(MC_VERSION, LOADER);
		if (config.connectionMode == ModpackConnectionMode.HOLEPUNCH && !ModpackConnectionMode.isHolepunchAvailable(MC_VERSION, LOADER)) {
			config.connectionMode = ModpackConnectionMode.MAGIC_PACKET;
			LOGGER.warn("HOLEPUNCH connection mode is unavailable on " + LOADER + " " + MC_VERSION + ". Falling back to MAGIC_PACKET");
		}

		Map<String, String> fixedNonModpackFilesToDelete = new LinkedHashMap<>(config.nonModpackFilesToDelete.size());

		config.syncedModFolders = normalizePaths(config.syncedModFolders);
		config.syncedConfigs = normalizePaths(config.syncedConfigs);
		if (config.serverOnlyMods == null) config.serverOnlyMods = Set.of();

		for (var entry : config.nonModpackFilesToDelete.entrySet()) {
			var file = entry.getKey();
			var hash = entry.getValue();
			if (file == null) {
				LOGGER.warn("Ignored null key in nonModpackFilesToDelete.");
				continue;
			}
			var trimmed = file.trim();
			if (trimmed.isEmpty()) {
				LOGGER.warn("Ignored empty key in nonModpackFilesToDelete.");
				continue;
			}
			fixedNonModpackFilesToDelete.put(prefixSlash(trimmed), hash);
		}

		config.nonModpackFilesToDelete = fixedNonModpackFilesToDelete;
	}

	private static List<String> normalizePaths(List<String> paths) {
		if (paths == null) return List.of();
		return paths.stream().filter(Objects::nonNull).map(String::trim).filter(path -> !path.isEmpty()).distinct().toList();
	}

	public static String prefixSlash(String path) {
		if (path == null) return null;
		if (path.isEmpty()) return path;
		if (path.startsWith("/!/")) return path.substring(1);
		if (path.startsWith("/")) return path;
		if (path.startsWith("!/")) return path;
		if (path.charAt(0) == '!') return "!/" + path.substring(1);
		return "/" + path;
	}
}
