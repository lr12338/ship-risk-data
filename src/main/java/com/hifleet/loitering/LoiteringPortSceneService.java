package com.hifleet.loitering;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoiteringPortSceneService {
    private final LoiteringPortSceneLoader loader;
    private volatile LoiteringPortSceneSnapshot snapshot;
    @Autowired public LoiteringPortSceneService(LoiteringPortSceneLoader loader) { this.loader = loader; }
    public LoiteringPortSceneSnapshot requireSnapshot() {
        LoiteringPortSceneSnapshot current = snapshot;
        if (current != null) return current;
        synchronized (this) { if (snapshot == null) snapshot = loader.load(); return snapshot; }
    }
}
