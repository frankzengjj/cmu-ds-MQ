package org.apache.ratis.examples.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Broker {
    private HashMap<String,PartitionList> topPartitionListMap;
}
