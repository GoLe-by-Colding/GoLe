package com.gole.api.notification.adapter.out.coolsms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.notification.application.port.out.AlimtalkSendException;
import com.gole.api.notification.application.port.out.AlimtalkSendException.FailureType;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort.SendAlimtalkCommand;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse.MessageList;
import com.solapi.sdk.message.exception.SolapiInvalidApiKeyException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import com.solapi.sdk.message.model.FailedMessage;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.model.MessageType;
import com.solapi.sdk.message.model.group.GroupInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CoolsmsAlimtalkAdapterTest {

    @Test
    void sendMapsAlimtalkRequestAndReturnsAcceptance() {
        AtomicReference<Message> sentMessage = new AtomicReference<>();
        AtomicReference<Boolean> showMessageList = new AtomicReference<>();
        CoolsmsAlimtalkAdapter adapter = new CoolsmsAlimtalkAdapter("PF-1", (message, config) -> {
            sentMessage.set(message);
            showMessageList.set(config.getShowMessageList());
            return acceptedResponse("GROUP-1", "MESSAGE-1");
        });

        var acceptance = adapter.send(new SendAlimtalkCommand(
                "010-1234-5678", "TEMPLATE-1", Map.of("#{name}", "홍길동", "#{order}", "ORDER-1")));

        assertThat(acceptance.groupId()).isEqualTo("GROUP-1");
        assertThat(acceptance.messageId()).isEqualTo("MESSAGE-1");
        assertThat(acceptance.statusCode()).isEqualTo("2000");
        assertThat(showMessageList.get()).isTrue();
        assertThat(sentMessage.get().getType()).isEqualTo(MessageType.ATA);
        assertThat(sentMessage.get().getAutoTypeDetect()).isFalse();
        assertThat(sentMessage.get().getTo()).isEqualTo("01012345678");
        assertThat(sentMessage.get().getKakaoOptions().getPfId()).isEqualTo("PF-1");
        assertThat(sentMessage.get().getKakaoOptions().getTemplateId()).isEqualTo("TEMPLATE-1");
        assertThat(sentMessage.get().getKakaoOptions().getDisableSms()).isTrue();
        assertThat(sentMessage.get().getKakaoOptions().variables)
                .containsEntry("#{name}", "홍길동")
                .containsEntry("#{order}", "ORDER-1");
    }

    @Test
    void invalidRecipientAndVariablesAreRejectedBeforeCallingProvider() {
        AtomicReference<Message> sentMessage = new AtomicReference<>();
        CoolsmsAlimtalkAdapter adapter = new CoolsmsAlimtalkAdapter("PF-1", (message, config) -> {
            sentMessage.set(message);
            return acceptedResponse("GROUP-1", "MESSAGE-1");
        });

        assertThatThrownBy(() -> adapter.send(new SendAlimtalkCommand("0111234567", "TEMPLATE-1", Map.of())))
                .isInstanceOfSatisfying(AlimtalkSendException.class, exception -> assertThat(exception.getFailureType())
                        .isEqualTo(FailureType.INVALID_REQUEST));
        assertThatThrownBy(() ->
                        adapter.send(new SendAlimtalkCommand("01012345678", "TEMPLATE-1", Map.of("#{name}", " "))))
                .isInstanceOfSatisfying(AlimtalkSendException.class, exception -> assertThat(exception.getFailureType())
                        .isEqualTo(FailureType.INVALID_REQUEST));
        assertThat(sentMessage.get()).isNull();
    }

    @Test
    void failedMessageListIsNotTreatedAsAcceptance() {
        MultipleDetailMessageSentResponse response = new MultipleDetailMessageSentResponse();
        FailedMessage failed = new FailedMessage();
        failed.setStatusCode("3010");
        failed.setStatusMessage("템플릿 불일치");
        response.setFailedMessageList(List.of(failed));
        CoolsmsAlimtalkAdapter adapter = new CoolsmsAlimtalkAdapter("PF-1", (message, config) -> response);

        assertThatThrownBy(() -> adapter.send(new SendAlimtalkCommand("01012345678", "TEMPLATE-1", Map.of())))
                .isInstanceOfSatisfying(AlimtalkSendException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.PROVIDER_REJECTED);
                    assertThat(exception.isRetrySafe()).isFalse();
                });
    }

    @Test
    void incompleteResponseIsRejected() {
        MultipleDetailMessageSentResponse response = new MultipleDetailMessageSentResponse();
        response.setFailedMessageList(List.of());
        CoolsmsAlimtalkAdapter adapter = new CoolsmsAlimtalkAdapter("PF-1", (message, config) -> response);

        assertThatThrownBy(() -> adapter.send(new SendAlimtalkCommand("01012345678", "TEMPLATE-1", Map.of())))
                .isInstanceOfSatisfying(AlimtalkSendException.class, exception -> assertThat(exception.getFailureType())
                        .isEqualTo(FailureType.PROVIDER_FAILURE));
    }

    @Test
    void sdkExceptionsAreTranslatedWithoutLeakingSdkTypes() {
        CoolsmsAlimtalkAdapter authenticationFailure = new CoolsmsAlimtalkAdapter("PF-1", (message, config) -> {
            throw new SolapiInvalidApiKeyException("invalid key");
        });
        CoolsmsAlimtalkAdapter rateLimitFailure = new CoolsmsAlimtalkAdapter("PF-1", (message, config) -> {
            throw new SolapiUnknownException("429 TooManyRequests");
        });

        assertThatThrownBy(() ->
                        authenticationFailure.send(new SendAlimtalkCommand("01012345678", "TEMPLATE-1", Map.of())))
                .isInstanceOfSatisfying(AlimtalkSendException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.AUTHENTICATION);
                    assertThat(exception.getCause()).isInstanceOf(SolapiInvalidApiKeyException.class);
                });
        assertThatThrownBy(() -> rateLimitFailure.send(new SendAlimtalkCommand("01012345678", "TEMPLATE-1", Map.of())))
                .isInstanceOfSatisfying(AlimtalkSendException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.RATE_LIMITED);
                    assertThat(exception.isRetrySafe()).isTrue();
                });
    }

    @Test
    void concurrentCallsDoNotShareRequestState() throws Exception {
        Set<String> observed = ConcurrentHashMap.newKeySet();
        CoolsmsAlimtalkAdapter adapter = new CoolsmsAlimtalkAdapter("PF-1", (message, config) -> {
            String index = message.getKakaoOptions().variables.get("#{index}");
            observed.add(message.getTo() + ":" + index);
            return acceptedResponse("GROUP-" + index, "MESSAGE-" + index);
        });
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                int current = index;
                futures.add(executor.submit(() -> adapter.send(new SendAlimtalkCommand(
                        "0100000" + String.format("%04d", current),
                        "TEMPLATE-1",
                        Map.of("#{index}", Integer.toString(current))))));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(observed).hasSize(20);
        for (int index = 0; index < 20; index++) {
            assertThat(observed).contains("0100000" + String.format("%04d", index) + ":" + index);
        }
    }

    private static MultipleDetailMessageSentResponse acceptedResponse(String groupId, String messageId) {
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setGroupId(groupId);
        MessageList accepted = new MessageList();
        accepted.setMessageId(messageId);
        accepted.setStatusCode("2000");
        accepted.setStatusMessage("정상 접수");

        MultipleDetailMessageSentResponse response = new MultipleDetailMessageSentResponse();
        response.setGroupInfo(groupInfo);
        response.setFailedMessageList(List.of());
        response.setMessageList(List.of(accepted));
        return response;
    }
}
