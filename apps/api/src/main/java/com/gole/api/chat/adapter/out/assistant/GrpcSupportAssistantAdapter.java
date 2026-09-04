package com.gole.api.chat.adapter.out.assistant;

import com.gole.api.chat.application.port.out.SupportAssistantPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.support.v1.AnalyzeSupportRequest;
import com.gole.support.v1.AnalyzeSupportResponse;
import com.gole.support.v1.SupportAgentGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 내부 LangGraph 서비스에 짧은 deadline으로 문의 분석을 요청하는 gRPC 어댑터. */
@Component
@ConditionalOnProperty(name = "gole.support-agent.enabled", havingValue = "true")
public class GrpcSupportAssistantAdapter implements SupportAssistantPort {

    private static final Logger log = LoggerFactory.getLogger(GrpcSupportAssistantAdapter.class);

    private final ManagedChannel channel;
    private final SupportAgentGrpc.SupportAgentBlockingStub client;
    private final Duration timeout;

    public GrpcSupportAssistantAdapter(
            @Value("${gole.support-agent.target:localhost:50051}") String target,
            @Value("${gole.support-agent.timeout:PT2S}") Duration timeout) {
        this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        this.client = SupportAgentGrpc.newBlockingStub(channel);
        this.timeout = timeout;
    }

    @Override
    public Optional<Analysis> analyze(Request request) {
        AnalyzeSupportRequest grpcRequest = AnalyzeSupportRequest.newBuilder()
                .setTicketId(request.ticketId())
                .setDeclaredCategory(toGrpcCategory(request.declaredCategory()))
                .setTitle(request.title())
                .setMessage(request.message())
                .setLocale(request.locale())
                .build();
        try {
            AnalyzeSupportResponse response = client.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .analyze(grpcRequest);
            return Optional.of(fromGrpc(response));
        } catch (StatusRuntimeException exception) {
            // 문의 본문·제목·사용자 식별자는 로그에 남기지 않는다. AI 장애가 문의 접수를 막지도 않는다.
            log.warn(
                    "Support agent gRPC unavailable status={}",
                    exception.getStatus().getCode());
            return Optional.empty();
        }
    }

    @PreDestroy
    void close() throws InterruptedException {
        channel.shutdown();
        if (!channel.awaitTermination(2, TimeUnit.SECONDS)) {
            channel.shutdownNow();
        }
    }

    private static Analysis fromGrpc(AnalyzeSupportResponse response) {
        return new Analysis(
                fromGrpcCategory(response.getRecommendedCategory()),
                switch (response.getPriority()) {
                    case SUPPORT_PRIORITY_LOW -> Priority.LOW;
                    case SUPPORT_PRIORITY_HIGH -> Priority.HIGH;
                    case SUPPORT_PRIORITY_URGENT -> Priority.URGENT;
                    case SUPPORT_PRIORITY_NORMAL, SUPPORT_PRIORITY_UNSPECIFIED, UNRECOGNIZED -> Priority.NORMAL;
                },
                response.getSummary(),
                response.getDraftReply(),
                List.copyOf(response.getRiskFlagsList()),
                response.getHumanReviewRequired(),
                response.getExternalModelUsed(),
                response.getEngineVersion());
    }

    private static com.gole.support.v1.SupportCategory toGrpcCategory(SupportCategory category) {
        return com.gole.support.v1.SupportCategory.valueOf("SUPPORT_CATEGORY_" + category.name());
    }

    private static SupportCategory fromGrpcCategory(com.gole.support.v1.SupportCategory category) {
        String name = category.name();
        if (!name.startsWith("SUPPORT_CATEGORY_")
                || category == com.gole.support.v1.SupportCategory.SUPPORT_CATEGORY_UNSPECIFIED
                || category == com.gole.support.v1.SupportCategory.UNRECOGNIZED) {
            return SupportCategory.GENERAL;
        }
        return SupportCategory.valueOf(name.substring("SUPPORT_CATEGORY_".length()));
    }
}
