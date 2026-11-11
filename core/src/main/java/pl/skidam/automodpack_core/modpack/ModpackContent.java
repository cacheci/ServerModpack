package pl.skidam.automodpack_core.modpack;

import static pl.skidam.automodpack_core.Constants.*;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import pl.skidam.automodpack_core.config.Jsons;
import pl.skidam.automodpack_core.loader.LoaderManagerService;
import pl.skidam.automodpack_core.utils.*;
import pl.skidam.automodpack_core.utils.cache.FileMetadataCache;

public class ModpackContent {
	public final Set<Jsons.ModpackContentFields.ModpackContentItem> list = ConcurrentHashMap.newKeySet();
	public final ObservableMap<String, Path> pathsMap = new ObservableMap<>();
	private final String modpackId;

	public ModpackContent() {
		Jsons.ModpackContentFields previous = ModpackContentTools.read(hostModpackContentFile);
		modpackId = previous != null && ModpackId.isValid(previous.modpackId) ? previous.modpackId : ModpackId.generate();
	}

	public String getModpackId() {
		return modpackId;
	}

	public boolean create(FileMetadataCache cache) {
		try {
			Map<String, Path> sources = discoverSources();
			Jsons.ModpackContentFields previous = ModpackContentTools.read(hostModpackContentFile);
			Map<String, String> previousMurmurs = new HashMap<>();
			if (previous != null) previous.list.forEach(item -> previousMurmurs.put(item.sha1, item.murmur));
			list.clear();
			pathsMap.clear();
			Map<String, Path> modIds = new HashMap<>();

			for (var entry : sources.entrySet()) {
				Path source = entry.getValue();
				String target = entry.getKey();
				boolean mod = target.startsWith("/mods/");
				String fileName = source.getFileName().toString();
				if (serverConfig.autoExcludeUnnecessaryFiles && (Files.size(source) == 0 || fileName.startsWith(".")
						|| fileName.endsWith(".tmp") || fileName.endsWith(".disabled") || fileName.endsWith(".bak")))
					continue;
				String modId = null;
				String modVersion = null;
				if (mod) {
					if (FileInspection.getModEnvironment(source) == LoaderManagerService.EnvironmentType.SERVER) continue;
					modId = FileInspection.getModID(source);
					modVersion = FileInspection.getModVersion(source);
					if (modId == null || modId.isBlank() || modVersion == null || modVersion.isBlank() || modVersion.contains("${")) {
						LOGGER.warn("Skipping mod without ID or version: {}", source);
						continue;
					}
					if (serverConfig.serverOnlyMods.contains(modId) || modId.equals(MOD_ID) || modId.equals(MOD_ID + "_bootstrap")
							|| modId.equals(MOD_ID + "-bootstrap") || modId.equals(MOD_ID + "_mod"))
						continue;
					Path previousSource = modIds.putIfAbsent(modId, source);
					if (previousSource != null && !previousSource.equals(source))
						throw new IOException("Conflicting mod ID " + modId + ": " + previousSource + " and " + source);
				}
				String sha1 = cache == null ? HashUtils.getHash(source) : cache.getHashOrNull(source);
				if (sha1 == null) continue;
				String murmur = mod ? previousMurmurs.get(sha1) : null;
				if (mod && murmur == null) murmur = HashUtils.getCurseforgeMurmurHash(source);
				var item = new Jsons.ModpackContentFields.ModpackContentItem(target, String.valueOf(Files.size(source)), mod ? "mod" : "config",
						false, false, false, sha1, murmur, modId, modVersion);
				list.add(item);
				pathsMap.put(sha1, source);
			}
			if (list.isEmpty()) {
				LOGGER.warn("Modpack is empty");
				return false;
			}
			saveModpackContent(previous);
			if (hostServer != null) hostServer.setPaths(pathsMap);
			return true;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to generate modpack", e);
			return false;
		}
	}

	public boolean loadPreviousContent() {
		Jsons.ModpackContentFields previous = ModpackContentTools.read(hostModpackContentFile);
		if (previous == null) return false;
		try {
			Map<String, Path> sources = discoverSources();
			list.clear();
			pathsMap.clear();
			for (var item : previous.list) {
				Path source = sources.get(item.file);
				if (source == null || !Files.isRegularFile(source)) {
					LOGGER.error("Manifest source is unavailable: {}", item.file);
					return false;
				}
				list.add(item);
				pathsMap.put(item.sha1, source);
			}
			if (hostServer != null) hostServer.setPaths(pathsMap);
			return !list.isEmpty();
		} catch (IOException e) {
			LOGGER.error("Failed to load generated modpack", e);
			return false;
		}
	}

