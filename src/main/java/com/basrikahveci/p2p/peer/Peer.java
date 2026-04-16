package com.basrikahveci.p2p.peer;

import com.basrikahveci.p2p.monitor.PeerRuntimeSnapshot;
import com.basrikahveci.p2p.monitor.PeerTelemetryPublisher;
import com.basrikahveci.p2p.monitor.PeerMonitorEventPublisher;
import com.basrikahveci.p2p.peer.network.Connection;
import com.basrikahveci.p2p.peer.network.message.file.FileTransfer;
import com.basrikahveci.p2p.peer.network.message.ping.CancelPongs;
import com.basrikahveci.p2p.peer.network.message.ping.Ping;
import com.basrikahveci.p2p.peer.network.message.ping.Pong;
import com.basrikahveci.p2p.peer.service.ConnectionService;
import com.basrikahveci.p2p.peer.service.LeadershipService;
import com.basrikahveci.p2p.peer.service.PingService;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.min;

public class Peer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Peer.class);

    public static final Random RANDOM = new Random();

    private final Config config;

    private final ConnectionService connectionService;

    private final PingService pingService;

    private final LeadershipService leadershipService;

    private Channel bindChannel;

    private boolean running = true;

    private final PeerTelemetryPublisher telemetryPublisher = new PeerTelemetryPublisher();

    private final PeerMonitorEventPublisher eventPublisher = new PeerMonitorEventPublisher();

    private final Path incomingFilesRoot = Paths.get("received");

    public Peer(Config config, ConnectionService connectionService, PingService pingService, LeadershipService leadershipService) {
        this.config = config;
        this.connectionService = connectionService;
        this.pingService = pingService;
        this.leadershipService = leadershipService;
    }

    public void handleConnectionOpened(Connection connection, String leaderName) {
        if (isShutdown()) {
            LOGGER.warn("New connection of {} ignored since not running", connection.getPeerName());
            return;
        }

        if (connection.getPeerName().equals(config.getPeerName())) {
            LOGGER.error("Can not connect to itself. Closing new connection.");
            connection.close();
            return;
        }

        connectionService.addConnection(connection);
        if (leaderName != null) {
            final String currentLeaderName = leadershipService.getLeaderName();
            if (currentLeaderName == null) {
                leadershipService.handleLeader(connection, leaderName);
            } else if (!leaderName.equals(currentLeaderName)) {
                LOGGER.info("Known leader {} and leader {} announced by {} are different.", currentLeaderName, leaderName, connection);
                leadershipService.scheduleElection();
            }
        }
        pingService.propagatePingsToNewConnection(connection);
    }

    public void handleConnectionClosed(Connection connection) {
        if (connection == null) {
            return;
        }

        final String connectionPeerName = connection.getPeerName();
        if (connectionPeerName == null || connectionPeerName.equals(config.getPeerName())) {
            return;
        }

        if (connectionService.removeConnection(connection)) {
            cancelPings(connection, connectionPeerName);
            cancelPongs(connectionPeerName);
        }

        if (connectionPeerName.equals(leadershipService.getLeaderName())) {
            LOGGER.warn("Starting an election since connection to current leader {} is closed.", connectionPeerName);
            leadershipService.scheduleElection();
        }
    }

    public void cancelPings(final Connection connection, final String removedPeerName) {
        if (running) {
            pingService.cancelPings(connection, removedPeerName);
        } else {
            LOGGER.warn("Pings of {} can't be cancelled since not running", removedPeerName);
        }
    }

    public void handlePing(Connection connection, Ping ping) {
        if (running) {
            pingService.handlePing((InetSocketAddress) bindChannel.localAddress(), connection, ping);
        } else {
            LOGGER.warn("Ping of {} is ignored since not running", connection.getPeerName());
        }
    }

    public void handlePong(Connection connection, Pong pong) {
        if (running) {
            pingService.handlePong(pong);
        } else {
            LOGGER.warn("Pong of {} is ignored since not running", connection.getPeerName());
        }
    }

    public void keepAlivePing() {
        if (isShutdown()) {
            LOGGER.warn("Periodic ping ignored since not running");
            return;
        }

        final int numberOfConnections = connectionService.getNumberOfConnections();
        if (numberOfConnections > 0) {
            final boolean discoveryPingEnabled = numberOfConnections < config.getMinNumberOfActiveConnections();
            pingService.keepAlive(discoveryPingEnabled);
        } else {
            LOGGER.debug("No auto ping since there is no connection");
        }
    }

    public void timeoutPings() {
        if (isShutdown()) {
            LOGGER.warn("Timeout pings ignored since not running");
            return;
        }

        final Collection<Pong> pongs = pingService.timeoutPings();
        final int availableConnectionSlots =
                config.getMinNumberOfActiveConnections() - connectionService.getNumberOfConnections();

        if (availableConnectionSlots > 0) {
            List<Pong> notConnectedPeers = new ArrayList<>();
            for (Pong pong : pongs) {
                if (!config.getPeerName().equals(pong.getPeerName()) && !connectionService
                        .isConnectedTo(pong.getPeerName())) {
                    notConnectedPeers.add(pong);
                }
            }

            Collections.shuffle(notConnectedPeers);
            for (int i = 0, j = min(availableConnectionSlots, notConnectedPeers.size()); i < j; i++) {
                final Pong peerToConnect = notConnectedPeers.get(i);
                final String host = peerToConnect.getServerHost();
                final int port = peerToConnect.getServerPort();
                LOGGER.info("Auto-connecting to {} via {}:{}", peerToConnect.getPeerName(), peerToConnect.getPeerName(), host,
                        port);
                connectTo(host, port, null);
            }
        }
    }

    public void cancelPongs(final String removedPeerName) {
        if (isShutdown()) {
            LOGGER.warn("Pongs of {} not cancelled since not running", removedPeerName);
            return;
        }

        pingService.cancelPongs(removedPeerName);
    }

    public void handleLeader(final Connection connection, String leaderName) {
        if (isShutdown()) {
            LOGGER.warn("Leader announcement of {} from connection {} ignored since not running", leaderName, connection.getPeerName());
            return;
        }

        leadershipService.handleLeader(connection, leaderName);
    }

    public void handleElection(final Connection connection) {
        if (isShutdown()) {
            LOGGER.warn("Election of {} ignored since not running", connection.getPeerName());
            return;
        }

        leadershipService.handleElection(connection);
    }

    public void handleRejection(final Connection connection) {
        if (isShutdown()) {
            LOGGER.warn("Rejection of {} ignored since not running", connection.getPeerName());
            return;

        }

        leadershipService.handleRejection(connection);
    }

    public void scheduleElection() {
        if (isShutdown()) {
            LOGGER.warn("Election not scheduled since not running");
            return;
        }

        leadershipService.scheduleElection();
    }

    public void disconnect(final String peerName) {
        if (isShutdown()) {
            LOGGER.warn("Not disconnected from {} since not running", peerName);
            return;
        }

        final Connection connection = connectionService.getConnection(peerName);
        if (connection != null) {
            LOGGER.info("Disconnecting this peer {} from {}", config.getPeerName(), peerName);
            connection.close();
        } else {
            LOGGER.warn("This peer {} is not connected to {}", config.getPeerName(), peerName);
        }
    }

    public String getLeaderName() {
        return leadershipService.getLeaderName();
    }

    public void setBindChannel(final Channel bindChannel) {
        this.bindChannel = bindChannel;
    }

    public void ping(final CompletableFuture<Collection<String>> futureToNotify) {
        if (isShutdown()) {
            futureToNotify.completeExceptionally(new RuntimeException("Disconnected!"));
            return;
        }

        pingService.ping(futureToNotify);
    }

    public void leave(final CompletableFuture<Void> futureToNotify) {
        if (isShutdown()) {
            LOGGER.warn("{} already shut down!", config.getPeerName());
            futureToNotify.complete(null);
            return;
        }

        bindChannel.closeFuture().addListener(future -> {
            if (future.isSuccess()) {
                futureToNotify.complete(null);
            } else {
                futureToNotify.completeExceptionally(future.cause());
            }
        });

        pingService.cancelOwnPing();
        pingService.cancelPongs(config.getPeerName());
        final CancelPongs cancelPongs = new CancelPongs(config.getPeerName());
        for (Connection connection : connectionService.getConnections()) {
            connection.send(cancelPongs);
            connection.close();
        }
        bindChannel.close();
        running = false;
        telemetryPublisher.close();
        eventPublisher.close();
    }

    public void connectTo(final String host, final int port, final CompletableFuture<Void> futureToNotify) {
        if (running) {
            connectionService.connectTo(this, host, port, futureToNotify);
        } else {
            futureToNotify.completeExceptionally(new RuntimeException("Server is not running"));
        }
    }

    private boolean isShutdown() {
        return !running;
    }

    public void publishTelemetry() {
        if (isShutdown()) {
            return;
        }

        final int bindPort = bindChannel == null ? -1 : ((InetSocketAddress) bindChannel.localAddress()).getPort();
        final String leaderName = leadershipService.getLeaderName();
        final boolean isLeader = config.getPeerName().equals(leaderName);

        final PeerRuntimeSnapshot snapshot = new PeerRuntimeSnapshot(
                config.getPeerName(),
                bindPort,
                connectionService.getConnectedPeerNames(),
                leaderName,
                isLeader,
                leadershipService.isElectionInProgress(),
                pingService.getCurrentPingCount(),
                System.currentTimeMillis()
        );
        telemetryPublisher.publish(snapshot);
    }

    public void sendFile(final String targetPeerName, final String fileName, final byte[] content,
                         final CompletableFuture<Void> futureToNotify) {
        if (isShutdown()) {
            futureToNotify.completeExceptionally(new RuntimeException("Disconnected!"));
            return;
        }

        final Connection connection = connectionService.getConnection(targetPeerName);
        if (connection == null) {
            futureToNotify.completeExceptionally(
                    new IllegalStateException("No direct connection to peer " + targetPeerName));
            return;
        }

        connection.send(new FileTransfer(config.getPeerName(), fileName, content));
        LOGGER.info("File {} ({} bytes) sent to {}", fileName, content.length, targetPeerName);
        eventPublisher.publish(config.getPeerName(), "INFO",
            "File sent to " + targetPeerName + ": " + fileName + " (" + content.length + " bytes)");
        futureToNotify.complete(null);
    }

    public void handleIncomingFile(final String senderPeerName, final String fileName, final byte[] content) {
        if (isShutdown()) {
            LOGGER.warn("Incoming file {} from {} ignored since peer is shutting down", fileName, senderPeerName);
            return;
        }

        try {
            final Path peerDir = incomingFilesRoot.resolve(config.getPeerName());
            Files.createDirectories(peerDir);
            final String sanitizedName = sanitizeFileName(fileName);
            final Path outputPath = peerDir.resolve(System.currentTimeMillis() + "_from_" + senderPeerName + "_" + sanitizedName);
            Files.write(outputPath, content);
            LOGGER.info("Received file {} ({} bytes) from {}. Stored at {}", fileName, content.length, senderPeerName, outputPath);
                eventPublisher.publish(config.getPeerName(), "INFO",
                    "File received from " + senderPeerName + ": " + fileName + " -> " + outputPath.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to persist incoming file {} from {}", fileName, senderPeerName, e);
                eventPublisher.publish(config.getPeerName(), "ERROR",
                    "Failed to save file from " + senderPeerName + ": " + fileName + " (" + e.getMessage() + ")");
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

}
