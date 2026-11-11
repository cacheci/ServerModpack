package pl.skidam.automodpack_core.modpack;

import static pl.skidam.automodpack_core.Constants.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import pl.skidam.automodpack_core.config.Jsons;
import pl.skidam.automodpack_core.utils.ModpackContentTools;
import pl.skidam.automodpack_core.utils.cache.FileMetadataCache;

public class ModpackExecutor {
	public final Map<String, ModpackContent> modpacks = new ConcurrentHashMap<>();
	private final Object generationLock = new Object();
	private CompletableFuture<Jsons.ModpackContentFields> refreshInFlight;
	private volatile boolean generating;

	private ModpackContent init() {
		return new ModpackContent();
	}

	public boolean generateNew(ModpackContent content) {
		synchronized (generationLock) {
			if (content == null) return false;
			generating = true;
			try (var cache = FileMetadataCache.open(hashCacheDBFile)) {
				boolean generated = content.create(cache);
				if (generated) register(content);
				return generated;
			} finally {
				generating = false;
			}
		}
	}

	public boolean generateNew() {
		synchronized (generationLock) {
			return generateNew(init());
		}
	}

	public CompletableFuture<Jsons.ModpackContentFields> regenerateFullManifest() {
		synchronized (generationLock) {
			if (refreshInFlight != null && !refreshInFlight.isDone()) return refreshInFlight;
			refreshInFlight = CompletableFuture.supplyAsync(() -> {
				if (!generateNew()) throw new CompletionException(new IOException("Failed to regenerate modpack"));
				Jsons.ModpackContentFields manifest = ModpackContentTools.read(hostModpackContentFile);
				if (manifest == null) throw new CompletionException(new IOException("Regenerated manifest is unavailable"));
				return manifest;
			});
			return refreshInFlight;
		}
	}

	public boolean loadLast() {
		synchronized (generationLock) {
			ModpackContent content = init();
			if (content == null) return false;
			boolean generated = content.loadPreviousContent();
			if (generated) register(content);
			return generated;
		}
	}

	private void register(ModpackContent content) {
		modpacks.clear();
		modpacks.put(content.getModpackId(), content);
	}

	public boolean isGenerating() {
		return generating;
	}
}
