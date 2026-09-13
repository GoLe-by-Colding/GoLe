package com.gole.api.chat.adapter.out.assistant;

import com.gole.api.agent.grpc.AgentJobsGrpc;
import com.gole.api.agent.grpc.Job;
import com.gole.api.agent.grpc.JobRequest;
import com.gole.api.agent.grpc.JobState;
import com.gole.api.agent.grpc.PurgeJobRequest;
import com.gole.api.agent.grpc.SubmitJobRequest;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Analysis;
import com.gole.api.chat.application.port.out.SupportAssistantPort.AnalysisPendingException;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Priority;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Request;
import com.gole.api.chat.application.port.out.SupportAssistantPurgePort;
import com.gole.api.chat.application.port.out.SupportAssistantWorkSourcePort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SupportCategory;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 기존 Java 작업 원장 안에서만 실행하며, 원격 작업 키나 원문을 별도 Java 원장에 복제하지 않는다. */
@Component
@ConditionalOnProperty(name = "gole.support-agent.durable.enabled", havingValue = "true")
public class DurableSupportAssistantAdapter implements SupportAssistantPurgePort {
    private final ManagedChannel channel;
    private final AgentJobsGrpc.AgentJobsBlockingStub client;
    private final Duration timeout;
    private final SupportTicketRepositoryPort tickets;
    private final SupportAssistantWorkSourcePort sources;
    private final ObjectMapper mapper;

    @Autowired
    public DurableSupportAssistantAdapter(
            @Value("${gole.environment:local}") String environment,
            @Value("${gole.support-agent.durable.target:127.0.0.1:50052}") String target,
            @Value("${gole.support-agent.durable.caller:}") String caller,
            @Value("${gole.support-agent.durable.token:}") String token,
            @Value("${gole.support-agent.durable.timeout:PT2S}") Duration timeout,
            SupportTicketRepositoryPort tickets,
            SupportAssistantWorkSourcePort sources,
            ObjectMapper mapper) {
        this(settings(environment, target, caller, token, timeout), tickets, sources, mapper);
    }

