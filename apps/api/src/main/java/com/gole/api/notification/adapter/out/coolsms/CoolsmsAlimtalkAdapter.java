package com.gole.api.notification.adapter.out.coolsms;

import com.gole.api.notification.application.port.out.AlimtalkSendException;
import com.gole.api.notification.application.port.out.AlimtalkSendException.FailureType;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.dto.request.SendRequestConfig;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse.MessageList;
import com.solapi.sdk.message.exception.SolapiApiKeyException;
import com.solapi.sdk.message.exception.SolapiBadRequestException;
import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiInvalidApiKeyException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import com.solapi.sdk.message.model.FailedMessage;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.model.MessageType;
import com.solapi.sdk.message.model.kakao.KakaoOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** CoolSMS 공식 SDK를 사용하는 카카오 알림톡 단건 발송 어댑터. */
public final class CoolsmsAlimtalkAdapter implements AlimtalkSenderPort {

    private static final Logger log = LoggerFactory.getLogger(CoolsmsAlimtalkAdapter.class);
    private static final Pattern KOREAN_MOBILE_NUMBER = Pattern.compile("010\\d{8}");
    private static final String ACCEPTED_STATUS_CODE = "2000";

    private final String pfId;
    private final SolapiMessageClient client;

    CoolsmsAlimtalkAdapter(CoolsmsProperties properties) {
        this(
                properties.pfId(),
                SolapiClient.INSTANCE.createInstance(properties.apiKey(), properties.apiSecret())::send);
    }

    CoolsmsAlimtalkAdapter(String pfId, SolapiMessageClient client) {
        this.pfId = requireText(pfId, "pfId");
        this.client = client;
    }

    @Override
    public AlimtalkAcceptance send(SendAlimtalkCommand command) {
        ValidatedCommand validated = validate(command);
        Message message = message(validated);
        SendRequestConfig config = new SendRequestConfig();
        config.setShowMessageList(true);

        MultipleDetailMessageSentResponse response;
        try {
            response = client.send(message, config);
        } catch (Exception exception) {
            throw translate(exception);
        }

        return acceptance(response, validated.to(), validated.templateId());
    }

    private Message message(ValidatedCommand command) {
        KakaoOption kakaoOption = new KakaoOption();
        kakaoOption.setPfId(pfId);
        kakaoOption.setTemplateId(command.templateId());
        kakaoOption.setVariables(command.variables());
        kakaoOption.setDisableSms(true);

        Message message = new Message();
        message.setType(MessageType.ATA);
        message.setAutoTypeDetect(false);
        message.setTo(command.to());
        message.setKakaoOptions(kakaoOption);
        return message;
    }

    private AlimtalkAcceptance acceptance(MultipleDetailMessageSentResponse response, String to, String templateId) {
        if (response == null) {
            throw failure(FailureType.ACCEPTANCE_UNKNOWN, "CoolSMS returned an empty response");
        }

        List<FailedMessage> failedMessages = response.getFailedMessageList();
        if (failedMessages != null && !failedMessages.isEmpty()) {
            FailedMessage failed = failedMessages.getFirst();
            log.warn(
                    "CoolSMS 알림톡 등록 실패 to={} templateId={} statusCode={}",
                    mask(to),
                    templateId,
                    safe(failed.getStatusCode()));
            throw failure(
                    FailureType.PROVIDER_REJECTED,
                    providerMessage("CoolSMS rejected the message", failed.getStatusCode(), failed.getStatusMessage()));
        }

        String groupId =
                response.getGroupInfo() == null ? null : response.getGroupInfo().getGroupId();
        List<MessageList> messages = response.getMessageList();
        if (!hasText(groupId) || messages == null || messages.size() != 1) {
            throw failure(FailureType.PROVIDER_FAILURE, "CoolSMS response is missing acceptance details");
        }

        MessageList accepted = messages.getFirst();
        if (!ACCEPTED_STATUS_CODE.equals(accepted.getStatusCode()) || !hasText(accepted.getMessageId())) {
            throw failure(
                    FailureType.PROVIDER_REJECTED,
                    providerMessage(
                            "CoolSMS did not accept the message",
                            accepted.getStatusCode(),
                            accepted.getStatusMessage()));
        }

        return new AlimtalkAcceptance(
                groupId, accepted.getMessageId(), accepted.getStatusCode(), safe(accepted.getStatusMessage()));
    }

    private static ValidatedCommand validate(SendAlimtalkCommand command) {
        if (command == null) {
            throw failure(FailureType.INVALID_REQUEST, "command must not be null");
        }
        String to = command.to() == null ? "" : command.to().replaceAll("[\\s-]", "");
        if (!KOREAN_MOBILE_NUMBER.matcher(to).matches()) {
            throw failure(FailureType.INVALID_REQUEST, "to must be a Korean 010 mobile number");
        }
        String templateId = requireText(command.templateId(), "templateId");
        for (Map.Entry<String, String> variable : command.variables().entrySet()) {
            requireText(variable.getKey(), "variable key");
            requireText(variable.getValue(), "variable value");
        }
        return new ValidatedCommand(to, templateId, command.variables());
    }

    private static AlimtalkSendException translate(Exception exception) {
        if (isRateLimited(exception)) {
            return new AlimtalkSendException(FailureType.RATE_LIMITED, "CoolSMS rate limit exceeded", exception);
        }
        if (exception instanceof SolapiInvalidApiKeyException || exception instanceof SolapiApiKeyException) {
            return new AlimtalkSendException(FailureType.AUTHENTICATION, "CoolSMS authentication failed", exception);
        }
        if (exception instanceof SolapiBadRequestException) {
            return new AlimtalkSendException(FailureType.INVALID_REQUEST, "CoolSMS rejected the request", exception);
        }
        if (exception instanceof SolapiMessageNotReceivedException) {
            return new AlimtalkSendException(
                    FailureType.PROVIDER_REJECTED, "CoolSMS did not receive the message", exception);
        }
        if (exception instanceof SolapiEmptyResponseException || exception instanceof SolapiUnknownException) {
            return new AlimtalkSendException(
                    FailureType.ACCEPTANCE_UNKNOWN, "CoolSMS acceptance could not be confirmed", exception);
        }
        return new AlimtalkSendException(FailureType.PROVIDER_FAILURE, "CoolSMS request failed", exception);
    }

    private static boolean isRateLimited(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("toomanyrequests")
                || normalized.contains("too many requests")
                || normalized.contains("429");
    }

    private static String requireText(String value, String field) {
        if (!hasText(value)) {
            throw failure(FailureType.INVALID_REQUEST, field + " must not be blank");
        }
        return value.trim();
    }

    private static AlimtalkSendException failure(FailureType type, String message) {
        return new AlimtalkSendException(type, message);
    }

    private static String providerMessage(String prefix, String code, String message) {
        return prefix + " (statusCode=" + safe(code) + ", statusMessage=" + safe(message) + ")";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String safe(String value) {
        return hasText(value) ? value : "unknown";
    }

    private static String mask(String phoneNumber) {
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(7);
    }

    private record ValidatedCommand(String to, String templateId, Map<String, String> variables) {}

    @FunctionalInterface
    interface SolapiMessageClient {

        MultipleDetailMessageSentResponse send(Message message, SendRequestConfig config) throws Exception;
    }
}