	private void saveModpackContent(Jsons.ModpackContentFields previous) throws IOException {
		var content = new Jsons.ModpackContentFields(Set.copyOf(list));
		content.automodpackVersion = AM_VERSION;
		content.mcVersion = MC_VERSION;
		content.loaderVersion = LOADER_VERSION;
		content.loader = LOADER;
		content.modpackId = modpackId;
		content.modpackName = "";
		Map<String, Jsons.ModpackContentFields.FileToDelete> oldDeletions = previous == null
				? Map.of()
				: previous.nonModpackFilesToDelete.stream().collect(Collectors.toMap(item -> item.file, item -> item, (a, b) -> a));
		Set<Jsons.ModpackContentFields.FileToDelete> deletions = new HashSet<>();
		serverConfig.nonModpackFilesToDelete.forEach((file, sha1) -> {
			var old = oldDeletions.get(file);
			deletions.add(old != null && Objects.equals(old.sha1, sha1)
					? old
					: new Jsons.ModpackContentFields.FileToDelete(file, sha1, String.valueOf(System.currentTimeMillis())));
		});
		content.nonModpackFilesToDelete = deletions;
		ModpackContentTools.write(hostModpackContentFile, content);
	}

	private Map<String, Path> discoverSources() throws IOException {
		Map<String, Path> sources = new LinkedHashMap<>();
		for (String folder : serverConfig.syncedModFolders) {
			Path directory = resolve(folder);
			if (!Files.isDirectory(directory)) continue;
			try (Stream<Path> files = Files.walk(directory)) {
				for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar")).toList()) {
					putSource(sources, "/mods/" + file.getFileName(), file);
				}
			}
		}
		for (boolean excluded : new boolean[]{false, true}) {
			for (String rule : serverConfig.syncedConfigs) {
				if (rule == null || rule.isBlank() || rule.startsWith("!") != excluded) continue;
				String raw = excluded ? rule.substring(1) : rule;
				Path path = resolve(raw);
				boolean glob = raw.indexOf('*') >= 0 || raw.indexOf('?') >= 0 || raw.indexOf('{') >= 0;
				Path base = glob ? globBase(path) : Files.isDirectory(path) ? path : path.getParent();
				if (base == null || !Files.exists(base)) continue;
				PathMatcher matcher = glob ? FileSystems.getDefault().getPathMatcher("glob:" + path) : null;
				try (Stream<Path> files = Files.isDirectory(base) ? Files.walk(base) : Stream.of(base)) {
					for (Path file : files.filter(Files::isRegularFile).toList()) {
						if (glob && !matcher.matches(file)) continue;
						if (!glob && Files.isRegularFile(path) && !file.equals(path)) continue;
						Path source = file.toAbsolutePath().normalize();
						if (source.equals(hostModpackContentFile.toAbsolutePath().normalize()) || source.equals(serverConfigFile.toAbsolutePath().normalize())) continue;
						String target = configTarget(file, base);
						if (excluded) sources.remove(target);
						else putSource(sources, target, file);
					}
				}
			}
		}
		return sources;
	}

	private static Path resolve(String path) {
		Path value = Path.of(path);
		return (value.isAbsolute() ? value : SmartFileUtils.CWD.resolve(value)).toAbsolutePath().normalize();
	}

	private static Path globBase(Path path) {
		Path base = path.getRoot();
		for (Path part : path) {
			String name = part.toString();
			if (name.indexOf('*') >= 0 || name.indexOf('?') >= 0 || name.indexOf('{') >= 0) break;
			base = base.resolve(part);
		}
		return base;
	}

	private static String configTarget(Path file, Path base) {
		Path cwd = SmartFileUtils.CWD.toAbsolutePath().normalize();
		Path relative = file.startsWith(cwd) ? cwd.relativize(file) : base.relativize(file);
		String value = relative.toString().replace('\\', '/');
		return value.startsWith("config/") ? "/" + value : "/config/" + value;
	}

	private static void putSource(Map<String, Path> sources, String target, Path source) throws IOException {
		Path previous = sources.putIfAbsent(target, source);
		if (previous != null && !previous.equals(source)) throw new IOException("Conflicting modpack target " + target + ": " + previous + " and " + source);
	}
}
