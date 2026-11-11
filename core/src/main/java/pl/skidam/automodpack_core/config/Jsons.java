
package pl.skidam.automodpack_core.config;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.annotations.SerializedName;

import pl.skidam.automodpack_core.Constants;
import pl.skidam.automodpack_core.auth.Secrets;
import pl.skidam.automodpack_core.protocol.ModpackConnectionMode;

@SuppressWarnings("unused")
public class Jsons {

	public static class VersionConfigField {
		public int DO_NOT_CHANGE_IT = 0; // file version
	}

	public static class ClientConfigFieldsV3 {
		public int DO_NOT_CHANGE_IT = 3; // file version
		public String selectedModpackId = "";
		@SerializedName(value = "modpackConnections", alternate = "installedModpacks")
		public Map<String, ConnectionInfo> modpackConnections = new HashMap<>(); // modpack ID, known origin and endpoint routing
		public boolean updateSelectedModpackOnLaunch = true;
		public boolean selfUpdater = false;
		public boolean syncAutoModpackVersion = true;
		public boolean syncLoaderVersion = true;
		public boolean playMusic = true;
		public boolean allowRemoteNonModpackDeletions = true;

		public ClientConfigFieldsV3() {}

		public ClientConfigFieldsV3(ClientConfigFieldsV3 source) {
			this.selectedModpackId = source.selectedModpackId;
			this.modpackConnections = source.modpackConnections == null ? new HashMap<>() : new HashMap<>(source.modpackConnections);
			this.updateSelectedModpackOnLaunch = source.updateSelectedModpackOnLaunch;
			this.selfUpdater = source.selfUpdater;
			this.syncAutoModpackVersion = source.syncAutoModpackVersion;
			this.syncLoaderVersion = source.syncLoaderVersion;
			this.playMusic = source.playMusic;
			this.allowRemoteNonModpackDeletions = source.allowRemoteNonModpackDeletions;
		}
	}

	public static class ConnectionInfo {
		@SerializedName(value = "origin", alternate = "serverAddress")
		public InetSocketAddress origin; // player-entered Minecraft identity and certificate trust root
		@SerializedName(value = "endpoint", alternate = "hostAddress")
		public InetSocketAddress endpoint; // server-advertised AutoModpack route; not an authenticated identity
		public ModpackConnectionMode connectionMode;
		public transient String expectedFingerprint; // runtime-only exact certificate pin bound to origin
		public transient String trustReason; // non-null only while importing new trust

		public ConnectionInfo() {}

		public ConnectionInfo(InetSocketAddress origin, InetSocketAddress endpoint, ModpackConnectionMode connectionMode, String expectedFingerprint, String trustReason) {
			this.origin = origin;
			this.endpoint = endpoint;
			this.connectionMode = connectionMode;
			this.expectedFingerprint = expectedFingerprint;
			this.trustReason = trustReason;
		}

		public boolean isComplete() {
			return origin != null && endpoint != null && connectionMode != null && !origin.getHostString().isBlank() && !endpoint.getHostString().isBlank();
		}
	}

	public static class ServerConfigFields {
		public int DO_NOT_CHANGE_IT = 1;
		public boolean modpackHost = true;
		public boolean generateModpackOnStart = true;
		public List<String> syncedModFolders = List.of("mods", "mods/client_recommended");
		@SerializedName("server-only-mods")
		public Set<String> serverOnlyMods = Set.of();
		public List<String> syncedConfigs = List.of();
		public Map<String, String> nonModpackFilesToDelete = Map.of();
		public boolean autoExcludeUnnecessaryFiles = true;
		public String bindAddress = "";
		public int bindPort = -1;
		public String advertisedEndpointHost = "";
		public int advertisedEndpointPort = -1;
		public boolean disableInternalTLS = false;
		public ModpackConnectionMode connectionMode = ModpackConnectionMode.defaultFor(Constants.MC_VERSION, Constants.LOADER);
		public boolean updateIpsOnEveryStart = false;
		public int bandwidthLimit = 0;
		public boolean validateSecrets = true;
		public long secretLifetime = 336; // 336 hours = 14 days
		public boolean selfUpdater = false;
		public Set<String> acceptedLoaders = new HashSet<>();
	}

	public static class ServerCoreConfigFields {
		public String automodpackVersion = "";
		public String loader = "";
		public String loaderVersion = "";
		public String mcVersion = "";
	}

	public static class SecretsFields {
		public Map<String, Secrets.Secret> secrets = new HashMap<>();
	}

	public static class KnownHostsFields {
		public Map<String, CertificateTrustEntry> hosts; // canonical Minecraft origin, exact certificate trust
	}

