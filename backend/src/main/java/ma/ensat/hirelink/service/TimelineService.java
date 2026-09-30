package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.ApplicationStatusHistory;
import ma.ensat.hirelink.repository.ApplicationStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TimelineService {
    private final ApplicationStatusHistoryRepository repository;

    public TimelineService(ApplicationStatusHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void append(Long applicationId, String from, String to, Long actorUserId, String payloadJson) {
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplicationId(applicationId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorUserId(actorUserId);
        h.setPayloadJson(payloadJson);
        repository.save(h);
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusHistory> forApplication(Long applicationId) {
        return repository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
    }
}
