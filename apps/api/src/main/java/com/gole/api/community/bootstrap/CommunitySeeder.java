package com.gole.api.community.bootstrap;

import static com.gole.api.common.bootstrap.DemoContentActors.USER_BUILDER;
import static com.gole.api.common.bootstrap.DemoContentActors.USER_COLLECTOR;
import static com.gole.api.common.bootstrap.DemoContentActors.USER_MOC;
import static com.gole.api.common.bootstrap.DemoContentActors.USER_NEWBIE;

import com.gole.api.community.adapter.out.persistence.PostMongoRepository;
import com.gole.api.community.application.port.in.PublishPostUseCase.PublishPostCommand;
import com.gole.api.community.application.port.out.CommunityIdGeneratorPort;
import com.gole.api.community.application.port.out.PostRepositoryPort;
import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 시드 데이터. 컬렉션이 비어 있을 때만 데모 게시글(자랑/MOC)을 발행한다(멱등).
 */
@Component
@Order(4)
@ConditionalOnProperty(name = "gole.community.seed-on-empty", havingValue = "true")
public class CommunitySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CommunitySeeder.class);

    private final PostRepositoryPort posts;
    private final CommunityIdGeneratorPort ids;
    private final PostMongoRepository repository;
    private final Clock clock;

    public CommunitySeeder(
            PostRepositoryPort posts, CommunityIdGeneratorPort ids, PostMongoRepository repository, Clock clock) {
        this.posts = posts;
        this.ids = ids;
        this.repository = repository;
        this.clock = clock;
    }

    private static List<String> img(String slug) {
        // GoLe 오리지널 데모 커버(MediaSeeder가 MinIO에 업로드). 공식 이미지 미사용.
        return List.of("community/" + slug + ".svg");
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        List<PublishPostCommand> seeds = List.of(
                new PublishPostCommand(USER_COLLECTOR, "드디어 에펠탑 완성! 3일 걸렸네요 🗼", img("eiffel"), "showcase"),
                new PublishPostCommand(USER_BUILDER, "밀레니엄 팰컨 UCS 디테일 미쳤습니다. 인생 세트 인정?", img("falcon"), "review"),
                new PublishPostCommand(
                        USER_MOC, "기본 브릭으로 만든 커스텀 등대 MOC 공유합니다. 도면 곧 올릴게요!", img("moc-lighthouse"), "moc"),
                new PublishPostCommand(USER_COLLECTOR, "타이타닉 진열장에 올렸어요. 길이 실화냐...", img("titanic"), "showcase"),
                new PublishPostCommand(USER_MOC, "테크닉 부품으로 동작하는 미니 관람차 MOC 제작기", img("moc-ferriswheel"), "moc"),
                new PublishPostCommand(USER_NEWBIE, "입문자인데 첫 세트로 뭐가 좋을까요? 예산 5만원 이하 추천 부탁드려요!", List.of(), "question"),
                new PublishPostCommand(USER_COLLECTOR, "[꿀팁] 레고 보관할 때 햇빛 직사광선 피하세요. 변색 진짜 빨라요.", List.of(), "tip"),
                new PublishPostCommand(
                        USER_BUILDER, "10307 에펠탑 박스 안쪽에 디자이너 사인 이스터에그 있는 거 아셨나요? 👀", List.of(), "easter_egg"));

        seeds.stream().map(this::post).forEach(posts::save);
        log.info("[seed] community: {}개 데모 게시글 발행", seeds.size());
    }

    private Post post(PublishPostCommand seed) {
        return Post.publish(
                ids.newId(),
                seed.authorId(),
                seed.content(),
                seed.imageKeys(),
                PostType.fromKey(seed.topic()),
                Instant.now(clock));
    }
}
