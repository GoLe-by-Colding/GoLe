package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.GetEmailAuthenticationAvailabilityUseCase;
import com.gole.api.account.config.EmailAuthenticationAvailability;
import org.springframework.stereotype.Service;

/** 이메일 인증 가용성 조회. 판정은 account 설정({@link EmailAuthenticationAvailability})이 한다. */
@Service
public class EmailAuthenticationAvailabilityService implements GetEmailAuthenticationAvailabilityUseCase {

    private final EmailAuthenticationAvailability availability;

    public EmailAuthenticationAvailabilityService(EmailAuthenticationAvailability availability) {
        this.availability = availability;
    }

    @Override
    public boolean available() {
        return availability.available();
    }
}
