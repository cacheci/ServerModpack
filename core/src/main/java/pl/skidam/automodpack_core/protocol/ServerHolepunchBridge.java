package pl.skidam.automodpack_core.protocol;

import static pl.skidam.automodpack_core.Constants.*;
import static pl.skidam.automodpack_core.protocol.NetUtils.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import pl.skidam.automodpack_core.protocol.compression.CompressionType;
import pl.skidam.automodpack_core.protocol.netty.NettyServer;
import pl.skidam.automodpack_core.protocol.netty.TrafficShaper;
import pl.skidam.automodpack_core.protocol.netty.handler.CompressionDecoder;
import pl.skidam.automodpack_core.protocol.netty.handler.CompressionEncoder;
import pl.skidam.automodpack_core.protocol.netty.handler.ConfigurationHandler;
import pl.skidam.automodpack_core.protocol.netty.handler.ErrorPrinter;
import pl.skidam.automodpack_core.protocol.netty.handler.ProtocolMessageDecoder;
import pl.skidam.automodpack_core.protocol.netty.handler.ServerMessageHandler;
import pl.skidam.mcholepunch.HolepunchConnection;
import pl.skidam.mcholepunch.server.netty.NettyChannelRegistry;

public final class ServerHolepunchBridge {
	private static final Set<Channel> channels = ConcurrentHashMap.newKeySet();
	private static NettyChannelRegistry.Registration registration;

	private ServerHolepunchBridge() {}

	public static synchronized void register(NettyServer server) {
		if (!serverConfig.modpackHost || serverConfig.connectionMode != ModpackConnectionMode.HOLEPUNCH || registration != null) return;
		registration = NettyChannelRegistry.register(maxPendingWriteBytes(), (channel, connection) -> install(server, channel, connection));
	}

	public static synchronized boolean isRegistered() {
		return registration != null;
	}

	public static synchronized void close() {
		if (registration != null) {
			registration.close();
			registration = null;
		}
		for (Channel channel : channels) channel.close();
		channels.clear();
	}

	private static void install(NettyServer server, Channel channel, HolepunchConnection connection) throws Exception {
		SocketAddress remoteAddress = channel.remoteAddress();
		channels.add(channel);
		channel.closeFuture().addListener(future -> channels.remove(channel));
		ChannelPipeline pipeline = channel.pipeline();
		pipeline.addLast("holepunch-camouflage-encoder", new CamouflageEncoder(connection));
		pipeline.addLast("holepunch-camouflage-decoder", new CamouflageDecoder(connection));
		pipeline.addLast("error-printer-first", new ErrorPrinter());
		pipeline.addLast("traffic-shaper", TrafficShaper.trafficShaper.getTrafficShapingHandler());
		if (server.getSslCtx() != null) {
			SslHandler tls = server.getSslCtx().newHandler(channel.alloc());
			pipeline.addLast("tls", tls);
			tls.handshakeFuture().addListener(future -> {
				if (future.isSuccess()) connection.commitTransportUpgrade().exceptionally(error -> {
					LOGGER.error("Holepunch transport upgrade failed for {}", remoteAddress, error);
					channel.close();
					return null;
				});
			});
		}
		channel.attr(NettyServer.REAL_REMOTE_ADDR).set(remoteAddress);
		channel.attr(NettyServer.PROTOCOL_VERSION).set(LATEST_SUPPORTED_PROTOCOL_VERSION);
		channel.attr(NettyServer.COMPRESSION_TYPE).set(CompressionType.ZSTD);
		channel.attr(NettyServer.CHUNK_SIZE).set(DEFAULT_CHUNK_SIZE);
		pipeline.addLast("configuration-handler", new ConfigurationHandler())
				.addLast("compression-encoder", new CompressionEncoder())
				.addLast("compression-decoder", new CompressionDecoder())
				.addLast("chunked-write", new ChunkedWriteHandler())
				.addLast("protocol-msg-decoder", new ProtocolMessageDecoder())
				.addLast("msg-handler", new ServerMessageHandler(server))
				.addLast("error-printer-last", new ErrorPrinter());
		LOGGER.debug("AutoModpack holepunch connection handed over: {}", remoteAddress);
	}

	/** Forwards decrypted transport-frame payloads before the handoff and decodes camouflaged TLS records after it. */
	static final class CamouflageDecoder extends ByteToMessageDecoder {
		private final HolepunchConnection connection;
		private final TlsRecordCamouflage.Pair camouflage;

		CamouflageDecoder(HolepunchConnection connection) throws Exception {
			this.connection = connection;
			this.camouflage = TlsRecordCamouflage.create(connection.transportSecret(), false);
		}

		@Override
		protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) throws IOException {
			if (!connection.isRaw()) {
				output.add(input.readRetainedSlice(input.readableBytes()));
				return;
			}
			ByteBuf decoded = context.alloc().ioBuffer(input.readableBytes() + camouflage.inbound().pendingRecordLength());
			ByteBuffer view = decoded.nioBuffer(0, decoded.capacity());
			try {
				camouflage.inbound().decode(readableView(input), view);
			} catch (IOException exception) {
				// Malformed input kills the connection, but the pipeline's error path cannot release this
				// method's local, so the buffer goes back first or every hostile frame leaks one.
				decoded.release();
				throw exception;
			}
			if (view.position() == 0) {
				decoded.release();
				return;
			}
			decoded.writerIndex(view.position());
			output.add(decoded);
		}
	}

	/** Passes handshake-era records through and encodes post-handoff records as camouflage frames the peer's pipeline decodes. */
	static final class CamouflageEncoder extends MessageToMessageEncoder<ByteBuf> {
		private final HolepunchConnection connection;
		private final TlsRecordCamouflage.Pair camouflage;

		CamouflageEncoder(HolepunchConnection connection) throws Exception {
			this.connection = connection;
			this.camouflage = TlsRecordCamouflage.create(connection.transportSecret(), false);
		}

		@Override
		protected void encode(ChannelHandlerContext context, ByteBuf message, List<Object> output) throws IOException {
			if (!connection.isRaw()) {
				output.add(message.retain());
				return;
			}
			// The SslHandler writes one TLS record per message, so one frame header of expansion
			// plus whatever partial record the encoder still holds is the whole bound.
			ByteBuf encoded = context.alloc().ioBuffer(message.readableBytes() + camouflage.outbound().pendingRecordLength() + TlsRecordCamouflage.FRAME_HEADER_LENGTH);
			ByteBuffer view = encoded.nioBuffer(0, encoded.capacity());
			try {
				camouflage.outbound().encode(readableView(message), view);
			} catch (IOException exception) {
				encoded.release();
				throw exception;
			}
			if (view.position() == 0) {
				encoded.release();
				return;
			}
			encoded.writerIndex(view.position());
			output.add(encoded);
		}
	}

	/** A readable view of the buffer for the camouflage codec, zero-copy when the buffer exposes one backing NIO buffer. */
	private static ByteBuffer readableView(ByteBuf buffer) {
		if (buffer.nioBufferCount() == 1) {
			ByteBuffer view = buffer.nioBuffer();
			buffer.skipBytes(buffer.readableBytes());
			return view;
		}
		byte[] bytes = new byte[buffer.readableBytes()];
		buffer.readBytes(bytes);
		return ByteBuffer.wrap(bytes);
	}

	/** Bounds pending output on a claimed Minecraft connection. */
	private static long maxPendingWriteBytes() {
		return 4L * 1024 * 1024 + TlsRecordCamouflage.MAX_RECORD_LENGTH + TlsRecordCamouflage.FRAME_HEADER_LENGTH;
	}
}
