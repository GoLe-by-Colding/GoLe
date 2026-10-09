package com.gole.api.parts.application.service;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.parts.application.port.in.ClosePartRequestUseCase;
import com.gole.api.parts.application.port.in.CreatePartRequestUseCase;
import com.gole.api.parts.application.port.in.DeletePartRequestUseCase;
import com.gole.api.parts.application.port.in.GetPartRequestUseCase;
import com.gole.api.parts.application.port.in.ListPartRequestsUseCase;
import com.gole.api.parts.application.port.out.PartRequestHolderNotifierPort;
import com.gole.api.parts.application.port.out.PartRequestIdGeneratorPort;
import com.gole.api.parts.application.port.out.PartRequestRepositoryPort;
import com.gole.api.parts.application.port.out.PartsCatalogPort;
import com.gole.api.parts.application.port.out.SetOwnerQueryPort;
import com.gole.api.parts.domain.exception.InvalidPartRequestException;
import com.gole.api.parts.domain.exception.PartRequestNotFoundException;
import com.gole.api.parts.domain.exception.PartRequestNotOpenException;
import com.gole.api.parts.domain.model.PartRequest;
import com.gole.api.parts.domain.model.WantedPart;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 부족 부품 요청 유스케이스. inbound port를 구현하고 outbound port에만 의존한다. (wanted-parts W1~W8)
 *
 * <p>보유자 알림의 수신자 정책(작성자 제외·중복 제거·상한)은 여기서 정한다. 조회와 발송은 각각
 * collection·notification 어댑터가 맡고, 둘 다 best-effort라 요청 등록을 되돌리지 않는다.
 */
@Service
public class PartRequestService
        implements CreatePartRequestUseCase,
                ListPartRequestsUseCase,
                GetPartRequestUseCase,
                ClosePartRequestUseCase,
                DeletePartRequestUseCase {

    /** 한 사람이 동시에 열어 둘 수 있는 요청 수. (W2) */
    static final int MAX_OPEN_PER_REQUESTER = 10;

    /** 요청 하나가 보내는 보유자 알림 상한. 인기 세트에서 알림 폭주를 막는다. (W8) */
    static final int MAX_NOTIFIED_OWNERS = 100;

    private static final Logger log = LoggerFactory.getLogger(PartRequestService.class);

    private final PartRequestRepositoryPort repository;
    private final PartRequestIdGeneratorPort idGenerator;
    private final PartsCatalogPort catalog;
    private final SetOwnerQueryPort owners;
    private final PartRequestHolderNotifierPort notifier;
    private final Clock clock;

    public PartRequestService(
            PartRequestRepositoryPort repository,
            PartRequestIdGeneratorPort idGenerator,
            PartsCatalogPort catalog,
            SetOwnerQueryPort owners,
            PartRequestHolderNotifierPort notifier,
            Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.catalog = catalog;
        this.owners = owners;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    public PartRequest create(CreatePartRequestCommand command) {
        // 입력 규칙(400)을 먼저 본다. 틀린 요청 때문에 카탈로그·저장소를 두드리지 않는다.
        PartRequest request = PartRequest.create(
                idGenerator.newId(),
                command.requesterId(),
                command.setNumber(),
                toWantedParts(command.items()),
                command.note(),
                clock.instant());

        // 세트 이름은 보유자 알림 문구에 쓴다("보유한 에펠탑(10307) 세트의…"). 번호만으로는 무슨 세트인지 바로 안 읽힌다.
        String setLabel = null;
        if (request.hasSet()) {
            String setNumber = request.getSetNumber();
            setLabel = catalog.setName(setNumber)
                    .map(name -> name + "(" + setNumber + ")")
                    .orElseThrow(
                            () -> new NotFoundException("PART_REQUEST_SET_NOT_FOUND", "카탈로그에 없는 세트입니다: " + setNumber));
        }
        if (repository.countOpenByRequester(request.getRequesterId()) >= MAX_OPEN_PER_REQUESTER) {
            throw new ConflictException(
                    "PART_REQUEST_LIMIT_EXCEEDED",
                    "열린 부품 요청은 " + MAX_OPEN_PER_REQUESTER + "건까지 둘 수 있습니다. 해결된 요청을 마감해 주세요");
        }

        PartRequest saved = repository.save(request);
        notifyOwners(saved, setLabel);
        return saved;
    }

    @Override
    public List<PartRequest> list(ListPartRequestsQuery query) {
        return repository.search(query.setNumber(), query.status().toStatus(), query.limit());
    }

    @Override
    public List<PartRequest> listMine(String requesterId) {
        return repository.findByRequester(requesterId, MINE_LIMIT);
    }

    @Override
    public PartRequest get(String requestId) {
        return load(requestId);
    }

    @Override
    public PartRequest close(String requestId, String actorId) {
        PartRequest request = load(requestId);
        requireRequester(request, actorId);
        PartRequest closed = request.close(clock.instant());
        if (!repository.saveClosureIfOpen(closed)) {
            // 읽은 뒤 다른 요청이 먼저 마감·삭제했다. 지금 상태에 맞는 오류를 돌려준다(삭제면 404).
            load(requestId);
            throw new PartRequestNotOpenException(requestId);
        }
        return closed;
    }

    @Override
    public void delete(String requestId, String actorId) {
        PartRequest request = load(requestId);
        requireRequester(request, actorId);
        repository.deleteById(requestId);
    }

    private PartRequest load(String requestId) {
        return repository.findById(requestId).orElseThrow(() -> new PartRequestNotFoundException(requestId));
    }

    private static void requireRequester(PartRequest request, String actorId) {
        if (!request.isRequestedBy(actorId)) {
            throw new ForbiddenException("PART_REQUEST_ACCESS_DENIED", "요청을 올린 사람만 할 수 있습니다");
        }
    }

    private static List<WantedPart> toWantedParts(List<ItemCommand> items) {
        if (items == null) {
            return null;
        }
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new InvalidPartRequestException("빈 부품 항목이 있습니다");
        }
        // 개수 상한(20)은 도메인이 본다. 다만 터무니없이 긴 목록을 다 변환하기 전에 자른다.
        if (items.size() > PartRequest.MAX_ITEMS) {
            throw new InvalidPartRequestException("부품은 1~20개까지 적을 수 있습니다");
        }
        return items.stream()
                .map(item -> WantedPart.of(item.partNumber(), item.colorName(), item.quantity()))
                .toList();
    }

    /** 세트 보유자 알림(W8). 실패해도 요청은 이미 저장됐으므로 등록 응답을 바꾸지 않는다. */
    private void notifyOwners(PartRequest request, String setLabel) {
        if (!request.hasSet()) {
            return;
        }
        List<String> recipients;
        try {
            recipients = owners.ownersOf(request.getSetNumber()).stream()
                    .filter(Objects::nonNull)
                    .filter(ownerId -> !request.isRequestedBy(ownerId))
                    .distinct()
                    .limit(MAX_NOTIFIED_OWNERS)
                    .toList();
        } catch (RuntimeException exception) {
            // 보유자 조회 장애가 요청 등록을 되돌리면 안 된다.
            log.warn(
                    "부품 요청 보유자 조회 실패 setNumber={} requestId={}: {}",
                    request.getSetNumber(),
                    request.getId(),
                    exception.getMessage());
            return;
        }
        for (String recipientId : recipients) {
            notifier.notifyOwner(recipientId, request.getId(), setLabel);
        }
    }
}
