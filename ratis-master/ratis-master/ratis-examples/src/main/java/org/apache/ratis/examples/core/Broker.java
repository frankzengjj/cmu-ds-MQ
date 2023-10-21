package org.apache.ratis.examples.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Broker {
    private HashMap<String, List<Partition>> topPartitionListMap;
}
