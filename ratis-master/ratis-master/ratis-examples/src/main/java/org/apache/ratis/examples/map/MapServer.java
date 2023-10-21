//package org.apache.ratis.examples.map;
//
//import org.apache.ratis.conf.RaftProperties;
//import org.apache.ratis.examples.common.Constants;
//import org.apache.ratis.examples.counter.server.CounterServer;
//import org.apache.ratis.grpc.GrpcConfigKeys;
//import org.apache.ratis.protocol.RaftGroup;
//import org.apache.ratis.protocol.RaftGroupId;
//import org.apache.ratis.protocol.RaftPeer;
//import org.apache.ratis.protocol.RaftPeerId;
//import org.apache.ratis.server.RaftServer;
//import org.apache.ratis.server.RaftServerConfigKeys;
//import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
//import org.apache.ratis.util.LifeCycle;
//import org.apache.ratis.util.NetUtils;
//
//import java.io.Closeable;
//import java.io.File;
//import java.io.IOException;
//import java.util.*;
//import java.util.concurrent.TimeUnit;
//
//import static java.nio.charset.StandardCharsets.UTF_8;
//
//public class MapServer implements Closeable {
//
//    private final RaftServer server;
//    private RaftProperties properties = new RaftProperties();
//
//    private static final UUID GROUP_ID = UUID.fromString("02511d47-d67c-49a3-9011-abb3109a44c2");
//
//    public MapServer(RaftPeer peer, RaftGroup group, File storageDir) throws IOException {
//
//        final int port = NetUtils.createSocketAddr(peer.getAddress()).getPort();
//        GrpcConfigKeys.Server.setPort(properties, port);
//
//        Optional.ofNullable(peer.getClientAddress()).ifPresent(address ->
//                GrpcConfigKeys.Client.setPort(properties, NetUtils.createSocketAddr(address).getPort()));
//        Optional.ofNullable(peer.getAdminAddress()).ifPresent(address ->
//                GrpcConfigKeys.Admin.setPort(properties, NetUtils.createSocketAddr(address).getPort()));
//        RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(storageDir));
//
//        this.server = RaftServer.newBuilder()
//                .setServerId(peer.getId())
//                .setStateMachine(new MapStateMachine()) // Your implemented StateMachine
//                .setProperties(properties)
//                .setGroup(group)
//                .build();
//    }
//
//    public void start() throws IOException {
//        server.start();
//    }
//
//    @Override
//    public void close() throws IOException {
//        server.close();
//    }
//
//
//    public static void main(String[] args) {
//        String id = args[0];
//        String peerAddrString = args[1];
//        File storageDir = new File("/tmp/cmu-ratis/server" + id);
//        String[] peerAddrs = peerAddrString.split(",");
//        RaftPeer[] peers = new RaftPeer[peerAddrs.length];
//        for (int i=0; i<peerAddrs.length; i++) {
//            peers[i] = RaftPeer
//                    .newBuilder()
//                    .setId(i + "")
//                    .setAddress(peerAddrs[i])
//                    .build();
//        }
//
//
//
//        RaftGroup group = RaftGroup.valueOf(RaftGroupId.valueOf(GROUP_ID), peers);
//        try(MapServer mapServer = new MapServer(peers[Integer.parseInt(id)], group, storageDir)) {
//            mapServer.start();
//
//            for(; mapServer.server.getLifeCycleState() != LifeCycle.State.CLOSED;) {
//                TimeUnit.SECONDS.sleep(1);
//            }
//
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
