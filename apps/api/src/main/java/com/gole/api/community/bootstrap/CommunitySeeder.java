package com.gole.api.community.bootstrap;

import com.gole.api.community.adapter.out.persistence.PostMongoRepository;
import com.gole.api.community.application.port.in.PublishPostUseCase;
import com.gole.api.community.application.port.in.PublishPostUseCase.PublishPostCommand;
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
@ConditionalOnProperty(name = "gole.community.seed-on-empty", havingValue = "true", matchIfMissing = true)
public class CommunitySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CommunitySeeder.class);

    private final PublishPostUseCase publishPost;
    private final PostMongoRepository repository;

    public CommunitySeeder(PublishPostUseCase publishPost, PostMongoRepository repository) {
        this.publishPost = publishPost;
        this.repository = repository;
    }

    private static List<String> img(String label) {
        return List.of("https://placehold.co/800x600/fff8e1/a66f00?text=" + label);
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        List<PublishPostCommand> posts = List.of(
                new PublishPostCommand("user-collector",
                        "드디어 에펠탑 완성! 3일 걸렸네요 🗼", img("Eiffel"), false),
                new PublishPostCommand("user-builder",
                        "밀레니엄 팰컨 UCS 디테일 미쳤습니다. 인생 세트 인정?", img("Falcon"), false),
                new PublishPostCommand("user-moc",
                        "기본 브릭으로 만든 커스텀 등대 MOC 공유합니다. 도면 곧 올릴게요!", img("MOC-Lighthouse"), true),
                new PublishPostCommand("user-collector",
                        "타이타닉 진열장에 올렸어요. 길이 실화냐...", img("Titanic"), false),
                new PublishPostCommand("user-moc",
                        "테크닉 부품으로 동작하는 미니 관람차 MOC 제작기", img("MOC-FerrisWheel"), true));

        posts.forEach(publishPost::publish);
        log.info("[seed] community: {}개 데모 게시글 발행", posts.size());
    }
}
