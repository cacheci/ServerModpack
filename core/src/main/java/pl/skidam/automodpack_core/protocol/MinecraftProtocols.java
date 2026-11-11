package pl.skidam.automodpack_core.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonParser;

/** Vanilla protocol numbers used by the Holepunch login handshake. */
public final class MinecraftProtocols {
	private static final String ENTRY = "mc-protocols.json";
	private static volatile Map<String, Integer> table;

	private MinecraftProtocols() {}

	public static int forVersion(String minecraftVersion) {
		Map<String, Integer> protocols = load();
		String version = minecraftVersion;
		int suffix = version.indexOf('-');
		if (suffix >= 0) version = version.substring(0, suffix);
		Integer protocol = protocols.get(version);
		while (protocol == null && version.indexOf('.') >= 0) {
			version = version.substring(0, version.lastIndexOf('.'));
			protocol = protocols.get(version);
		}
		if (protocol == null) {
			throw new IllegalArgumentException("Unsupported Minecraft version: " + minecraftVersion + "; shipped: " + String.join(", ", protocols.keySet()));
		}
		return protocol;
	}

	private static Map<String, Integer> load() {
		Map<String, Integer> cached = table;
		if (cached != null) return cached;
		synchronized (MinecraftProtocols.class) {
			if (table == null) table = readFromResource();
			return table;
		}
	}

	/** A failed read stays uncached so a retried handshake can succeed once the jar is readable. */
	private static Map<String, Integer> readFromResource() {
		try (InputStream input = MinecraftProtocols.class.getResourceAsStream("/" + ENTRY)) {
			if (input == null) throw new IllegalStateException("Missing protocol table " + ENTRY);
			Map<String, Integer> protocols = new TreeMap<>();
			for (var field : JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) {
				String version = field.getKey();
				protocols.put(version.startsWith("~") ? version.substring(1) : version, field.getValue().getAsInt());
			}
			return Collections.unmodifiableMap(protocols);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read " + ENTRY, e);
		}
	}
}
