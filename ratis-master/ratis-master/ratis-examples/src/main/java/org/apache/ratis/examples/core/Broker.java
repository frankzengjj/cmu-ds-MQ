package org.apache.ratis.examples.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ratis.examples.core.index.ConsumerIndex;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Broker implements Serializable {
    private HashMap<String, List<Partition>> topPartitionListMap = new HashMap<>();
    private ConsumerIndex consumerIndex = new ConsumerIndex();
}
