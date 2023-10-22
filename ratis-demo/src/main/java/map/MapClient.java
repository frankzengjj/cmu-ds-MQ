package map;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.protocol.*;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

import java.io.IOException;
import java.util.Scanner;
import java.util.stream.Stream;

public class MapClient {
    public static final String raftGroupId = "demoRaftGroup123";
    public static String peers;

    public static void main(String[] args) {
        peers = args[0];
        RaftProperties raftProperties = new RaftProperties();

        final RaftGroup raftGroup = RaftGroup.valueOf(RaftGroupId.valueOf(ByteString.copyFromUtf8(raftGroupId)),
                getPeers());

        RaftClient.Builder builder =
                RaftClient.newBuilder().setProperties(raftProperties);
        builder.setRaftGroup(raftGroup);
        builder.setClientRpc(new GrpcFactory(new Parameters()).newRaftClientRpc(ClientId.randomId(), raftProperties));
        RaftClient client = builder.build();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("Enter command: ");
                String command = scanner.nextLine();

                if ("exit".equalsIgnoreCase(command)) {
                    break;
                }

                RaftClientReply reply = client.io().send(Message.valueOf(command));
                System.out.println(reply.getMessage().getContent().toStringUtf8());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String processCommand(String command) {
        // Here, handle the user's command. For demonstration, I'm just printing it.
        System.out.println("You entered: " + command);
        return command;
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
