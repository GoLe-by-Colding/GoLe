package com.gole.api.admin.application.port.out;

import com.gole.api.admin.domain.model.AdminAction;
import java.util.List;

/**
 * Outbound port: 감사 로그 저장소. (admin-console 요구사항 8.4)
 *
 * <p>의도적으로 <b>추가와 조회만</b> 노출한다. 수정/삭제 메서드가 없는 것이 이 포트의 계약이며,
 * 그래서 감사 로그를 지우는 API를 만들 수 없다.
 */
public interface AdminAuditPort {

    void append(AdminAction action);

    /** 최근 발생순 목록. */
    List<AdminAction> findRecent(int limit);
}
