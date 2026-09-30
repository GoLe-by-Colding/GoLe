package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPostContext;
import java.util.List;

/**
 * 홍보 게시물 초안 작성 유스케이스. (promotion-review P1)
 */
public interface CreatePromotionPostUseCase {

    String create(CreatePromotionPostCommand command);

    /** @param mediaKeys 업로드 스테이지 키(예: {@code images/<uuid>.png}) 목록 — 공개 URL이 아니다.
     *  등록 시 {@code media} 컨텍스트로 PUBLIC 전이·연결한다(promotion-review D8). */
    /** @param context 종류·스크린샷 설명표·출처. 사람이 콘솔에서 쓴 글은 없다({@code NONE}). */
    record CreatePromotionPostCommand(
            String authorId,
            PromotionChannel channel,
            String caption,
            List<String> mediaKeys,
            String sourceCommitSha,
            PromotionPostContext context) {
        public CreatePromotionPostCommand {
            mediaKeys = mediaKeys == null ? List.of() : mediaKeys;
            context = context == null ? PromotionPostContext.NONE : context;
        }

        public CreatePromotionPostCommand(
                String authorId,
                PromotionChannel channel,
                String caption,
                List<String> mediaKeys,
                String sourceCommitSha) {
            this(authorId, channel, caption, mediaKeys, sourceCommitSha, PromotionPostContext.NONE);
        }
    }
}
