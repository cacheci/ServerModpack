package pl.skidam.automodpack_core.utils;

import java.io.IOException;
import java.net.URI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class UriOpener {
	private static final Logger LOGGER = LogManager.getLogger(UriOpener.class);

	private UriOpener() {}

	public static void openUri(String uri) {
		try {
			URI location = URI.create(uri);
			String[] command = PlatformUtils.IS_WIN ? new String[]{"rundll32", "url.dll,FileProtocolHandler", location.toString()}
					: PlatformUtils.IS_MAC ? new String[]{"open", location.toString()} : new String[]{"xdg-open", location.toString()};
			Process process = Runtime.getRuntime().exec(command);
			process.getInputStream().close();
			process.getErrorStream().close();
			process.getOutputStream().close();
		} catch (IllegalArgumentException | IOException e) {
			LOGGER.error("Couldn't open uri '{}'", uri, e);
		}
	}
}
