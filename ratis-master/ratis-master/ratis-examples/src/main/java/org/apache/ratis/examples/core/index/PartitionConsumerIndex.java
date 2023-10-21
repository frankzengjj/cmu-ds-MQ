package org.apache.ratis.examples.core.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PartitionConsumerIndex {
    private String topic;
    // key: consumerGroupId, value: consumeIndex
    private HashMap<String, Integer> partitionConsumerIndexMap;
}
