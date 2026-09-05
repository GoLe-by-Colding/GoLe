package com.gole.api.community.adapter.in.web;

import com.gole.api.community.domain.model.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class CommunityV2Dtos {

    private CommunityV2Dtos() {}

    @Schema(
            description = "게시글 부분 수정. 누락한 필드는 기존 값을 유지합니다.",
            example =
                    """
                    {
                      "body": "",
                      "mediaKeys": ["images/0194f1c0-15ab-4f33-9b1d-34073d9d7738.jpg"],
                      "visibility": "public",
                      "status": "draft"
                    }
                    """)
    public static final class PatchPostRequest {

        @Size(max = 5000)
        private String body;

        @Size(max = 10)
        private List<@NotBlank @Size(max = 80) String> mediaKeys;

        @Size(max = 16)
        private String visibility;

        @Size(max = 16)
        private String status;

        private boolean bodyProvided;
        private boolean mediaKeysProvided;
        private boolean visibilityProvided;
        private boolean statusProvided;

        public PatchPostRequest() {}

        public String body() {
            return body;
        }

        public void setBody(String body) {
            this.bodyProvided = true;
            this.body = body;
        }

        public List<String> mediaKeys() {
            return mediaKeys;
        }

        public void setMediaKeys(List<String> mediaKeys) {
            this.mediaKeysProvided = true;
            this.mediaKeys = mediaKeys;
        }

        public String visibility() {
            return visibility;
        }

        public void setVisibility(String visibility) {
            this.visibilityProvided = true;
            this.visibility = visibility;
        }

        public String status() {
            return status;
        }

        public void setStatus(String status) {
            this.statusProvided = true;
            this.status = status;
        }

        public boolean bodyProvided() {
            return bodyProvided;
        }

        public boolean mediaKeysProvided() {
            return mediaKeysProvided;
        }

        public boolean visibilityProvided() {
            return visibilityProvided;
        }

        public boolean statusProvided() {
            return statusProvided;
        }
    }

    public record PatchPostResponse(
            String id, String body, List<String> mediaKeys, List<String> imageUrls, String visibility, String status) {

        public static PatchPostResponse from(Post post) {
            return new PatchPostResponse(
                    post.getId(),
                    post.getContent(),
                    post.getImageUrls().stream()
                            .flatMap(value -> com.gole.api.media.domain.model.MediaKey.safeStoredKey(value).stream())
                            .toList(),
                    post.getImageUrls().stream()
                            .flatMap(value -> com.gole.api.media.domain.model.MediaKey.safePublicPath(value).stream())
                            .toList(),
                    "public",
                    post.getStatus().name().toLowerCase());
        }
    }
}
