package com.gole.api.chat.adapter.out.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.agent.grpc.AgentJobsGrpc;
import com.gole.api.agent.grpc.Job;
import com.gole.api.agent.grpc.JobRequest;
import com.gole.api.agent.grpc.JobState;
import com.gole.api.agent.grpc.PurgeJobReceipt;
import com.gole.api.agent.grpc.PurgeJobRequest;
import com.gole.api.agent.grpc.SubmitJobRequest;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Request;
import com.gole.api.chat.application.port.out.SupportAssistantWorkSourcePort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportTicket;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DurableSupportAssistantAdapterTest {
    private static final Request REQUEST = new Request("room-1", SupportCategory.PAYMENT, "환불 문의", "결제 취소 문의", "ko-KR");
    private static final String TOKEN = "test-internal-credential-longer-than-32";
    private final SupportTicketRepositoryPort tickets = mock(SupportTicketRepositoryPort.class);
    private final SupportAssistantWorkSourcePort sources = mock(SupportAssistantWorkSourcePort.class);
    private final FakeJobs fake = new FakeJobs();
    private Server server;
    private DurableSupportAssistantAdapter adapter;

    @BeforeEach
    void setup() throws Exception {
        when(tickets.findByRoomId("room-1"))
                .thenReturn(
                        Optional.of(SupportTicket.opened("room-1", "owner-1", SupportCategory.PAYMENT, Instant.EPOCH)));
        when(sources.findRequest("room-1")).thenReturn(Optional.of(REQUEST));
        server = ServerBuilder.forPort(0)
                .addService(fake)
                .intercept(new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                        assertThat(headers.get(Metadata.Key.of("x-gole-caller", Metadata.ASCII_STRING_MARSHALLER)))
                                .isEqualTo("java");
                        assertThat(headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)))
                                .isEqualTo("Bearer " + TOKEN);
                        return next.startCall(call, headers);
                    }
                })
                .build()
                .start();
        adapter = newAdapter(Duration.ofSeconds(2));
    }

    private DurableSupportAssistantAdapter newAdapter(Duration timeout) {
        return new DurableSupportAssistantAdapter(
                new DurableSupportAssistantSettings("127.0.0.1:" + server.getPort(), "java", TOKEN, timeout),
                tickets,
                sources,
                JsonMapper.builder().build());
    }

    @AfterEach
    void cleanup() throws Exception {
        adapter.close();
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("실제 gRPC Submit/Get을 통해 rules-v1만 받아 기존 분석으로 반환함")
    void analyzesUsingAuthenticatedSubmitAndGet() {
        assertThat(adapter.analyze(REQUEST)).hasValueSatisfying(result -> {
            assertThat(result.humanReviewRequired()).isTrue();
            assertThat(result.externalModelUsed()).isFalse();
            assertThat(result.engineVersion()).isEqualTo("rules-v1");
        });
        assertThat(fake.submissions).hasSize(1);
        SubmitJobRequest sent = fake.submissions.getFirst();
        assertThat(sent.getOwnerId()).isEqualTo(DurableSupportAssistantAdapter.ownerKey("owner-1"));
        assertThat(sent.getKind()).isEqualTo("support.rules");
        assertThat(sent.getProvider()).isEqualTo("rules");
        assertThat(sent.getAllowExternal()).isFalse();
        assertThat(fake.lookups.getFirst().getOwnerId()).isEqualTo(sent.getOwnerId());
    }

    @Test
    @DisplayName("Java 재시도는 같은 원격 멱등 키와 승인 참조를 보냄")
    void retryKeepsSameRemoteIdentity() {
        adapter.analyze(REQUEST);
        adapter.analyze(REQUEST);
        assertThat(fake.submissions).hasSize(2);
        assertThat(fake.submissions.get(0)).isEqualTo(fake.submissions.get(1));
    }

    @Test
    @DisplayName("원문이나 owner가 현재 문의와 다르면 Submit하지 않음")
    void rejectsUnownedOrStaleRequestBeforeSubmit() {
        when(sources.findRequest("room-1")).thenReturn(Optional.empty());
        assertThat(adapter.analyze(REQUEST)).isEmpty();
        assertThat(fake.submissions).isEmpty();
    }

    @Test
    @DisplayName("실행 중 원문이 삭제되면 Cancel하고 오래된 결과를 버림")
    void cancelsIfSourceDisappears() {
        fake.onGet = () -> when(sources.findRequest("room-1")).thenReturn(Optional.empty());
        assertThat(adapter.analyze(REQUEST)).isEmpty();
        assertThat(fake.cancellations).hasSize(1);
    }

    @Test
    @DisplayName("다른 job 응답과 실패·취소·정책 위반 결과를 저장하지 않음")
    void rejectsTerminalFailureWrongJobAndUnsafeResults() {
        for (JobState state : List.of(JobState.FAILED, JobState.CANCELLED, JobState.JOB_STATE_UNSPECIFIED)) {
            fake.result = success().toBuilder().setState(state).build();
            assertThat(adapter.analyze(REQUEST)).isEmpty();
        }
        fake.result = success().toBuilder().setJobId("other-job").build();
        assertThat(adapter.analyze(REQUEST)).isEmpty();
        fake.result = success().toBuilder().setHumanReviewRequired(false).build();
        assertThat(adapter.analyze(REQUEST)).isEmpty();
        fake.result = success().toBuilder()
                .setResultJson(success().getResultJson().replace("rules-v1", "external-v1"))
                .build();
        assertThat(adapter.analyze(REQUEST)).isEmpty();
        fake.result = success().toBuilder().setResultJson("{}").build();
        assertThat(adapter.analyze(REQUEST)).isEmpty();
    }

    @Test
    @DisplayName("실행 중 poll은 전체 deadline에서 반환하고 원격 작업을 취소하지 않음")
    void deadlineKeepsRemoteWorkForExistingJavaRetry() throws Exception {
        adapter.close();
        adapter = newAdapter(Duration.ofMillis(500));
        fake.result = success().toBuilder().setState(JobState.RUNNING).build();
        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter.analyze(REQUEST))
                .isInstanceOf(
                        com.gole.api.chat.application.port.out.SupportAssistantPort.AnalysisPendingException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(fake.cancellations).isEmpty();
        fake.result = success();
        assertThat(adapter.analyze(REQUEST)).isPresent();
        assertThat(fake.submissions.getFirst().getIdempotencyKey())
                .isEqualTo(fake.submissions.getLast().getIdempotencyKey());
    }

    @Test
    @DisplayName("파기는 동일 owner/key로 호출하고 원격 실패는 예외로 반환함")
    void purgeUsesSameIdentityAndFailsClosed() {
        adapter.analyze(REQUEST);
        adapter.purge("room-1", "owner-1");
        assertThat(fake.purges.getFirst().getOwnerId())
                .isEqualTo(fake.submissions.getFirst().getOwnerId());
        assertThat(fake.purges.getFirst().getIdempotencyKey())
                .isEqualTo(fake.submissions.getFirst().getIdempotencyKey());
        fake.purgeFailure = true;
        assertThatThrownBy(() -> adapter.purge("room-1", "owner-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SUPPORT_ASSISTANT_PURGE_UNAVAILABLE");
    }

    @Test
    @DisplayName("공개 대상·무제한 timeout·운영 환경을 거절하고 자격증명을 출력하지 않음")
    void configurationRejectsUnsafeTransportAndRedactsSecrets() {
        assertThatThrownBy(() ->
                        new DurableSupportAssistantSettings("example.com:50052", "java", TOKEN, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                        new DurableSupportAssistantSettings("127.0.0.1:50052", "java", TOKEN, Duration.ofSeconds(31)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DurableSupportAssistantSettings.requireLocalEnvironment("production"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new DurableSupportAssistantSettings("127.0.0.1:50052", "java", TOKEN, Duration.ofSeconds(1))
                        .toString())
                .doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("실행 중 owner가 바뀌면 이전 owner의 작업을 취소하고 결과를 버림")
    void ownerChangeRejectsLateResult() {
        fake.onGet = () -> when(tickets.findByRoomId("room-1"))
                .thenReturn(Optional.of(
                        SupportTicket.opened("room-1", "other-owner", SupportCategory.PAYMENT, Instant.EPOCH)));
        assertThat(adapter.analyze(REQUEST)).isEmpty();
        assertThat(fake.cancellations.getFirst().getOwnerId())
                .isEqualTo(DurableSupportAssistantAdapter.ownerKey("owner-1"));
    }

    @Test
    @DisplayName("deadline 뒤 도착한 성공 응답을 분석 결과로 반환하지 않음")
    void slowSuccessCannotEscapeDeadline() throws Exception {
        adapter.close();
        adapter = newAdapter(Duration.ofMillis(500));
        fake.onGet = () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        assertThatThrownBy(() -> adapter.analyze(REQUEST))
                .isInstanceOf(
                        com.gole.api.chat.application.port.out.SupportAssistantPort.AnalysisPendingException.class);
    }

    private static Job success() {
        return Job.newBuilder()
                .setJobId("job-1")
                .setState(JobState.SUCCEEDED)
                .setHumanReviewRequired(true)
                .setResultJson(JsonMapper.builder()
                        .build()
                        .writeValueAsString(Map.of(
                                "recommended_category",
                                "PAYMENT",
                                "priority",
                                "HIGH",
                                "summary",
                                "결제 검토 필요",
                                "draft_reply",
                                "담당자가 확인합니다",
                                "risk_flags",
                                List.of("PAYMENT_REVIEW"),
                                "human_review_required",
                                true,
                                "external_model_used",
                                false,
                                "engine_version",
                                "rules-v1")))
                .build();
    }

    private static class FakeJobs extends AgentJobsGrpc.AgentJobsImplBase {
        final List<SubmitJobRequest> submissions = new ArrayList<>();
        final List<JobRequest> lookups = new ArrayList<>();
        final List<JobRequest> cancellations = new ArrayList<>();
        final List<PurgeJobRequest> purges = new ArrayList<>();
        volatile Job result = success();
        volatile Runnable onGet = () -> {};
        volatile boolean purgeFailure;

        @Override
        public void submit(SubmitJobRequest request, StreamObserver<Job> observer) {
            submissions.add(request);
            observer.onNext(
                    Job.newBuilder().setJobId("job-1").setState(JobState.QUEUED).build());
            observer.onCompleted();
        }

        @Override
        public void get(JobRequest request, StreamObserver<Job> observer) {
            lookups.add(request);
            onGet.run();
            observer.onNext(result);
            observer.onCompleted();
        }

        @Override
        public void cancel(JobRequest request, StreamObserver<Job> observer) {
            cancellations.add(request);
            observer.onNext(Job.newBuilder()
                    .setJobId("job-1")
                    .setState(JobState.CANCELLED)
                    .build());
            observer.onCompleted();
        }

        @Override
        public void purge(PurgeJobRequest request, StreamObserver<PurgeJobReceipt> observer) {
            purges.add(request);
            if (purgeFailure) {
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
            } else {
                observer.onNext(PurgeJobReceipt.newBuilder()
                        .setReceiptId("receipt-1")
                        .setPurgedAtMs(1000)
                        .build());
                observer.onCompleted();
            }
        }
    }
}
