package map;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.statemachine.StateMachine;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.apache.ratis.util.LifeCycle;
import org.apache.ratis.util.NetUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class MapServer {

    public static String id;
    public static String peers;
    public static File storageDir;
    public static final String raftGroupId = "demoRaftGroup123";

    public static void main(String[] args) throws IOException, InterruptedException {
        id = args[0];
        storageDir = new File(args[1]);
        peers = args[2];


        RaftPeerId peerId = RaftPeerId.valueOf(id);
        RaftProperties properties = new RaftProperties();

        final int port = NetUtils.createSocketAddr(getPeer(peerId).getAddress()).getPort();
        GrpcConfigKeys.Server.setPort(properties, port);

        Optional.ofNullable(getPeer(peerId).getClientAddress()).ifPresent(address ->
                GrpcConfigKeys.Client.setPort(properties, NetUtils.createSocketAddr(address).getPort()));
        Optional.ofNullable(getPeer(peerId).getAdminAddress()).ifPresent(address ->
                GrpcConfigKeys.Admin.setPort(properties, NetUtils.createSocketAddr(address).getPort()));

        RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(storageDir));
        StateMachine stateMachine = new MapStateMachine();

        final RaftGroup raftGroup = RaftGroup.valueOf(RaftGroupId.valueOf(ByteString.copyFromUtf8(raftGroupId)),
                getPeers());
        RaftServer raftServer = RaftServer.newBuilder()
                .setServerId(RaftPeerId.valueOf(id))
                .setStateMachine(stateMachine).setProperties(properties)
                .setGroup(raftGroup)
                .build();
        raftServer.start();

        for(; raftServer.getLifeCycleState() != LifeCycle.State.CLOSED;) {
            TimeUnit.SECONDS.sleep(1);
        }
    }

    public static RaftPeer getPeer(RaftPeerId raftPeerId) {
        Objects.requireNonNull(raftPeerId, "raftPeerId == null");
        RaftPeer[] var2 = getPeers();
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            RaftPeer p = var2[var4];
            if (raftPeerId.equals(p.getId())) {
                return p;
            }
        }

        throw new IllegalArgumentException("Raft peer id " + raftPeerId + " is not part of the raft group definitions " + peers);
    }

    public static RaftPeer[] getPeers() {
        return parsePeers(peers);
    }

    public static RaftPeer[] parsePeers(String peers) {
        return (RaftPeer[]) Stream.of(peers.split(",")).map((address) -> {
            String[] addressParts = address.split(":");
            if (addressParts.length < 3) {
                throw new IllegalArgumentException("Raft peer " + address + " is not a legitimate format. (format: name:host:port:dataStreamPort:clientPort:adminPort)");
            } else {
                RaftPeer.Builder builder = RaftPeer.newBuilder();
                builder.setId(addressParts[0]).setAddress(addressParts[1] + ":" + addressParts[2]);
                if (addressParts.length >= 4) {
                    builder.setDataStreamAddress(addressParts[1] + ":" + addressParts[3]);
                    if (addressParts.length >= 5) {
                        builder.setClientAddress(addressParts[1] + ":" + addressParts[4]);
                        if (addressParts.length >= 6) {
                            builder.setAdminAddress(addressParts[1] + ":" + addressParts[5]);
                        }
                    }
                }

                return builder.build();
            }
        }).toArray((x$0) -> {
            return new RaftPeer[x$0];
        });
    }
}
