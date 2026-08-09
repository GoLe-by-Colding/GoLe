package com.gole.api.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.community.application.port.in.CommentOnPostUseCase.CommentCommand;
import com.gole.api.community.application.port.in.PatchPostUseCase.PatchField;
import com.gole.api.community.application.port.in.PatchPostUseCase.PatchPostCommand;
import com.gole.api.community.application.port.in.PublishPostUseCase.PublishPostCommand;
import com.gole.api.community.application.port.out.CommentRepositoryPort;
import com.gole.api.community.application.port.out.CommunityIdGeneratorPort;
import com.gole.api.community.application.port.out.PostRepositoryPort;
import com.gole.api.community.domain.exception.DuplicateLikeException;
import com.gole.api.community.domain.exception.PostContentRequiredException;
import com.gole.api.community.domain.model.Comment;
import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostStatus;
import com.gole.api.community.domain.model.PostType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommunityServiceTest {

    private InMemoryPosts posts;
    private CommunityService service;

    @BeforeEach
    void setUp() {
        posts = new InMemoryPosts();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new CommunityService(posts, new InMemoryComments(), new SeqIds(), (authorId, postId) -> {}, clock);
    }

    private String publish() {
        return service.publish(new PublishPostCommand("author-1", "내 자랑", List.of("img.jpg"), "showcase"));
    }

    @Test
    void publish_appearsInFeed() {
        publish();
        assertThat(service.feed(30)).hasSize(1);
    }

    @Test
    void like_incrementsOnce_andRejectsDuplicate() {
        String id = publish();
        service.like(id, "user-1");
        assertThat(service.getPost(id).likeCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.like(id, "user-1")).isInstanceOf(DuplicateLikeException.class);
        assertThat(service.getPost(id).likeCount()).isEqualTo(1);
    }

    @Test
    void delete_byNonAuthor_isForbidden_byAuthor_removesFromFeed() {
        String id = publish();
        assertThatThrownBy(() -> service.delete(id, "intruder")).isInstanceOf(ForbiddenException.class);
        service.delete(id, "author-1");
        assertThat(service.feed(30)).isEmpty();
    }

    @Test
    void comment_isAttachedToPost() {
        String id = publish();
        Comment created = service.comment(new CommentCommand(id, "user-2", "멋져요"));
        assertThat(created.id()).isEqualTo("id-2");
        assertThat(created.authorId()).isEqualTo("user-2");
        assertThat(service.comments(id, 100)).hasSize(1);
    }

    @Test
    void patch_omittedBody_keepsExistingDraftBody() {
        draft("draft-1", "기존 본문", List.of("old.jpg"));

        Post updated = service.patch(new PatchPostCommand(
                "draft-1",
                "author-1",
                PatchField.omitted(),
                PatchField.provided(List.of("new.jpg")),
                PatchField.omitted()));

        assertThat(updated.getContent()).isEqualTo("기존 본문");
        assertThat(updated.getImageUrls()).containsExactly("new.jpg");
        assertThat(updated.getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    void patch_emptyBody_clearsDraftAndKeepsPhotos() {
        draft("draft-1", "지울 본문", List.of("community/example.jpg"));

        Post updated = service.patch(new PatchPostCommand(
                "draft-1",
                "author-1",
                PatchField.provided(""),
                PatchField.provided(List.of("community/example.jpg")),
                PatchField.provided(PostStatus.DRAFT)));

        assertThat(updated.getContent()).isEmpty();
        assertThat(updated.getImageUrls()).containsExactly("community/example.jpg");
        assertThat(updated.getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    void patch_emptyBody_rejectsPublishingDraft() {
        draft("draft-1", "기존 본문", List.of("photo.jpg"));

        assertThatThrownBy(() -> service.patch(new PatchPostCommand(
                        "draft-1",
                        "author-1",
                        PatchField.provided(""),
                        PatchField.omitted(),
                        PatchField.provided(PostStatus.PUBLISHED))))
                .isInstanceOf(PostContentRequiredException.class)
                .hasMessage("발행 게시글은 본문이 필요합니다");
    }

    @Test
    void patch_emptyBody_rejectsWhenExistingStatusIsPublished() {
        String id = publish();

        assertThatThrownBy(() -> service.patch(new PatchPostCommand(
                        id, "author-1", PatchField.provided(""), PatchField.omitted(), PatchField.omitted())))
                .isInstanceOf(PostContentRequiredException.class);
    }

    private void draft(String id, String content, List<String> photos) {
        posts.save(new Post(
                id,
                "author-1",
                content,
                photos,
                PostType.GENERAL,
                PostStatus.DRAFT,
                java.util.Set.of(),
                Instant.parse("2026-01-01T00:00:00Z")));
    }

    private static final class InMemoryPosts implements PostRepositoryPort {
        private final List<Post> store = new ArrayList<>();

        @Override
        public Post save(Post post) {
            store.removeIf(p -> p.getId().equals(post.getId()));
            store.add(post);
            return post;
        }

        @Override
        public Optional<Post> findById(String postId) {
            return store.stream().filter(p -> p.getId().equals(postId)).findFirst();
        }

        @Override
        public List<Post> findPublishedRecentFirst(int limit) {
            return store.stream()
                    .filter(Post::isPublished)
                    .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    private static final class InMemoryComments implements CommentRepositoryPort {
        private final List<Comment> store = new ArrayList<>();

        @Override
        public Comment save(Comment comment) {
            store.add(comment);
            return comment;
        }

        @Override
        public List<Comment> findByPostId(String postId, int limit) {
            return store.stream()
                    .filter(c -> c.postId().equals(postId))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class SeqIds implements CommunityIdGeneratorPort {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public String newId() {
            return "id-" + n.incrementAndGet();
        }
    }
}
