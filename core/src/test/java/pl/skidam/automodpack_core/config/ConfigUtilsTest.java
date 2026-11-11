package pl.skidam.automodpack_core.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import pl.skidam.automodpack_core.Constants;
import pl.skidam.automodpack_core.protocol.ModpackConnectionMode;

class ConfigUtilsTest {
	@Test
	void holepunchAvailabilityFollowsPublishedProtocolVersions() {
		String previousVersion = Constants.MC_VERSION;
		String previousLoader = Constants.LOADER;
		try {
			Jsons.ServerConfigFields config = new Jsons.ServerConfigFields();
			config.bindPort = 24444;

			Constants.MC_VERSION = "1.20.1";
			Constants.LOADER = "fabric";

			config.connectionMode = ModpackConnectionMode.HOLEPUNCH;
			ConfigUtils.normalizeServerConfig(config);
			assertEquals(config.connectionMode, ModpackConnectionMode.HOLEPUNCH);
			assertEquals(24444, config.bindPort);

			Constants.LOADER = "forge";

			config.connectionMode = ModpackConnectionMode.HOLEPUNCH;
			ConfigUtils.normalizeServerConfig(config);
			assertEquals(config.connectionMode, ModpackConnectionMode.HOLEPUNCH);
			assertEquals(24444, config.bindPort);

			Constants.MC_VERSION = "1.19.2";
			Constants.LOADER = "fabric";

			config.connectionMode = ModpackConnectionMode.HOLEPUNCH;
			ConfigUtils.normalizeServerConfig(config);
			assertEquals(config.connectionMode, ModpackConnectionMode.MAGIC_PACKET);
			assertEquals(24444, config.bindPort);
		} finally {
			Constants.MC_VERSION = previousVersion;
			Constants.LOADER = previousLoader;
		}
	}
}
