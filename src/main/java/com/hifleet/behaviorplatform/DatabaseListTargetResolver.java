package com.hifleet.behaviorplatform;

import com.hifleet.domain.TargetShip;
import com.hifleet.repository.TargetShipRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public final class DatabaseListTargetResolver {
    private final TargetShipRepository repository;
    public DatabaseListTargetResolver(TargetShipRepository repository) { this.repository = repository; }
    public TargetListSnapshot snapshot(String list) { return repository.snapshot(list); }
    public List<TargetShip> resolve(String list, int startSeq, int limit, int pageSize) {
        List<TargetShip> result = new ArrayList<TargetShip>();
        int after = startSeq;
        while (result.size() < limit) {
            List<TargetShip> page = repository.selectPage(list, after, Math.min(pageSize, limit - result.size()));
            if (page.isEmpty()) break;
            result.addAll(page);
            after = page.get(page.size() - 1).getSeqNo();
        }
        return result;
    }
}
