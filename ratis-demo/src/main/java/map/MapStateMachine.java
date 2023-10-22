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
import java.util.concurrent.CompletableFuture;

public class MapStateMachine extends BaseStateMachine {

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
}
