package org.apache.ratis.examples.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String topic;
    private String content;
    private Date generateTime;
    private String generateBy;
}
