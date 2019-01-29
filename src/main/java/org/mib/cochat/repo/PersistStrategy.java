package org.mib.cochat.repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistStrategy {
    private boolean compressionEnabled;
    private int maxEditsAllowedBetweenPersists;
    private int periodicalPersistIntervalSeconds;
}
