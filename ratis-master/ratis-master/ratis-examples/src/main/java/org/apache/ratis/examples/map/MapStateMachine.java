//package org.apache.ratis.examples.map;
//
//import org.apache.ratis.io.MD5Hash;
//import org.apache.ratis.proto.RaftProtos.RaftPeerRole;
//import org.apache.ratis.proto.RaftProtos.LogEntryProto;
//import org.apache.ratis.protocol.Message;
//import org.apache.ratis.protocol.RaftGroupId;
//import org.apache.ratis.server.RaftServer;
//import org.apache.ratis.server.protocol.TermIndex;
//import org.apache.ratis.server.raftlog.RaftLog;
//import org.apache.ratis.server.storage.FileInfo;
//import org.apache.ratis.server.storage.RaftStorage;
//import org.apache.ratis.statemachine.StateMachineStorage;
//import org.apache.ratis.statemachine.TransactionContext;
//import org.apache.ratis.statemachine.impl.BaseStateMachine;
//import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
//import org.apache.ratis.statemachine.impl.SingleFileSnapshotInfo;
//import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
//import org.apache.ratis.util.AutoCloseableLock;
//import org.apache.ratis.util.JavaUtils;
//import org.apache.ratis.util.MD5FileUtil;
//
//import org.apache.ratis.examples.map.MapRequestProtos.MapRequest;
//import org.apache.ratis.examples.map.MapRequestProtos.*;
//
//import java.io.*;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.locks.ReentrantReadWriteLock;
//
//public class MapStateMachine extends BaseStateMachine {
//
//    private final Map<String, String> variables = new ConcurrentHashMap<>();
//
//    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();
//
//    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
//
//    private AutoCloseableLock readLock() {
//        return AutoCloseableLock.acquire(lock.readLock());
//    }
//
//    private AutoCloseableLock writeLock() {
//        return AutoCloseableLock.acquire(lock.writeLock());
//    }
//
//    void reset() {
//        variables.clear();
//        setLastAppliedTermIndex(null);
//    }
//
//    @Override
//    public void initialize(RaftServer server, RaftGroupId groupId,
//                           RaftStorage raftStorage) throws IOException {
//        super.initialize(server, groupId, raftStorage);
//        this.storage.init(raftStorage);
//        loadSnapshot(storage.getLatestSnapshot());
//    }
//
//    @Override
//    public void reinitialize() throws IOException {
//        close();
//        loadSnapshot(storage.getLatestSnapshot());
//    }
//
//    @Override
//    public long takeSnapshot() {
//        final Map<String, String> copy;
//        final TermIndex last;
//        try(AutoCloseableLock readLock = readLock()) {
//            copy = new HashMap<>(variables);
//            last = getLastAppliedTermIndex();
//        }
//
//        final File snapshotFile =  storage.getSnapshotFile(last.getTerm(), last.getIndex());
//        LOG.info("Taking a snapshot to file {}", snapshotFile);
//
//        try(ObjectOutputStream out = new ObjectOutputStream(
//                new BufferedOutputStream(new FileOutputStream(snapshotFile)))) {
//            out.writeObject(copy);
//        } catch(IOException ioe) {
//            LOG.warn("Failed to write snapshot file \"" + snapshotFile
//                    + "\", last applied index=" + last);
//        }
//
//        final MD5Hash md5 = MD5FileUtil.computeAndSaveMd5ForFile(snapshotFile);
//        final FileInfo info = new FileInfo(snapshotFile.toPath(), md5);
//        storage.updateLatestSnapshot(new SingleFileSnapshotInfo(info, last));
//        return last.getIndex();
//    }
//
//    public long loadSnapshot(SingleFileSnapshotInfo snapshot) throws IOException {
//        if (snapshot == null) {
//            LOG.warn("The snapshot info is null.");
//            return RaftLog.INVALID_LOG_INDEX;
//        }
//        final File snapshotFile = snapshot.getFile().getPath().toFile();
//        if (!snapshotFile.exists()) {
//            LOG.warn("The snapshot file {} does not exist for snapshot {}", snapshotFile, snapshot);
//            return RaftLog.INVALID_LOG_INDEX;
//        }
//
//        // verify md5
//        final MD5Hash md5 = snapshot.getFile().getFileDigest();
//        if (md5 != null) {
//            MD5FileUtil.verifySavedMD5(snapshotFile, md5);
//        }
//
//        final TermIndex last = SimpleStateMachineStorage.getTermIndexFromSnapshotFile(snapshotFile);
//        try(AutoCloseableLock writeLock = writeLock();
//            ObjectInputStream in = new ObjectInputStream(
//                    new BufferedInputStream(new FileInputStream(snapshotFile)))) {
//            reset();
//            setLastAppliedTermIndex(last);
//            variables.putAll(JavaUtils.cast(in.readObject()));
//        } catch (ClassNotFoundException e) {
//            throw new IllegalStateException("Failed to load " + snapshot, e);
//        }
//        return last.getIndex();
//    }
//
//    @Override
//    public StateMachineStorage getStateMachineStorage() {
//        return storage;
//    }
//
//    @Override
//    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
//        final LogEntryProto entry = trx.getLogEntry();
//        MapRequestProtos.MapRequest request = getRequest(entry);
//        final long index = entry.getIndex();
//        String key = null;
//        String value = null;
//        try(AutoCloseableLock writeLock = writeLock()) {
//            switch (request.getRequestCase()) {
//                //TODO handle requests
//                case PUT_REQUEST:
//                    return CompletableFuture.completedFuture(applyPut(request.getPutRequest()));
//                case GET_REQUEST:
//                    return CompletableFuture.completedFuture(applyGet(request.getGetRequest()));
//                default:
//                    throw new IllegalStateException("Unknown request type: " + request.getRequestCase());
//            }
//        }
//    }
//
//
//    static MapRequest getRequest(LogEntryProto entry) {
//        try {
//            return MapRequest.parseFrom(entry.getStateMachineLogEntry().getLogData().toByteArray());
//        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private Message applyPut(PutRequest putRequest) {
//        variables.put(putRequest.getKey(), putRequest.getValue());
//        PutResponse response = PutResponse.newBuilder()
//                .setSuccess(true)
//                .build();
//        return Message.valueOf(ByteString.copyFrom(response.toByteArray()));
//    }
//
//    private Message applyGet(GetRequest getRequest) {
//        String value = variables.get(getRequest.getKey());
//        GetResponse response = GetResponse.newBuilder()
//                .setValue(value != null ? value : "")
//                .build();
//        return Message.valueOf(ByteString.copyFrom(response.toByteArray()));
//    }
//
//}
