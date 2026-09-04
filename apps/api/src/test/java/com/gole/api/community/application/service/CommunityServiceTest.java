package com.gole.api.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.community.application.port.in.CommentOnPostUseCase.CommentCommand;
import com.gole.api.community.application.port.in.DeleteCommentUseCase.DeleteCommentCommand;
import com.gole.api.community.application.port.in.GetFeedUseCase.FeedCursor;
import com.gole.api.community.application.port.in.PatchPostUseCase.PatchField;
import com.gole.api.community.application.port.in.PatchPostUseCase.PatchPostCommand;
import com.gole.api.community.application.port.in.PublishPostUseCase.PublishPostCommand;
import com.gole.api.community.application.port.out.CommentRepositoryPort;
import com.gole.api.community.application.port.out.CommunityIdGeneratorPort;
import com.gole.api.community.application.port.out.PostAuthorNotifierPort;
import com.gole.api.community.application.port.out.PostRepositoryPort;
import com.gole.api.community.domain.exception.DuplicateLikeException;
import com.gole.api.community.domain.exception.PostContentRequiredException;
import com.gole.api.community.domain.model.Comment;
import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostStatus;
import com.gole.api.community.domain.model.PostType;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
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
    private RecordingPostAuthorNotifier notifier;
    private CommunityService service;

    @BeforeEach
    void setUp() {
        posts = new InMemoryPosts();
        notifier = new RecordingPostAuthorNotifier();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new CommunityService(
                posts, new InMemoryComments(), new SeqIds(), notifier, mock(ManageMediaAssetsUseCase.class), clock);
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
    void feedPage_usesCreatedAtAndIdCursor_withoutDuplicates() {
        for (int index = 0; index < 5; index++) {
            publish();
        }

        var first = service.feedPage(2, Optional.empty());
        assertThat(first.items()).extracting(Post::getId).containsExactly("id-5", "id-4");
        assertThat(first.hasMore()).isTrue();

        Post last = first.items().get(1);
        var second = service.feedPage(2, Optional.of(new FeedCursor(last.getCreatedAt(), last.getId())));
        assertThat(second.items()).extracting(Post::getId).containsExactly("id-3", "id-2");
        assertThat(second.hasMore()).isTrue();

        Post secondLast = second.items().get(1);
        var finalPage = service.feedPage(2, Optional.of(new FeedCursor(secondLast.getCreatedAt(), secondLast.getId())));
        assertThat(finalPage.items()).extracting(Post::getId).containsExactly("id-1");
        assertThat(finalPage.hasMore()).isFalse();
    }

    @Test
    void feedPage_filtersEntireFeedByTopicAndQueryBeforePagination() {
        service.publish(new PublishPostCommand("builder-a", "에펠탑 보관 팁", List.of(), "tip"));
        service.publish(new PublishPostCommand("builder-b", "에펠탑 창작 완성", List.of(), "moc"));
        service.publish(new PublishPostCommand("builder-c", "타이타닉 보관 팁", List.of(), "tip"));

        var page = service.feedPage(2, Optional.empty(), Optional.of(PostType.TIP), Optional.of("에펠탑"));

        assertThat(page.items()).singleElement().satisfies(post -> {
            assertThat(post.getAuthorId()).isEqualTo("builder-a");
            assertThat(post.getContent()).contains("에펠탑");
        });
        assertThat(page.hasMore()).isFalse();
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
    void unlike_removesViewerReaction_andIsIdempotent() {
        String id = publish();
        service.like(id, "user-1");

        service.unlike(id, "user-1");
        service.unlike(id, "user-1");

        Post post = service.getPost(id);
        assertThat(post.likeCount()).isZero();
        assertThat(post.isLikedBy("user-1")).isFalse();
    }

    @Test
    void like_notifiesAuthorOnceThroughNotifier_butSelfLikeDoesNot() {
        String id = publish();

        service.like(id, "viewer-1");

        assertThat(notifier.likes).containsExactly("author-1:" + id + ":viewer-1");

        String ownPostId = service.publish(new PublishPostCommand("self", "내 글", List.of(), "general"));
        service.like(ownPostId, "self");
        assertThat(notifier.likes).hasSize(1);
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
    void hiddenCommentKeepsOriginalButDisappearsFromPublicComments() {
        String postId = publish();
        Comment created = service.comment(new CommentCommand(postId, "user-2", "신고될 원문"));

        service.hide(created.id(), "욕설 포함");

        assertThat(service.comments(postId, 100)).isEmpty();
        Comment preserved = service.getForModeration(created.id());
        assertThat(preserved.content()).isEqualTo("신고될 원문");
        assertThat(preserved.hiddenReason()).isEqualTo("욕설 포함");
        assertThat(preserved.isHidden()).isTrue();
    }

    @Test
    void deleteComment_byAuthorHidesPublicCopy_andPreservesOriginal() {
        String postId = publish();
        Comment created = service.comment(new CommentCommand(postId, "user-2", "나중에 지울 댓글"));

        service.deleteComment(new DeleteCommentCommand(postId, created.id(), "user-2"));

        assertThat(service.comments(postId, 100)).isEmpty();
        Comment preserved = service.getForModeration(created.id());
        assertThat(preserved.content()).isEqualTo("나중에 지울 댓글");
        assertThat(preserved.hiddenReason()).isEqualTo("작성자가 삭제함");
    }

    @Test
    void deleteComment_byAnotherUser_isForbidden() {
        String postId = publish();
        Comment created = service.comment(new CommentCommand(postId, "user-2", "내 댓글"));

        assertThatThrownBy(() -> service.deleteComment(new DeleteCommentCommand(postId, created.id(), "intruder")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(service.comments(postId, 100)).singleElement().isEqualTo(created);
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
                    .sorted(Comparator.comparing(Post::getCreatedAt)
                            .thenComparing(Post::getId)
                            .reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Post> findPublishedPage(
                int limit,
                Optional<PostRepositoryPort.FeedCursor> before,
                Optional<PostType> topic,
                Optional<String> query) {
            return store.stream()
                    .filter(Post::isPublished)
                    .filter(post -> topic.map(value -> post.getType() == value).orElse(true))
                    .filter(post -> query.map(value -> {
                                String needle = value.toLowerCase(java.util.Locale.ROOT);
                                return post.getContent()
                                                .toLowerCase(java.util.Locale.ROOT)
                                                .contains(needle)
                                        || post.getAuthorId()
                                                .toLowerCase(java.util.Locale.ROOT)
                                                .contains(needle);
                            })
                            .orElse(true))
                    .filter(post -> before.map(cursor -> post.getCreatedAt().isBefore(cursor.createdAt())
                                    || (post.getCreatedAt().equals(cursor.createdAt())
                                            && post.getId().compareTo(cursor.postId()) < 0))
                            .orElse(true))
                    .sorted(Comparator.comparing(Post::getCreatedAt)
                            .thenComparing(Post::getId)
                            .reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Post> findPublishedByAuthorIdsRecentFirst(List<String> authorIds, int limit) {
            return store.stream()
                    .filter(Post::isPublished)
                    .filter(post -> authorIds.contains(post.getAuthorId()))
                    .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    private static final class InMemoryComments implements CommentRepositoryPort {
        private final List<Comment> store = new ArrayList<>();

        @Override
        public Comment save(Comment comment) {
            store.removeIf(existing -> existing.id().equals(comment.id()));
            store.add(comment);
            return comment;
        }

        @Override
        public Optional<Comment> findById(String commentId) {
            return store.stream()
                    .filter(comment -> comment.id().equals(commentId))
                    .findFirst();
        }

        @Override
        public List<Comment> findByPostId(String postId, int limit) {
            return store.stream()
                    .filter(c -> c.postId().equals(postId))
                    .filter(c -> !c.isHidden())
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

    private static final class RecordingPostAuthorNotifier implements PostAuthorNotifierPort {
        private final List<String> likes = new ArrayList<>();

        @Override
        public void notifyComment(String authorId, String postId) {}

        @Override
        public void notifyLike(String authorId, String postId, String actorId) {
            likes.add(authorId + ":" + postId + ":" + actorId);
        }
    }
}
