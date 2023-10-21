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

package org.apache.ratis.examples.common;

import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.util.TimeDuration;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Constants across servers and clients
 */
public final class Constants {
  public static final List<RaftPeer> PEERS;
  public static final String PATH;
  public static final List<TimeDuration> SIMULATED_SLOWNESS;

  static {
    final Properties properties = new Properties();
    //C:\Users\DAZHA\Downloads\ratis-master\ratis-master\ratis-examples\src\main\resources
    final String conf = "D:/study/CMU/SPT/cmu-ds-MQ/ratis-master/ratis-master/ratis-examples/src/main/resources/conf.properties";
    try(InputStream inputStream = new FileInputStream(conf);
        Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(reader)) {
      properties.load(bufferedReader);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load " + conf, e);
    }
    Function<String, String[]> parseConfList = confKey ->
        Optional.ofNullable(properties.getProperty(confKey))
        .map(s -> s.split(","))
        .orElse(null);
    final String key = "raft.server.address.list";
    final String[] addresses = parseConfList.apply(key);
    if (addresses == null || addresses.length == 0) {
      throw new IllegalArgumentException("Failed to get " + key + " from " + conf);
    }

    final String priorityKey = "raft.server.priority.list";
    final String[] priorities = parseConfList.apply(priorityKey);
    if (priorities != null && priorities.length != addresses.length) {
      throw new IllegalArgumentException("priority should be assigned to each server in " + conf);
    }

    final String slownessKey = "raft.server.simulated-slowness.list";
    final String[] slowness = parseConfList.apply(slownessKey);
    if (slowness != null && slowness.length != addresses.length) {
      throw new IllegalArgumentException("simulated-slowness should be assigned to each server in" + conf);
    }
    SIMULATED_SLOWNESS = slowness == null ? null:
        Arrays.stream(slowness)
          .map(s -> TimeDuration.valueOf(s, TimeUnit.SECONDS))
          .collect(Collectors.toList());

    final String key1 = "raft.server.root.storage.path";
    final String path = properties.getProperty(key1);
    PATH = path == null ? "./ratis-examples/target" : path;
    final List<RaftPeer> peers = new ArrayList<>(addresses.length);
    for (int i = 0; i < addresses.length; i++) {
      final int priority = priorities == null ? 0 : Integer.parseInt(priorities[i]);
      peers.add(RaftPeer.newBuilder().setId("n" + i).setAddress(addresses[i]).setPriority(priority).build());
    }
    PEERS = Collections.unmodifiableList(peers);
  }

  private static final UUID GROUP_ID = UUID.fromString("02511d47-d67c-49a3-9011-abb3109a44c1");

  public static final RaftGroup RAFT_GROUP = RaftGroup.valueOf(RaftGroupId.valueOf(Constants.GROUP_ID), PEERS);

  private Constants() {
  }
}
