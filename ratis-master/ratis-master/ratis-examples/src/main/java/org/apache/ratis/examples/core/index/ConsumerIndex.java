package org.apache.ratis.examples.core.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;

/**
 * ConsumerIndex is used to record the index of the message that the consumer has consumed.
 * This object will not be synchronized to other brokers.
 * This is the whole point of the consumer index.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerIndex {
    // key: topic, value: topicConsumerIndex
    private HashMap<String, List<PartitionConsumerIndex>> topicConsumerIndexMap = new HashMap<>();
}
