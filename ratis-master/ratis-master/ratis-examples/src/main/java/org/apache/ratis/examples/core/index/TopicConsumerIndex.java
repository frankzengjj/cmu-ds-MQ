package org.apache.ratis.examples.core.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ratis.examples.core.index.PartitionConsumerIndex;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicConsumerIndex {
    private String topic;
    //corresponding to the partition index, shows partitionConsumerIndexList in Broker
    private List<PartitionConsumerIndex> partitionConsumerIndexList;
}
