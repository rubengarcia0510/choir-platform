package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.RepertoireItem;
import com.rubengarcia.choir.repository.RepertoireItemRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RepertoireItemServiceImpl implements RepertoireItemService {

    private final ResourceAuthorizationService authorizationService;
    private final RepertoireItemRepository repository;

    public RepertoireItemServiceImpl(
            ResourceAuthorizationService authorizationService,
            RepertoireItemRepository repository) {
        this.authorizationService = authorizationService;
        this.repository = repository;
    }

    @Override
    public Flux<RepertoireItem> list(String email, long choirId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .thenMany(repository.findByChoirId(choirId));
    }

    @Override
    public Mono<RepertoireItem> create(
            String email, long choirId, RepertoireItemData data) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(Mono.defer(() -> {
                    RepertoireItem item = new RepertoireItem();
                    item.setChoirId(choirId);
                    item.setTitle(data.title().trim());
                    item.setComposer(normalize(data.composer()));
                    item.setArranger(normalize(data.arranger()));
                    item.setNotes(normalize(data.notes()));
                    return repository.save(item);
                }));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
