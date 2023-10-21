package org.apache.ratis.examples.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Partition {
    private String topic;
    private List<MyMessage> messageList = new ArrayList<>();
}
