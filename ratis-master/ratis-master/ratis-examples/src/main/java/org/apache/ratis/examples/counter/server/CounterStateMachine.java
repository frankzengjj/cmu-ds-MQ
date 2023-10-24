/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.examples.counter.server;

import org.apache.ratis.examples.core.*;
import org.apache.ratis.examples.core.index.ConsumerIndex;
import org.apache.ratis.examples.core.index.PartitionConsumerIndex;
import org.apache.ratis.examples.counter.CounterCommand;
import org.apache.ratis.io.MD5Hash;
import org.apache.ratis.proto.RaftProtos.LogEntryProto;
import org.apache.ratis.proto.RaftProtos.RaftPeerRole;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.raftlog.RaftLog;
import org.apache.ratis.server.storage.FileInfo;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
import org.apache.ratis.statemachine.impl.SingleFileSnapshotInfo;
import org.apache.ratis.util.JavaUtils;
import org.apache.ratis.util.MD5FileUtil;
import org.apache.ratis.util.TimeDuration;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link org.apache.ratis.statemachine.StateMachine} implementation for the {@link CounterServer}.
 * This class maintain a {@link AtomicInteger} object as a state and accept two commands:
 * <p>
 * - {@link CounterCommand#GET} is a readonly command
 *   which is handled by the {@link #query(Message)} method.
 * <p>
 * - {@link CounterCommand#INCREMENT} is a transactional command
 *   which is handled by the {@link #applyTransaction(TransactionContext)} method.
 */
public class CounterStateMachine extends BaseStateMachine {
  /** The state of the {@link CounterStateMachine}. */
  static class CounterState {
    private final TermIndex applied;
    private final int counter;
    private Broker broker;

    CounterState(TermIndex applied, int counter, Broker broker) {
      this.applied = applied;
      this.counter = counter;
      this.broker = broker;
    }

    TermIndex getApplied() {
      return applied;
    }

    int getCounter() {
      return counter;
    }

    Broker getBroker() {
      return broker;
    }
  }

  private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();
  private final AtomicInteger counter = new AtomicInteger(0);

  private Broker broker = new Broker();

  private final TimeDuration simulatedSlowness;

  private MyMessage myMessage = new MyMessage();

  //private ConsumerIndex consumerIndex = new ConsumerIndex();

  CounterStateMachine(TimeDuration simulatedSlowness) {
    this.simulatedSlowness = simulatedSlowness;
  }
  CounterStateMachine() {
    this.simulatedSlowness = TimeDuration.ZERO;
  }

  /** @return the current state. */
  private synchronized CounterState getState() {
    return new CounterState(getLastAppliedTermIndex(), counter.get(), broker);
  }

  private synchronized void updateState(TermIndex applied, int counterValue,Broker broker) {
    updateLastAppliedTermIndex(applied);
    counter.set(counterValue);
    this.broker = broker;
  }

  private synchronized String incrementIndex(TermIndex termIndex,String command){
    try {
      if (!simulatedSlowness.equals(TimeDuration.ZERO)) {
        simulatedSlowness.sleep();
      }
    } catch (InterruptedException e) {
      LOG.warn("{}: get interrupted in simulated slowness sleep before apply transaction", this);
      Thread.currentThread().interrupt();
    }
    updateLastAppliedTermIndex(termIndex);


    System.out.println("query: " + command);

    String topic = command.split(" ")[1];
    int partition = Integer.parseInt(command.split(" ")[2]);
    String consumerGroupId = command.split(" ")[3];
    System.out.println(broker);
    System.out.println(broker.getConsumerIndex());

    //if broker does not have this topic
    if(!broker.getTopPartitionListMap().containsKey(topic)){
      return "No such topic";
    }
    //if broker does not have this partition
    if(partition >= broker.getTopPartitionListMap().get(topic).size()){
      return "No such partition";
    }

    if(!broker.getConsumerIndex().getTopicConsumerIndexMap().containsKey(topic)){
      ArrayList<PartitionConsumerIndex> partitionConsumerIndexList = new ArrayList<PartitionConsumerIndex>();
      PartitionConsumerIndex partitionConsumerIndex = new PartitionConsumerIndex();
      partitionConsumerIndex.setTopic(topic);
      partitionConsumerIndexList.add(partitionConsumerIndex);
      broker.getConsumerIndex().getTopicConsumerIndexMap().put(topic,partitionConsumerIndexList);
    }
    if (partition >= broker.getConsumerIndex().getTopicConsumerIndexMap().get(topic).size()) {
      for(int i = broker.getConsumerIndex().getTopicConsumerIndexMap().get(topic).size(); i <= partition; i++){
        PartitionConsumerIndex partitionConsumerIndex = new PartitionConsumerIndex();
        partitionConsumerIndex.setTopic(topic);
        broker.getConsumerIndex().getTopicConsumerIndexMap().get(topic).add(partitionConsumerIndex);
      }
    }

    int currentIndex = 0;
    HashMap<String, Integer> partitionConsumerIndexMap = broker.getConsumerIndex().getTopicConsumerIndexMap().get(topic).get(partition).getPartitionConsumerIndexMap();
    if(!partitionConsumerIndexMap.containsKey(consumerGroupId)){
      partitionConsumerIndexMap.put(consumerGroupId,0);
    }else{
        currentIndex = partitionConsumerIndexMap.get(consumerGroupId);
    }
    if(currentIndex >= broker.getTopPartitionListMap().get(topic).get(partition).getMessageList().size()){
      return "No more message";
    }
    partitionConsumerIndexMap.put(consumerGroupId,currentIndex+1);
    return broker.getTopPartitionListMap().get(topic).get(partition).getMessageList().get(currentIndex).getContent();

  }

  private synchronized int incrementCounter(TermIndex termIndex,MyMessage myMessage){
    try {
      if (!simulatedSlowness.equals(TimeDuration.ZERO)) {
        simulatedSlowness.sleep();
      }
    } catch (InterruptedException e) {
      LOG.warn("{}: get interrupted in simulated slowness sleep before apply transaction", this);
      Thread.currentThread().interrupt();
    }
    updateLastAppliedTermIndex(termIndex);

    //TODO default partition index = 0;
    //1. Build partition
    int partitionIndex = 0;
    if(broker.getTopPartitionListMap().containsKey(myMessage.getTopic())) {
      if (broker.getTopPartitionListMap().get(myMessage.getTopic()).size() < partitionIndex) {
        for (int i = broker.getTopPartitionListMap().get(myMessage.getTopic()).size(); i < partitionIndex; i++) {
          broker.getTopPartitionListMap().get(myMessage.getTopic()).add(new Partition());
        }
      }
    }else {
      List<Partition> partitionList = new ArrayList<Partition>();
      for (int i = 0; i <= partitionIndex; i++) {
        Partition partition = new Partition();
        partition.setTopic(myMessage.getTopic());
        partitionList.add(partition);

      }
      broker.getTopPartitionListMap().put(myMessage.getTopic(), partitionList);
    }

    //2. Fill message into partition
    if(myMessage.getTopic()!=null) {
      broker.getTopPartitionListMap().get(myMessage.getTopic()).get(partitionIndex).getMessageList().add(
            MyMessage.builder().topic(myMessage.getTopic()).content(myMessage.getContent()).build()
      );
    }

    System.out.println(broker.getTopPartitionListMap());
    return counter.incrementAndGet();
  }

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
    reinitialize();
  }

  /**
   * Simply load the latest snapshot.
   *
   * @throws IOException if it fails to load the state.
   */
  @Override
  public void reinitialize() throws IOException {
    load(storage.getLatestSnapshot());
  }

  /**
   * Store the current state as a snapshot file in the {@link #storage}.
   *
   * @return the index of the snapshot
   */
  @Override
  public long takeSnapshot() {
    //get the current state
    final CounterState state = getState();
    final long index = state.getApplied().getIndex();

    //create a file with a proper name to store the snapshot
    final File snapshotFile = storage.getSnapshotFile(state.getApplied().getTerm(), index);

    //write the counter value into the snapshot file
    try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(
        Files.newOutputStream(snapshotFile.toPath())))) {
      out.writeInt(state.getCounter());
      out.writeObject(state.getBroker());
    } catch (IOException ioe) {
      LOG.warn("Failed to write snapshot file \"" + snapshotFile
          + "\", last applied index=" + state.getApplied());
    }

    // update storage
    final MD5Hash md5 = MD5FileUtil.computeAndSaveMd5ForFile(snapshotFile);
    final FileInfo info = new FileInfo(snapshotFile.toPath(), md5);
    storage.updateLatestSnapshot(new SingleFileSnapshotInfo(info, state.getApplied()));

    //return the index of the stored snapshot (which is the last applied one)
    return index;
  }

  /**
   * Load the state of the state machine from the {@link #storage}.
   *
   * @param snapshot the information of the snapshot being loaded
   * @return the index of the snapshot or -1 if snapshot is invalid
   * @throws IOException if it failed to read from storage
   */
  private long load(SingleFileSnapshotInfo snapshot) throws IOException {
    //check null
    if (snapshot == null) {
      return RaftLog.INVALID_LOG_INDEX;
    }
    //check if the snapshot file exists.
    final Path snapshotPath = snapshot.getFile().getPath();
    if (!Files.exists(snapshotPath)) {
      LOG.warn("The snapshot file {} does not exist for snapshot {}", snapshotPath, snapshot);
      return RaftLog.INVALID_LOG_INDEX;
    }

    // verify md5
    final MD5Hash md5 = snapshot.getFile().getFileDigest();
    if (md5 != null) {
      MD5FileUtil.verifySavedMD5(snapshotPath.toFile(), md5);
    }

    //read the TermIndex from the snapshot file name
    final TermIndex last = SimpleStateMachineStorage.getTermIndexFromSnapshotFile(snapshotPath.toFile());

    //read the counter value from the snapshot file
    final int counterValue;
    Broker broker;
    try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(snapshotPath)))) {
      counterValue = in.readInt();
      broker = (Broker) in.readObject();
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    //update state
    updateState(last, counterValue, broker);

    return last.getIndex();
  }

  /**
   * Process {@link CounterCommand#GET}, which gets the counter value.
   *
   * @param request the GET request
   * @return a {@link Message} containing the current counter value as a {@link String}.
   */
  @Override
  public CompletableFuture<Message> query(Message request) {
    //TODO handle request
    final String command = request.getContent().toStringUtf8();

    System.out.println("query!: " + command);

    //String topic = command.split(" ")[0];
    //int partition = Integer.parseInt(command.split(" ")[1]);
    //String consumerGroupId = command.split(" ")[2];
    System.out.println(broker);
    System.out.println(broker.getConsumerIndex());

    //if broker does not have this topic
    //if(!broker.getTopPartitionListMap().containsKey(topic)){
    //  return CompletableFuture.completedFuture(Message.valueOf("No such topic"));
    //}
    ////if broker does not have this partition
    //if(partition >= broker.getTopPartitionListMap().get(topic).size()){
    //  return CompletableFuture.completedFuture(Message.valueOf("No such partition"));
    //}
    //
    //if(!broker.getConsumerIndex().getTopicConsumerIndexMap().containsKey(topic)){
    //  ArrayList<PartitionConsumerIndex> partitionConsumerIndexList = new ArrayList<PartitionConsumerIndex>();
    //  PartitionConsumerIndex partitionConsumerIndex = new PartitionConsumerIndex();
    //  partitionConsumerIndex.setTopic(topic);
    //  partitionConsumerIndexList.add(partitionConsumerIndex);
    //  broker.getConsumerIndex().getTopicConsumerIndexMap().put(topic,partitionConsumerIndexList);
    //}
    //if (partition >= broker.getConsumerIndex().getTopicConsumerIndexMap().get(topic).size()) {
    //  for(int i = broker.getConsumerIndex().getTopicConsumerIndexMap().get(topic).size(); i <= partition; i++){
    //    PartitionConsumerIndex partitionConsumerIndex = new PartitionConsumerIndex();
    //    partitionConsumerIndex.setTopic(topic);
    //    broker.getConsumerIndex().getTopicConsumerIndexMap().get(topic).add(partitionConsumerIndex);
    //  }
    //}
    //
    //int currentIndex = 0;
    //HashMap<String, Integer> partitionConsumerIndexMap = broker.getConsumerIndex().getTopicConsumerIndexMap().get(topic).get(partition).getPartitionConsumerIndexMap();
    //if(!partitionConsumerIndexMap.containsKey(consumerGroupId)){
    //  partitionConsumerIndexMap.put(consumerGroupId,0);
    //}else{
    //    currentIndex = partitionConsumerIndexMap.get(consumerGroupId);
    //}
    //if(currentIndex >= broker.getTopPartitionListMap().get(topic).get(partition).getMessageList().size()){
    //  return CompletableFuture.completedFuture(Message.valueOf("No more message"));
    //}
    //partitionConsumerIndexMap.put(consumerGroupId,currentIndex+1);
    //
    //incrementIndex(getLastAppliedTermIndex(),broker);
    //
    //return CompletableFuture.completedFuture(Message.valueOf(broker.getTopPartitionListMap().get(topic).get(partition).getMessageList().get(currentIndex).getContent()));



    //if (!CounterCommand.GET.matches(command)) {
    //  return JavaUtils.completeExceptionally(new IllegalArgumentException("Invalid Command: " + command));
    //}

    //return CompletableFuture.completedFuture(Message.valueOf(counter.toString() + " index: " + partitionIndex +" " + broker.toString()));
    return CompletableFuture.completedFuture(Message.valueOf("query" + broker.toString()));
  }

  /**
   * Apply the {@link CounterCommand#INCREMENT} by incrementing the counter object.
   *
   * @param trx the transaction context
   * @return the message containing the updated counter value
   */
  @Override
  public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
    final LogEntryProto entry = trx.getLogEntry();

    //check if the command is valid
    final String command = entry.getStateMachineLogEntry().getLogData().toStringUtf8();
    //TODO Handle request
    System.out.println("applyTransaction: " + command);
    String topic = command.split(" ")[0];
    String content = command.split(" ")[1];




    myMessage.setTopic(topic);
    myMessage.setContent(content);

    //if (!CounterCommand.INCREMENT.matches(command)) {
    //  return JavaUtils.completeExceptionally(new IllegalArgumentException("Invalid Command: " + command));
    //}
    //increment the counter and update term-index
    final TermIndex termIndex = TermIndex.valueOf(entry);
    String incremented = "";
    if(!topic.equals("query")) {
      incremented = Integer.toString(incrementCounter(termIndex, myMessage));
    }else{
      System.out.println("hhhhhh");
      incremented = incrementIndex(termIndex,command);
    }

    //if leader, log the incremented value and the term-index
    if (trx.getServerRole() == RaftPeerRole.LEADER) {
      LOG.info("{}: Increment to {}", termIndex, incremented);
    }

    //return the new value of the counter to the client
    return CompletableFuture.completedFuture(Message.valueOf(String.valueOf(incremented)));
  }
}
