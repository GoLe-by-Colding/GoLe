package com.gole.api.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.community.adapter.out.media.MediaPostImageAdapter;
import com.gole.api.community.application.port.in.CommentOnPostUseCase.CommentCommand;
import com.gole.api.community.application.port.in.DeleteCommentUseCase.DeleteCommentCommand;
import com.gole.api.community.application.port.in.GetFeedUseCase.FeedCursor;
import com.gole.api.community.application.port.in.PatchPostUseCase.PatchField;
import com.gole.api.community.application.port.in.PatchPostUseCase.PatchPostCommand;
import com.gole.api.community.application.port.in.PublishPostUseCase.PublishPostCommand;
import com.gole.api.community.application.port.out.BlockedAuthorQueryPort;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommunityServiceTest {

    private InMemoryPosts posts;
    private RecordingPostAuthorNotifier notifier;
    private FakeBlocks blocks;
    private InMemoryComments comments;
    private CommunityService service;

    @BeforeEach
    void setUp() {
        posts = new InMemoryPosts();
        notifier = new RecordingPostAuthorNotifier();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        blocks = new FakeBlocks();
        comments = new InMemoryComments();
        service = new CommunityService(
                posts,
                comments,
                new SeqIds(),
                notifier,
                new MediaPostImageAdapter(mock(ManageMediaAssetsUseCase.class)),
                blocks,
                clock);
    }

    private String publish() {
        return service.publish(new PublishPostCommand("author-1", "내 자랑", List.of("img.jpg"), "showcase"));
    }

    @Test
    void publish_appearsInFeed() {
        publish();
        assertThat(service.feed(30, null)).hasSize(1);
    }

    @Test
    void feedPage_usesCreatedAtAndIdCursor_withoutDuplicates() {
        for (int index = 0; index < 5; index++) {
            publish();
        }

        var first = service.feedPage(2, Optional.empty(), Optional.empty(), Optional.empty(), null);
        assertThat(first.items()).extracting(Post::getId).containsExactly("id-5", "id-4");
        assertThat(first.hasMore()).isTrue();

        Post last = first.items().get(1);
        var second = service.feedPage(
                2,
                Optional.of(new FeedCursor(last.getCreatedAt(), last.getId())),
                Optional.empty(),
                Optional.empty(),
                null);
        assertThat(second.items()).extracting(Post::getId).containsExactly("id-3", "id-2");
        assertThat(second.hasMore()).isTrue();

        Post secondLast = second.items().get(1);
        var finalPage = service.feedPage(
                2,
                Optional.of(new FeedCursor(secondLast.getCreatedAt(), secondLast.getId())),
                Optional.empty(),
                Optional.empty(),
                null);
        assertThat(finalPage.items()).extracting(Post::getId).containsExactly("id-1");
        assertThat(finalPage.hasMore()).isFalse();
    }

    @Test
    void feedPage_filtersEntireFeedByTopicAndQueryBeforePagination() {
        service.publish(new PublishPostCommand("builder-a", "에펠탑 보관 팁", List.of(), "tip"));
        service.publish(new PublishPostCommand("builder-b", "에펠탑 창작 완성", List.of(), "moc"));
        service.publish(new PublishPostCommand("builder-c", "타이타닉 보관 팁", List.of(), "tip"));

        var page = service.feedPage(2, Optional.empty(), Optional.of(PostType.TIP), Optional.of("에펠탑"), null);

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
        assertThat(service.getPost(id, null).likeCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.like(id, "user-1")).isInstanceOf(DuplicateLikeException.class);
        assertThat(service.getPost(id, null).likeCount()).isEqualTo(1);
    }

    @Test
    void unlike_removesViewerReaction_andIsIdempotent() {
        String id = publish();
        service.like(id, "user-1");

        service.unlike(id, "user-1");
        service.unlike(id, "user-1");

        Post post = service.getPost(id, null);
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
        assertThat(service.feed(30, null)).isEmpty();
    }

    @Test
    void comment_isAttachedToPost() {
        String id = publish();
        Comment created = service.comment(new CommentCommand(id, "user-2", "멋져요"));
        assertThat(created.id()).isEqualTo("id-2");
        assertThat(created.authorId()).isEqualTo("user-2");
        assertThat(service.comments(id, 100, null)).hasSize(1);
    }

    @Test
    void hiddenCommentKeepsOriginalButDisappearsFromPublicComments() {
        String postId = publish();
        Comment created = service.comment(new CommentCommand(postId, "user-2", "신고될 원문"));

        service.hide(created.id(), "욕설 포함");

        assertThat(service.comments(postId, 100, null)).isEmpty();
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

        assertThat(service.comments(postId, 100, null)).isEmpty();
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
        assertThat(service.comments(postId, 100, null)).singleElement().isEqualTo(created);
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

    // ---- 차단 (user-block R2~R5, R10, R13) ----

    private void publishAs(String authorId, int count) {
        for (int index = 0; index < count; index++) {
            service.publish(new PublishPostCommand(authorId, "글", List.of(), "general"));
        }
    }

    @Test
    @DisplayName("차단 관계인 작성자의 글은 페이지를 자르기 전에 걸러져 첫 페이지가 꽉 찬다")
    void feedPage_excludesBlockedAuthorsBeforePaging() {
        publishAs("B", 12);
        publishAs("C", 12);
        blocks.block("A", "B");

        var page = service.feedPage(6, Optional.empty(), Optional.empty(), Optional.empty(), "A");

        assertThat(page.items()).hasSize(6).extracting(Post::getAuthorId).containsOnly("C");
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    @DisplayName("차단은 양방향이라 차단당한 쪽의 피드에서도 상대 글이 빠진다")
    void feedPage_excludesBlockedAuthorsInBothDirections() {
        publishAs("A", 3);
        publishAs("C", 3);
        blocks.block("A", "B");

        var page = service.feedPage(6, Optional.empty(), Optional.empty(), Optional.empty(), "B");

        assertThat(page.items()).extracting(Post::getAuthorId).containsOnly("C").hasSize(3);
    }

    @Test
    @DisplayName("feed 는 viewer 가 있을 때만 차단 작성자를 거르고 비로그인에는 모두 보인다")
    void feed_excludesBlockedAuthorsOnlyForLoggedInViewer() {
        publishAs("B", 2);
        publishAs("C", 2);
        blocks.block("A", "B");

        assertThat(service.feed(30, "A")).extracting(Post::getAuthorId).containsOnly("C");
        assertThat(service.feed(30, null)).extracting(Post::getAuthorId).contains("B", "C");
    }

    @Test
    @DisplayName("차단 관계인 글의 상세는 COMMUNITY_BLOCKED 로 막고 해제하면 다시 보인다")
    void getPost_rejectsBlockedAuthorAndRecoversAfterUnblock() {
        String postId = service.publish(new PublishPostCommand("B", "글", List.of(), "general"));
        blocks.block("A", "B");

        assertThatThrownBy(() -> service.getPost(postId, "A"))
                .isInstanceOfSatisfying(
                        ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("COMMUNITY_BLOCKED"));
        assertThat(service.getPost(postId, "C")).isNotNull();
        assertThat(service.getPost(postId, null)).isNotNull();

        blocks.unblock("A", "B");

        assertThat(service.getPost(postId, "A")).isNotNull();
        assertThat(service.feed(30, "A")).extracting(Post::getAuthorId).contains("B");
    }

    @Test
    @DisplayName("댓글 목록은 글 작성자와 차단이면 막고 차단 상대의 댓글만 숨긴다")
    void comments_rejectsBlockedPostAuthorAndHidesBlockedCommenters() {
        String postId = service.publish(new PublishPostCommand("D", "글", List.of(), "general"));
        service.comment(new CommentCommand(postId, "B", "B 댓글"));
        service.comment(new CommentCommand(postId, "C", "C 댓글"));
        blocks.block("A", "B");
        blocks.block("A", "D");

        assertThatThrownBy(() -> service.comments(postId, 100, "A"))
                .isInstanceOfSatisfying(
                        ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("COMMUNITY_BLOCKED"));

        blocks.unblock("A", "D");

        assertThat(service.comments(postId, 100, "A"))
                .extracting(Comment::authorId)
                .containsExactly("C");
        assertThat(service.comments(postId, 100, "C"))
                .extracting(Comment::authorId)
                .containsExactly("B", "C");
        assertThat(service.comments(postId, 100, null)).hasSize(2);
    }

    @Test
    @DisplayName("차단 상대 댓글이 앞에 몰려 있어도 limit만큼 다른 댓글을 채운다")
    void comments_excludesBlockedBeforeLimit() {
        String postId = service.publish(new PublishPostCommand("D", "글", List.of(), "general"));
        service.comment(new CommentCommand(postId, "B", "B 댓글 1"));
        service.comment(new CommentCommand(postId, "B", "B 댓글 2"));
        service.comment(new CommentCommand(postId, "C", "C 댓글 1"));
        service.comment(new CommentCommand(postId, "C", "C 댓글 2"));
        blocks.block("A", "B");

        assertThat(service.comments(postId, 2, "A"))
                .extracting(Comment::content)
                .containsExactly("C 댓글 1", "C 댓글 2");
    }

    @Test
    @DisplayName("차단 관계인 글에는 좋아요·댓글이 막히고 저장도 알림도 없다")
    void likeAndComment_rejectedWhenBlocked_withoutSideEffects() {
        String postId = service.publish(new PublishPostCommand("B", "글", List.of(), "general"));
        blocks.block("A", "B");

        assertThatThrownBy(() -> service.like(postId, "A"))
                .isInstanceOfSatisfying(
                        ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("COMMUNITY_BLOCKED"));
        assertThatThrownBy(() -> service.comment(new CommentCommand(postId, "A", "안녕")))
                .isInstanceOfSatisfying(
                        ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("COMMUNITY_BLOCKED"));

        assertThat(service.getPost(postId, null).likeCount()).isZero();
        assertThat(comments.store).isEmpty();
        assertThat(notifier.likes).isEmpty();
    }

    @Test
    @DisplayName("좋아요 취소는 차단 관계여도 허용한다")
    void unlike_allowedEvenWhenBlocked() {
        String postId = service.publish(new PublishPostCommand("B", "글", List.of(), "general"));
        service.like(postId, "A");
        blocks.block("A", "B");

        service.unlike(postId, "A");

        assertThat(service.getPost(postId, null).isLikedBy("A")).isFalse();
    }

    private static final class FakeBlocks implements BlockedAuthorQueryPort {
        private final Set<String> pairs = new HashSet<>();

        void block(String a, String b) {
            pairs.add(a + ">" + b);
        }

        void unblock(String a, String b) {
            pairs.remove(a + ">" + b);
        }

        @Override
        public boolean blockedBetween(String a, String b) {
            return pairs.contains(a + ">" + b) || pairs.contains(b + ">" + a);
        }

        @Override
        public List<String> blockedCounterparts(String accountId) {
            List<String> result = new ArrayList<>();
            for (String pair : pairs) {
                String[] parts = pair.split(">");
                if (parts[0].equals(accountId)) {
                    result.add(parts[1]);
                } else if (parts[1].equals(accountId)) {
                    result.add(parts[0]);
                }
            }
            return result;
        }
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
        public List<Post> findPublishedRecentFirst(int limit, List<String> excludedAuthorIds) {
            return store.stream()
                    .filter(Post::isPublished)
                    .filter(post -> !excludedAuthorIds.contains(post.getAuthorId()))
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
                Optional<String> query,
                List<String> excludedAuthorIds) {
            return store.stream()
                    .filter(Post::isPublished)
                    .filter(post -> !excludedAuthorIds.contains(post.getAuthorId()))
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
        public List<Comment> findByPostId(String postId, int limit, List<String> excludedAuthorIds) {
            return store.stream()
                    .filter(c -> c.postId().equals(postId))
                    .filter(c -> !c.isHidden())
                    .filter(c -> !excludedAuthorIds.contains(c.authorId()))
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
