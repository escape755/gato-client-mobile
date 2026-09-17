package org.cloudburstmc.protocol.bedrock.data.diagnostics;

import lombok.Value;
import org.cloudburstmc.math.vector.Vector3f;

@Value
public class EntityDiagnosticTimingInfo {
    String displayName;
    String entity;
    long timeInNs;
    byte percentOfTotal;
    /**
     * @since v2192
     */
    Vector3f position;
    /**
     * @since v2192
     */
    String dimension;
}
