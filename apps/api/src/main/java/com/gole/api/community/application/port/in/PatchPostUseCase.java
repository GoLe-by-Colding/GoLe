package com.gole.api.community.application.port.in;

import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostStatus;
import java.util.List;

/** Inbound port: v2 게시글 부분 수정. 필드 누락과 명시적 값을 구분한다. */
public interface PatchPostUseCase {

    Post patch(PatchPostCommand command);

    record PatchPostCommand(
            String postId,
            String requesterId,
            PatchField<String> body,
            PatchField<List<String>> photos,
            PatchField<PostStatus> status) {}

    /**
     * {@link java.util.Optional}은 null을 빈 값으로 접어 버리므로 PATCH의 "미전달" 의미를 보존하지 못한다.
     * 이 값 객체는 JSON 필드 존재 여부와 값을 각각 전달한다.
     */
    record PatchField<T>(boolean provided, T value) {

        public static <T> PatchField<T> omitted() {
            return new PatchField<>(false, null);
        }

        public static <T> PatchField<T> provided(T value) {
            return new PatchField<>(true, value);
        }
    }
}
