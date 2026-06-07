package com.gole.api.discovery.domain.exception;

import com.gole.api.common.exception.ConflictException;

/** 요구사항 16.4: 이미 팔로우한 셀러. */
public class DuplicateFollowException extends ConflictException {

    public DuplicateFollowException() {
        super("DUPLICATE_FOLLOW", "Already following this seller");
    }
}
