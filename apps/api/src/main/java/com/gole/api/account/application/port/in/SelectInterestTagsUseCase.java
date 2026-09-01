package com.gole.api.account.application.port.in;

import java.util.Set;

/**
 * Inbound port: 관심 태그 선택. (onboarding R6, D8)
 */
public interface SelectInterestTagsUseCase {

    void select(SelectInterestTagsCommand command);

    record SelectInterestTagsCommand(String accountId, Set<String> tags) {}
}
