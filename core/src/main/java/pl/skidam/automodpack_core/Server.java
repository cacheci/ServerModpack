package pl.skidam.automodpack_core;

import static pl.skidam.automodpack_core.Constants.*;

import java.io.IOException;
import java.nio.file.Path;

import pl.skidam.automodpack_core.config.ConfigTools;
import pl.skidam.automodpack_core.config.Jsons;
import pl.skidam.automodpack_core.modpack.ModpackContent;
import pl.skidam.automodpack_core.modpack.ModpackExecutor;
import pl.skidam.automodpack_core.protocol.netty.NettyServer;

public class Server {

	// TODO Finish this class that it will be able to host the server without mod
	public static void main(String[] args) throws IOException {

		if (args.length < 1) {
			LOGGER.error("Modpack directory not provided!");
			return;
		}

		NettyServer server = new NettyServer();
		hostServer = server;

		String modpackDirStr = args[0];

		Path cwd = Path.of(System.getProperty("user.dir"));
		Path modpackDir = cwd.resolve("modpacks").resolve(modpackDirStr);

		modpackDir.toFile().mkdirs();

		serverCoreConfigFile = modpackDir.resolve("automodpack-core.json");

		serverConfig = ConfigTools.readOrCreate(serverConfigFile, Jsons.ServerConfigFields.class, Jsons.ServerConfigFields::new);
		if (serverConfig != null) {
			serverConfig.validateSecrets = false;

			if (serverConfig.bindPort == -1) {
				LOGGER.error("Host port not set in config!");
				return;
			}
		}

		Jsons.ServerCoreConfigFields serverCoreConfig = ConfigTools.readOrCreate(serverCoreConfigFile, Jsons.ServerCoreConfigFields.class,
				Jsons.ServerCoreConfigFields::new);
		if (serverCoreConfig != null) {
			AM_VERSION = serverCoreConfig.automodpackVersion;
			LOADER = serverCoreConfig.loader;
			LOADER_VERSION = serverCoreConfig.loaderVersion;
			MC_VERSION = serverCoreConfig.mcVersion;
			ConfigTools.writeAtomic(serverCoreConfigFile, serverCoreConfig);
		}

		ModpackExecutor modpackExecutor = new ModpackExecutor();
		ModpackContent modpackContent = new ModpackContent();
		boolean generated = modpackExecutor.generateNew(modpackContent);

		if (generated) {
			LOGGER.info("Modpack generated!");
		} else {
			LOGGER.error("Failed to generate modpack!");
		}

		LOGGER.info("Starting server on port {}", serverConfig.bindPort);
		server.start();
		// wait for server to stop
		while (server.isRunning()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				LOGGER.error("Interrupted server thread", e);
			}
		}
	}
}
