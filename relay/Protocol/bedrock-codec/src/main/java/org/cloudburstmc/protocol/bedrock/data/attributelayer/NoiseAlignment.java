package org.cloudburstmc.protocol.bedrock.data.attributelayer;

import lombok.Value;

@Value
public class NoiseAlignment {

    Type type;
    int value;

    public enum Type {
        MIN_LOCAL_TRANSITION_END
    }
}