    DurableSupportAssistantAdapter(
            DurableSupportAssistantSettings settings,
            SupportTicketRepositoryPort tickets,
            SupportAssistantWorkSourcePort sources,
            ObjectMapper mapper) {
        this.channel = ManagedChannelBuilder.forTarget(settings.target())
                .usePlaintext()
                .maxInboundMessageSize(32 * 1024)
                .build();
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-gole-caller", Metadata.ASCII_STRING_MARSHALLER), settings.caller());
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + settings.token());
        this.client = AgentJobsGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
        this.timeout = settings.timeout();
        this.tickets = tickets;
        this.sources = sources;
        this.mapper = mapper;
    }

    private static DurableSupportAssistantSettings settings(
            String environment, String target, String caller, String token, Duration timeout) {
        DurableSupportAssistantSettings.requireLocalEnvironment(environment);
        return new DurableSupportAssistantSettings(target, caller, token, timeout);
    }

    public Optional<Analysis> analyze(Request request) {
        Deadline deadline = Deadline.after(timeout.toNanos(), TimeUnit.NANOSECONDS);
        boolean remotePending = false;
        try {
            var ticket = tickets.findByRoomId(request.ticketId()).orElse(null);
            if (ticket == null || !current(request, ticket.requesterId())) {
                return Optional.empty();
            }
            String owner = ownerKey(ticket.requesterId());
            String key = workKey(request.ticketId());
            var stub = client.withDeadline(deadline);
            Job job = stub.submit(SubmitJobRequest.newBuilder()
                    .setOwnerId(owner)
                    .setIdempotencyKey(key)
                    .setAuthorizationRef(key)
                    .setKind("support.rules")
                    .setProvider("rules")
                    .setAllowExternal(false)
                    .setPayloadJson(mapper.writeValueAsString(Map.of(
                            "ticket_id", key,
                            "declared_category", request.declaredCategory().name(),
                            "title", request.title(),
                            "message", request.message(),
                            "locale", request.locale())))
                    .build());
            String jobId = job.getJobId();
            if (jobId.isBlank()) {
                return Optional.empty();
            }
            JobRequest query =
                    JobRequest.newBuilder().setOwnerId(owner).setJobId(jobId).build();
            while (!deadline.isExpired()) {
                remotePending = false;
                if (!jobId.equals(job.getJobId())) {
                    return Optional.empty();
                }
                if (!current(request, ticket.requesterId())) {
                    stub.cancel(query);
                    return Optional.empty();
                }
                if (job.getState() == JobState.SUCCEEDED) {
                    Optional<Analysis> result = parse(job);
                    return deadline.isExpired() ? Optional.empty() : result;
                }
                if (job.getState() == JobState.FAILED || job.getState() == JobState.CANCELLED) {
                    return Optional.empty();
                }
                if (job.getState() != JobState.QUEUED
                        && job.getState() != JobState.RUNNING
                        && job.getState() != JobState.RETRY_WAIT) {
                    return Optional.empty();
                }
                remotePending = true;
                TimeUnit.NANOSECONDS.sleep(Math.min(
                        TimeUnit.MILLISECONDS.toNanos(25), Math.max(0, deadline.timeRemaining(TimeUnit.NANOSECONDS))));
                job = stub.get(query);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException unavailable) {
            // 원문, owner, credential, 원격 예외 메시지를 저장하거나 로깅하지 않는다.
        }
        // 실행 중 작업은 취소하거나 실패 횟수에 넣지 않는다. 같은 Java 작업을 다시 조회한다.
        if (remotePending) {
            throw new AnalysisPendingException();
        }
        return Optional.empty();
    }

    private boolean current(Request request, String requesterId) {
        return tickets.findByRoomId(request.ticketId())
                        .filter(ticket -> requesterId.equals(ticket.requesterId()))
                        .isPresent()
                && sources.findRequest(request.ticketId())
                        .filter(request::equals)
                        .isPresent();
    }

    private Optional<Analysis> parse(Job job) {
        if (!job.getHumanReviewRequired()) {
            return Optional.empty();
        }
        JsonNode value = mapper.readTree(job.getResultJson());
        if (!value.path("human_review_required").isBoolean()
                || !value.path("human_review_required").booleanValue()
                || !value.path("external_model_used").isBoolean()
                || value.path("external_model_used").booleanValue()
                || !"rules-v1".equals(value.path("engine_version").asString())
                || !value.path("risk_flags").isArray()) {
            return Optional.empty();
        }
        var risk = new ArrayList<String>();
        for (JsonNode flag : value.path("risk_flags")) {
            if (!flag.isString() || flag.asString().length() > 128 || risk.size() >= 16) {
                return Optional.empty();
            }
            risk.add(flag.asString());
        }
        String summary = text(value, "summary", 2000);
        String draft = text(value, "draft_reply", 4000);
        return Optional.of(new Analysis(
                SupportCategory.valueOf(text(value, "recommended_category", 128)),
                Priority.valueOf(text(value, "priority", 32)),
                summary,
                draft,
                risk,
                true,
                false,
                "rules-v1"));
    }

    private static String text(JsonNode value, String field, int limit) {
        JsonNode node = value.path(field);
        if (!node.isString() || node.asString().isBlank() || node.asString().length() > limit) {
            throw new IllegalArgumentException("INVALID_INTERNAL_ANALYSIS");
        }
        return node.asString();
    }

    @Override
    public void purge(String roomId, String requesterId) {
        try {
            var receipt = client.withDeadlineAfter(timeout.toNanos(), TimeUnit.NANOSECONDS)
                    .purge(PurgeJobRequest.newBuilder()
                            .setOwnerId(ownerKey(requesterId))
                            .setIdempotencyKey(workKey(roomId))
                            .build());
            if (receipt.getReceiptId().isBlank() || receipt.getPurgedAtMs() <= 0) {
                throw new IllegalStateException("INVALID_REMOTE_PURGE_RECEIPT");
            }
        } catch (RuntimeException failure) {
            throw new IllegalStateException("SUPPORT_ASSISTANT_PURGE_UNAVAILABLE");
        }
    }

    static String workKey(String roomId) {
        // 기존 원장도 방별 최초 문의 한 건이다. 후속 메시지/담당 변경의 ticket version을 키로 쓰지 않는다.
        return digest("support-opening-v1", roomId);
    }

    static String ownerKey(String requesterId) {
        return digest("support-owner-v1", requesterId);
    }

    private static String digest(String namespace, String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest((namespace + "\0" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA256_UNAVAILABLE");
        }
    }

    @PreDestroy
    void close() throws InterruptedException {
        channel.shutdownNow();
        channel.awaitTermination(2, TimeUnit.SECONDS);
    }
}
