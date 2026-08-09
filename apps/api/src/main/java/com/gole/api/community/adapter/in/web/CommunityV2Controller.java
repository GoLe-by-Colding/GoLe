package com.gole.api.community.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.community.adapter.in.web.CommunityV2Dtos.PatchPostRequest;
import com.gole.api.community.adapter.in.web.CommunityV2Dtos.PatchPostResponse;
import com.gole.api.community.application.port.in.PatchPostUseCase;
import com.gole.api.community.application.port.in.PatchPostUseCase.PatchField;
import com.gole.api.community.application.port.in.PatchPostUseCase.PatchPostCommand;
import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** v2 호환 API: draft를 포함한 게시글 부분 수정 계약을 제공한다. */
@Tag(name = "Community v2", description = "커뮤니티 게시글 임시저장·부분 수정")
@RestController
@RequestMapping("/api/v2/community/posts")
public class CommunityV2Controller {

    private final PatchPostUseCase patchPostUseCase;

    public CommunityV2Controller(PatchPostUseCase patchPostUseCase) {
        this.patchPostUseCase = patchPostUseCase;
    }

    @Operation(
            summary = "게시글 부분 수정",
            description = "필드 미전달은 기존 값을 유지합니다. body의 빈 문자열은 최종 상태가 draft일 때만 허용하며, "
                    + "published에는 공백이 아닌 본문이 필요합니다. visibility는 현재 public만 지원합니다.")
    @PatchMapping("/{postId}")
    public PatchPostResponse patch(
            @PathVariable String postId, @Valid @RequestBody PatchPostRequest request, HttpServletRequest http) {
        validateVisibility(request);
        Post updated = patchPostUseCase.patch(new PatchPostCommand(
                postId,
                AuthenticatedUser.id(http),
                field(request.bodyProvided(), request.body()),
                field(request.photosProvided(), request.photos()),
                statusField(request)));
        return PatchPostResponse.from(updated);
    }

    private static void validateVisibility(PatchPostRequest request) {
        if (request.visibilityProvided()
                && (request.visibility() == null || !"public".equalsIgnoreCase(request.visibility()))) {
            throw new BadRequestException("INVALID_POST_VISIBILITY", "visibility는 public만 지원합니다");
        }
    }

    private static PatchField<PostStatus> statusField(PatchPostRequest request) {
        if (!request.statusProvided()) {
            return PatchField.omitted();
        }
        if (request.status() == null) {
            return PatchField.provided(null);
        }
        try {
            PostStatus status = PostStatus.valueOf(request.status().toUpperCase(Locale.ROOT));
            if (status == PostStatus.DELETED) {
                throw invalidStatus();
            }
            return PatchField.provided(status);
        } catch (IllegalArgumentException exception) {
            throw invalidStatus();
        }
    }

    private static BadRequestException invalidStatus() {
        return new BadRequestException("INVALID_POST_STATUS", "status는 draft 또는 published여야 합니다");
    }

    private static <T> PatchField<T> field(boolean provided, T value) {
        return provided ? PatchField.provided(value) : PatchField.omitted();
    }
}
