package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPostContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 홍보 게시물 초안 작성 유스케이스. (promotion-review P1)
 */
public interface CreatePromotionPostUseCase {

    String create(CreatePromotionPostCommand command);

    /** @param mediaKeys 업로드 스테이지 키(예: {@code images/<uuid>.png}) 목록 — 공개 URL이 아니다.
     *  등록 시 {@code media} 컨텍스트로 PUBLIC 전이·연결한다(promotion-review D8). */
    /** @param context 종류·스크린샷 설명표·출처. 사람이 콘솔에서 쓴 글은 없다({@code NONE}).
     *  @param originals 설명표와 같은 순서의 다듬기 전 원본. 비우면 원본을 그대로 올린 글이다.
     *  원본도 스테이지 키로 받아 게시 이미지와 함께 PUBLIC 전이·연결한다. */
    record CreatePromotionPostCommand(
            String authorId,
            PromotionChannel channel,
            String caption,
            List<String> mediaKeys,
            String sourceCommitSha,
            PromotionPostContext context,
            List<CaptureOriginal> originals) {
        public CreatePromotionPostCommand {
            mediaKeys = mediaKeys == null ? List.of() : mediaKeys;
            context = context == null ? PromotionPostContext.NONE : context;
            // 원본 없이 올린 사진 자리는 null 이라 List.copyOf 를 쓰지 못한다.
            originals = originals == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(originals));
        }

        public CreatePromotionPostCommand(
                String authorId,
                PromotionChannel channel,
                String caption,
                List<String> mediaKeys,
                String sourceCommitSha,
                PromotionPostContext context) {
            this(authorId, channel, caption, mediaKeys, sourceCommitSha, context, List.of());
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

    /** 다듬기 전 원본 한 장. 원본을 그대로 올린 사진 자리는 null 로 둔다. */
    record CaptureOriginal(String mediaKey, String edit) {}
}
