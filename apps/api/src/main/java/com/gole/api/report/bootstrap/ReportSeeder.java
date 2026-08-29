package com.gole.api.report.bootstrap;

import com.gole.api.community.adapter.out.persistence.PostDocument;
import com.gole.api.community.adapter.out.persistence.PostMongoRepository;
import com.gole.api.listing.adapter.out.persistence.ListingDocument;
import com.gole.api.listing.adapter.out.persistence.ListingMongoRepository;
import com.gole.api.report.adapter.out.persistence.ReportMongoRepository;
import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase.SubmitReportCommand;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportTargetType;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 신고 시드 데이터. 컬렉션이 비어 있을 때만 데모 신고를 접수한다(멱등).
 *
 * <p>운영자 콘솔의 신고 큐는 플랫폼의 핵심 운영 동선인데, 시드가 없으면 로컬에서 늘 빈 화면이라
 * 조치 흐름(조치완료/기각 → 감사 로그)을 확인할 수 없다. 그래서 실제 시드된 매물·게시글을
 * 대상으로 삼아 클릭하면 대상 화면으로 이동까지 되도록 만든다.
 *
 * <p>매물·게시글 시드 이후에 실행되어야 하므로 {@code @Order}는 커뮤니티(4)·리뷰(6)보다 뒤다.
 */
@Component
@Order(7)
@ConditionalOnProperty(name = "gole.report.seed-on-empty", havingValue = "true")
public class ReportSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReportSeeder.class);

    private final SubmitReportUseCase submitReport;
    private final ReportMongoRepository reportRepository;
    private final ListingMongoRepository listingRepository;
    private final PostMongoRepository postRepository;

    public ReportSeeder(
            SubmitReportUseCase submitReport,
            ReportMongoRepository reportRepository,
            ListingMongoRepository listingRepository,
            PostMongoRepository postRepository) {
        this.submitReport = submitReport;
        this.reportRepository = reportRepository;
        this.listingRepository = listingRepository;
        this.postRepository = postRepository;
    }

    @Override
    public void run(String... args) {
        if (reportRepository.count() > 0) {
            return;
        }

        List<ListingDocument> listings = listingRepository.findAll();
        List<PostDocument> posts = postRepository.findAll();
        if (listings.isEmpty() && posts.isEmpty()) {
            return; // 대상이 없으면 신고도 만들지 않는다.
        }

        List<SubmitReportCommand> commands = new ArrayList<>();

        if (!listings.isEmpty()) {
            commands.add(new SubmitReportCommand(
                    "user-collector",
                    ReportTargetType.LISTING,
                    listings.getFirst().getId(),
                    ReportReason.COUNTERFEIT,
                    "스터드 각인이 흐릿하고 박스 인쇄 색감이 정품과 다릅니다. 레핀 의심."));
        }
        if (listings.size() > 1) {
            commands.add(new SubmitReportCommand(
                    "user-builder",
                    ReportTargetType.LISTING,
                    listings.get(1).getId(),
                    ReportReason.IP_INFRINGEMENT,
                    "상세 사진이 공식 제품 렌더 이미지 그대로입니다. 실물 사진이 아님."));
        }
        if (listings.size() > 2) {
            commands.add(new SubmitReportCommand(
                    "user-newbie",
                    ReportTargetType.LISTING,
                    listings.get(2).getId(),
                    ReportReason.FRAUD,
                    "시세 대비 지나치게 저렴하고 선입금만 요구합니다."));
        }
        if (!posts.isEmpty()) {
            commands.add(new SubmitReportCommand(
                    "user-moc",
                    ReportTargetType.POST,
                    posts.getFirst().getId(),
                    ReportReason.INAPPROPRIATE,
                    "본문에 외부 판매 링크를 반복 게시하는 스팸입니다."));
        }

        commands.forEach(submitReport::submit);
        log.info("[seed] report: {}건 데모 신고 접수(PENDING)", commands.size());
    }
}
