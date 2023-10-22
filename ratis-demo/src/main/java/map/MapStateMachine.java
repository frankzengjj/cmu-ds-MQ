package map;

import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.StateMachineStorage;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MapStateMachine extends BaseStateMachine {
    private Map<String, String> map = new ConcurrentHashMap<>();
    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();

    /**
     * Initialize the state machine storage and then load the state.
     *
     * @param server  the server running this state machine
     * @param groupId the id of the {@link org.apache.ratis.protocol.RaftGroup}
     * @param raftStorage the storage of the server
     * @throws IOException if it fails to load the state.
     */
    @Override
    public void initialize(RaftServer server, RaftGroupId groupId, RaftStorage raftStorage) throws IOException {
        super.initialize(server, groupId, raftStorage);
        storage.init(raftStorage);
    }

    @Override
    public StateMachineStorage getStateMachineStorage() {
        return storage;
    }

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        final RaftProtos.LogEntryProto entry = trx.getLogEntry();
        String command = entry.getStateMachineLogEntry().getLogData().toStringUtf8();
        System.out.println(command);
        if (command.startsWith("PUT") || command.startsWith("GET")) {
            String[] parts = command.trim().split("\\s+", 3);
            switch (parts[0].toUpperCase()) {
                case "PUT":
                    if (parts.length != 3) {
                        System.out.println("Invalid PUT syntax. Expected: PUT <KEY> <VALUE>");
                    }
                    String putKey = parts[1];
                    String value = parts[2];
                    return CompletableFuture.completedFuture(Message.valueOf("Put key: " + putKey + ", value: " + value));
                case "GET":
                    if (parts.length != 2) {
                        System.out.println("Invalid GET syntax. Expected: GET <KEY>");
                    }
                    String getKey = parts[1];
                    return CompletableFuture.completedFuture(Message.valueOf("Get key: " + getKey));
                default:
                    break;
            }
        }
        return super.applyTransaction(trx);
    }
}
