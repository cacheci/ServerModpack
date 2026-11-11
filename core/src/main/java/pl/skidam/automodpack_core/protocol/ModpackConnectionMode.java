package pl.skidam.automodpack_core.protocol;

public enum ModpackConnectionMode {
	HOLEPUNCH,
	MAGIC_PACKET,
	DIRECT;

	public static ModpackConnectionMode defaultFor(String minecraftVersion, String loader) {
		if (minecraftVersion == null || minecraftVersion.isBlank()) return MAGIC_PACKET;
		return isHolepunchAvailable(minecraftVersion, loader) ? HOLEPUNCH : MAGIC_PACKET;
	}

	public static boolean isHolepunchAvailable(String minecraftVersion, String loader) {
		if (minecraftVersion == null || minecraftVersion.isBlank()) return false;
		try {
			MinecraftProtocols.forVersion(minecraftVersion);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