	public static class CertificateTrustEntry {
		public String fingerprint;
		public String reason;

		public CertificateTrustEntry() {}

		public CertificateTrustEntry(String fingerprint, String reason) {
			this.fingerprint = fingerprint;
			this.reason = reason;
		}
	}

	public static class KnownHostsBootstrapFields {
		public String origin;
		public String fingerprint;
		public String modpackId;
		public String endpoint;
		public ModpackConnectionMode connectionMode;
	}

	public static class ModpackContentFields {
		public String modpackId = "";
		public String modpackName = "";
		public String automodpackVersion = "";
		public String loader = "";
		public String loaderVersion = "";
		public String mcVersion = "";
		public Set<ModpackContentItem> list;
		public Set<FileToDelete> nonModpackFilesToDelete = Set.of();

		public ModpackContentFields(Set<ModpackContentItem> list) {
			this.list = list;
		}

		public ModpackContentFields() {
			this.list = Set.of();
		}

		public static class ModpackContentItem {
			public final String file;
			public final String size;
			public final String type;
			public final boolean editable;
			public final boolean overwriteEditable;
			public final boolean forceCopy;
			public final String sha1;
			public final String murmur;
			public final String modId;
			public final String modVersion;

			public ModpackContentItem(String file, String size, String type, boolean editable, boolean overwriteEditable, boolean forceCopy, String sha1, String murmur) {
				this(file, size, type, editable, overwriteEditable, forceCopy, sha1, murmur, null, null);
			}

			public ModpackContentItem(String file, String size, String type, boolean editable, boolean overwriteEditable, boolean forceCopy, String sha1, String murmur,
					String modId, String modVersion) {
				this.file = file;
				this.size = size;
				this.type = type;
				this.editable = editable;
				this.overwriteEditable = overwriteEditable;
				this.forceCopy = forceCopy;
				this.sha1 = sha1;
				this.murmur = murmur;
				this.modId = modId;
				this.modVersion = modVersion;
			}

			@Override
			public String toString() {
				return String.format("ModpackContentItems(file=%s, size=%s, type=%s, editable=%s, forceCopy=%s, sha1=%s, murmur=%s)", file, size, type,
						editable, forceCopy, sha1, murmur);
			}

			// if the relative file path is the same, we consider the items equal
			@Override
			public boolean equals(Object obj) {
				if (this == obj) return true;
				if (obj == null || getClass() != obj.getClass()) return false;
				ModpackContentItem that = (ModpackContentItem) obj;
				return Objects.equals(file, that.file);
			}

			@Override
			public int hashCode() {
				return Objects.hash(file);
			}
		}

		public static class FileToDelete {
			public final String file;
			public final String sha1;
			public final String timestamp;

			public FileToDelete(String file, String sha1, String timestamp) {
				this.file = file;
				this.sha1 = sha1;
				this.timestamp = timestamp;
			}
		}
	}

	// seems kinda too verbose and it may take too much space for large modpack but lets keep it for now
	public static class LocalMetadata {
		// Map of File Path -> Fingerprint
		public Map<String, FileFingerprint> files = new ConcurrentHashMap<>();

		public static class FileFingerprint {
			public final String sha1;
			public final long lastSize; // Local disk size
			public final long lastModified; // Local disk timestamp

			public FileFingerprint(String sha1, long lastSize, long lastModified) {
				this.sha1 = sha1;
				this.lastSize = lastSize;
				this.lastModified = lastModified;
			}
		}
	}

	public static class ClientDummyFiles {
		// Set of absolute file paths to delete when we can
		public Set<String> files = ConcurrentHashMap.newKeySet();
	}

	public static class ClientDeletedNonModpackFilesTimestamps {
		// Set of timestamps of the files to delete
		public Set<String> timestamps = ConcurrentHashMap.newKeySet();
	}

	// Per-modpack record of the player's selected mod IDs and the server mods they last reviewed.
	public static class ClientSelectionManagerFields {
		public int DO_NOT_CHANGE_IT = 2; // file version
		public Map<String, ModpackSelection> selections = new HashMap<>();

		public static class ModpackSelection {
			public Set<String> selectedModIds = new HashSet<>();
			// Complete server mod snapshot from the last time the player confirmed this selection.
			// It is deliberately independent from selectedModIds so new or changed mods cannot be
			// hidden by the player's previous selection before the confirmation screen is shown.
			public Map<String, String> knownModFingerprints;

			public ModpackSelection() {}

			public ModpackSelection(Set<String> selectedModIds, Map<String, String> knownModFingerprints) {
				this.selectedModIds = selectedModIds;
				this.knownModFingerprints = knownModFingerprints;
			}
		}
	}
}
