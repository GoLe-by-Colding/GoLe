package com.gole.api.account.adapter.out.id;

import com.gole.api.account.application.port.out.ConsentEventIdGeneratorPort;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

/** 동의 이벤트 식별자를 MongoDB ObjectId(16진 24자)로 만든다. 생성 순서대로 커져서 같은 시각 결정의 순서를 지킨다. */
@Component
public class ObjectIdConsentEventIdGeneratorAdapter implements ConsentEventIdGeneratorPort {

    @Override
    public String newConsentEventId() {
        return new ObjectId().toHexString();
    }
}
