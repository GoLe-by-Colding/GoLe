package com.gole.api.parts.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.parts.application.port.in.CreatePartRequestUseCase.CreatePartRequestCommand;
import com.gole.api.parts.application.port.in.CreatePartRequestUseCase.ItemCommand;
import com.gole.api.parts.application.port.in.ListPartRequestsUseCase.ListPartRequestsQuery;
import com.gole.api.parts.application.port.in.ListPartRequestsUseCase.StatusFilter;
import com.gole.api.parts.application.port.out.PartRequestHolderNotifierPort;
import com.gole.api.parts.application.port.out.PartRequestRepositoryPort;
import com.gole.api.parts.application.port.out.SetOwnerQueryPort;
import com.gole.api.parts.domain.exception.InvalidPartRequestException;
import com.gole.api.parts.domain.exception.PartRequestNotFoundException;
import com.gole.api.parts.domain.exception.PartRequestNotOpenException;
import com.gole.api.parts.domain.model.PartRequest;
import com.gole.api.parts.domain.model.PartRequestStatus;
import com.gole.api.parts.domain.model.WantedPart;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PartRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    private static final List<ItemCommand> ONE_ITEM = List.of(new ItemCommand("3062b", "Black", 4));

    private InMemoryPartRequestRepository repository;
    private RecordingNotifier notifier;
    private List<String> owners;
    private RuntimeException ownerLookupFailure;
    private AtomicInteger ownerLookups;
    private Set<String> catalogSets;
    private PartRequestService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPartRequestRepository();
        notifier = new RecordingNotifier();
        owners = new ArrayList<>();
        ownerLookupFailure = null;
        ownerLookups = new AtomicInteger();
        catalogSets = Set.of("10305", "75192");
        AtomicInteger sequence = new AtomicInteger();
        SetOwnerQueryPort ownerQuery = setNumber -> {
            ownerLookups.incrementAndGet();
            if (ownerLookupFailure != null) {
                throw ownerLookupFailure;
            }
            return owners;
        };
        service = new PartRequestService(
                repository,
                () -> "pr-" + sequence.incrementAndGet(),
                setNumber -> catalogSets.contains(setNumber),
                ownerQuery,
                notifier,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PartRequest create(String requesterId, String setNumber) {
        return service.create(new CreatePartRequestCommand(requesterId, setNumber, ONE_ITEM, "메모"));
    }

    @Test
    @DisplayName("요청을 저장하고 열린 상태로 돌려준다")
    void create_savesOpenRequest() {
        PartRequest created = service.create(new CreatePartRequestCommand(
                "user-1",
                " 10305 ",
                List.of(new ItemCommand(" 3062b ", " Black ", 4), new ItemCommand("3001", "Red", 2)),
                "  급해요 "));

        assertThat(created.getId()).isEqualTo("pr-1");
        assertThat(created.getRequesterId()).isEqualTo("user-1");
        assertThat(created.getSetNumber()).isEqualTo("10305");
        assertThat(created.getItems())
                .containsExactly(new WantedPart("3062b", "Black", 4), new WantedPart("3001", "Red", 2));
        assertThat(created.getNote()).isEqualTo("급해요");
        assertThat(created.getStatus()).isEqualTo(PartRequestStatus.OPEN);
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
        assertThat(repository.findById("pr-1")).isPresent();
    }

    @Test
    @DisplayName("입력 규칙 위반은 저장하지 않고 PART_REQUEST_INVALID")
    void create_rejectsInvalidInputWithoutSaving() {
        assertThatThrownBy(() -> service.create(new CreatePartRequestCommand("user-1", null, List.of(), "")))
                .isInstanceOf(InvalidPartRequestException.class);
        assertThatThrownBy(() -> service.create(new CreatePartRequestCommand("user-1", null, null, "")))
                .isInstanceOf(InvalidPartRequestException.class);
        assertThatThrownBy(() -> service.create(new CreatePartRequestCommand(
                        "user-1", null, List.of(new ItemCommand("3062b", "Black", null)), "")))
                .isInstanceOf(InvalidPartRequestException.class);
        assertThatThrownBy(() -> service.create(
                        new CreatePartRequestCommand("user-1", null, Arrays.asList((ItemCommand) null), "")))
                .isInstanceOf(InvalidPartRequestException.class);
        List<ItemCommand> tooMany = IntStream.range(0, 21)
                .mapToObj(i -> new ItemCommand("3062b", "Black", 1))
                .toList();
        assertThatThrownBy(() -> service.create(new CreatePartRequestCommand("user-1", null, tooMany, "")))
                .isInstanceOf(InvalidPartRequestException.class)
                .extracting("code")
                .isEqualTo("PART_REQUEST_INVALID");

        assertThat(repository.store).isEmpty();
    }

    @Test
    @DisplayName("카탈로그에 없는 세트면 404 PART_REQUEST_SET_NOT_FOUND")
    void create_unknownSetIsNotFound() {
        assertThatThrownBy(() -> create("user-1", "99999"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo("PART_REQUEST_SET_NOT_FOUND");
        assertThat(repository.store).isEmpty();
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    @DisplayName("세트 없는 요청은 카탈로그를 보지 않고 알림도 보내지 않는다")
    void create_withoutSet_skipsCatalogAndNotification() {
        owners.add("owner-1");

        PartRequest created = create("user-1", "  ");

        assertThat(created.getSetNumber()).isNull();
        assertThat(ownerLookups.get()).isZero();
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    @DisplayName("열린 요청이 10건이면 11번째는 409, 하나 마감하면 다시 열 수 있다")
    void create_limitsOpenRequestsToTen() {
        for (int i = 0; i < 10; i++) {
            create("user-1", null);
        }
        create("user-2", null);

        assertThatThrownBy(() -> create("user-1", null))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("PART_REQUEST_LIMIT_EXCEEDED");

        service.close("pr-1", "user-1");
        assertThatCode(() -> create("user-1", null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("세트 보유자에게 알리되 작성자는 빼고 중복은 한 번만 보낸다")
    void create_notifiesOwnersExceptRequester() {
        owners.addAll(List.of("owner-1", "user-1", "owner-2", "owner-1"));

        PartRequest created = create("user-1", "10305");

        assertThat(notifier.sent)
                .containsExactly(
                        new Sent("owner-1", created.getId(), "10305"), new Sent("owner-2", created.getId(), "10305"));
    }

    @Test
    @DisplayName("보유자 알림은 최대 100명까지")
    void create_capsNotifiedOwnersAt100() {
        IntStream.range(0, 150).forEach(i -> owners.add("owner-" + i));

        create("user-1", "10305");

        assertThat(notifier.sent).hasSize(100);
        assertThat(notifier.sent.getFirst().recipientId()).isEqualTo("owner-0");
        assertThat(notifier.sent.getLast().recipientId()).isEqualTo("owner-99");
    }

    @Test
    @DisplayName("보유자 조회가 실패해도 요청은 등록된다")
    void create_ownerLookupFailureDoesNotFailCreation() {
        ownerLookupFailure = new IllegalStateException("collection down");

        PartRequest created = create("user-1", "10305");

        assertThat(repository.findById(created.getId())).isPresent();
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    @DisplayName("게시판은 질의 조건을 저장소에 그대로 넘긴다 — ALL은 상태 무관")
    void list_passesFiltersToRepository() {
        create("user-1", "10305");
        create("user-1", "75192");
        create("user-2", "10305");
        service.close("pr-1", "user-1");

        assertThat(service.list(ListPartRequestsQuery.of("10305", StatusFilter.OPEN, null)))
                .extracting(PartRequest::getId)
                .containsExactly("pr-3");
        assertThat(service.list(ListPartRequestsQuery.of("10305", StatusFilter.CLOSED, null)))
                .extracting(PartRequest::getId)
                .containsExactly("pr-1");
        assertThat(service.list(ListPartRequestsQuery.of(null, StatusFilter.ALL, null)))
                .extracting(PartRequest::getId)
                .containsExactlyInAnyOrder("pr-1", "pr-2", "pr-3");
        assertThat(service.list(ListPartRequestsQuery.of(null, StatusFilter.ALL, 2)))
                .hasSize(2);
    }

    @Test
    @DisplayName("내 요청은 상태 무관, 최대 50건")
    void listMine_returnsOwnRequestsUpTo50() {
        create("user-1", null);
        create("user-2", null);
        service.close("pr-1", "user-1");

        assertThat(service.listMine("user-1")).extracting(PartRequest::getId).containsExactly("pr-1");
        assertThat(repository.lastRequesterLimit).isEqualTo(50);
    }

    @Test
    @DisplayName("없는 요청은 404 PART_REQUEST_NOT_FOUND")
    void get_missingIsNotFound() {
        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOf(PartRequestNotFoundException.class)
                .extracting("code")
                .isEqualTo("PART_REQUEST_NOT_FOUND");
    }

    @Test
    @DisplayName("작성자는 마감할 수 있고, 다시 마감하면 409")
    void close_byRequester_thenAgainConflicts() {
        PartRequest created = create("user-1", null);

        PartRequest closed = service.close(created.getId(), "user-1");

        assertThat(closed.getStatus()).isEqualTo(PartRequestStatus.CLOSED);
        assertThat(closed.getClosedAt()).isEqualTo(NOW);
        assertThat(repository.findById(created.getId()).orElseThrow().getStatus())
                .isEqualTo(PartRequestStatus.CLOSED);
        assertThatThrownBy(() -> service.close(created.getId(), "user-1"))
                .isInstanceOf(PartRequestNotOpenException.class)
                .extracting("code")
                .isEqualTo("PART_REQUEST_NOT_OPEN");
    }

    @Test
    @DisplayName("작성자가 아니면 마감·삭제 모두 403")
    void closeAndDelete_byOtherUserAreForbidden() {
        PartRequest created = create("user-1", null);

        assertThatThrownBy(() -> service.close(created.getId(), "user-2"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("PART_REQUEST_ACCESS_DENIED");
        assertThatThrownBy(() -> service.delete(created.getId(), "user-2"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("PART_REQUEST_ACCESS_DENIED");
        assertThat(repository.findById(created.getId()).orElseThrow().isOpen()).isTrue();
    }

    @Test
    @DisplayName("마감 직전에 다른 요청이 먼저 마감했으면 409, 지웠으면 404")
    void close_raceWithConcurrentCloseOrDelete() {
        PartRequest first = create("user-1", null);
        PartRequest second = create("user-1", null);
        repository.beforeClosure =
                id -> repository.store.put(id, repository.store.get(id).close(NOW));
        assertThatThrownBy(() -> service.close(first.getId(), "user-1"))
                .isInstanceOf(PartRequestNotOpenException.class);

        repository.beforeClosure = repository.store::remove;
        assertThatThrownBy(() -> service.close(second.getId(), "user-1"))
                .isInstanceOf(PartRequestNotFoundException.class);
        assertThat(repository.findById(second.getId())).isEmpty();
    }

    @Test
    @DisplayName("작성자는 삭제할 수 있고, 없는 요청 삭제는 404")
    void delete_byRequester() {
        PartRequest created = create("user-1", null);

        service.delete(created.getId(), "user-1");

        assertThat(repository.findById(created.getId())).isEmpty();
        assertThatThrownBy(() -> service.delete(created.getId(), "user-1"))
                .isInstanceOf(PartRequestNotFoundException.class);
    }

    private record Sent(String recipientId, String requestId, String setNumber) {}

    private static final class RecordingNotifier implements PartRequestHolderNotifierPort {
        final List<Sent> sent = new ArrayList<>();

        @Override
        public void notifyOwner(String recipientId, String requestId, String setNumber) {
            sent.add(new Sent(recipientId, requestId, setNumber));
        }
    }

    /** 저장 순서를 생성 순서로 보고, 최신순은 그 역순으로 흉내 낸다(같은 시각 고정 시계라서). */
    private static final class InMemoryPartRequestRepository implements PartRequestRepositoryPort {
        final Map<String, PartRequest> store = new LinkedHashMap<>();
        final Map<String, Integer> order = new LinkedHashMap<>();
        java.util.function.Consumer<String> beforeClosure = id -> {};
        int lastRequesterLimit;

        @Override
        public PartRequest save(PartRequest request) {
            store.put(request.getId(), request);
            order.putIfAbsent(request.getId(), order.size());
            return request;
        }

        @Override
        public Optional<PartRequest> findById(String requestId) {
            return Optional.ofNullable(store.get(requestId));
        }

        @Override
        public boolean saveClosureIfOpen(PartRequest closed) {
            beforeClosure.accept(closed.getId());
            PartRequest current = store.get(closed.getId());
            if (current == null || !current.isOpen()) {
                return false;
            }
            store.put(closed.getId(), closed);
            return true;
        }

        @Override
        public long countOpenByRequester(String requesterId) {
            return store.values().stream()
                    .filter(r -> r.isRequestedBy(requesterId) && r.isOpen())
                    .count();
        }

        @Override
        public List<PartRequest> search(String setNumber, PartRequestStatus status, int limit) {
            return newestFirst().stream()
                    .filter(r -> setNumber == null || setNumber.equals(r.getSetNumber()))
                    .filter(r -> status == null || status == r.getStatus())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<PartRequest> findByRequester(String requesterId, int limit) {
            lastRequesterLimit = limit;
            return newestFirst().stream()
                    .filter(r -> r.isRequestedBy(requesterId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void deleteById(String requestId) {
            store.remove(requestId);
        }

        private List<PartRequest> newestFirst() {
            return store.values().stream()
                    .sorted(Comparator.comparing((PartRequest r) -> order.get(r.getId()))
                            .reversed())
                    .toList();
        }
    }
}
