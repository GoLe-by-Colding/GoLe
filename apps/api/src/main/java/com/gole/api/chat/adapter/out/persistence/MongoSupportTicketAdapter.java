package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportNotice;
import com.gole.api.chat.domain.model.SupportNoticeFilter;
import com.gole.api.chat.domain.model.SupportNoticeKind;
import com.gole.api.chat.domain.model.SupportNoticeScope;
import com.gole.api.chat.domain.model.SupportNoticeSummary;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.ConflictException;
import com.mongodb.MongoException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class MongoSupportTicketAdapter implements SupportTicketRepositoryPort {

    private final SupportTicketMongoRepository tickets;
    private final MongoTemplate mongo;

    public MongoSupportTicketAdapter(SupportTicketMongoRepository tickets, MongoTemplate mongo) {
        this.tickets = tickets;
        this.mongo = mongo;
    }

    @Override
    public Optional<SupportTicket> findByRoomId(String roomId) {
        return tickets.findById(roomId).map(MongoSupportTicketAdapter::toDomain);
    }

    @Override
    public List<SupportTicket> findByRoomIds(List<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        return tickets.findAllById(roomIds).stream()
                .map(MongoSupportTicketAdapter::toDomain)
                .toList();
    }

    @Override
    public List<SupportTicket> findByParticipant(String accountId, int limit) {
        var page = PageRequest.of(0, Math.clamp(limit, 1, 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        return tickets.findByParticipant(accountId, page).stream()
                .map(MongoSupportTicketAdapter::toDomain)
                .toList();
    }

    @Override
    public SupportTicket save(SupportTicket ticket) {
        try {
            return toDomain(tickets.save(toDocument(ticket)));
        } catch (OptimisticLockingFailureException concurrentChange) {
            throw new ConflictException("SUPPORT_CONCURRENT_UPDATE", "문의가 다른 관리자에 의해 변경되었습니다. 새로고침해 주세요");
        } catch (DataAccessException failure) {
            // 트랜잭션 스냅샷 이후 인수·답변이 겹친 경우 버전 불일치 대신 Mongo 112가 발생한다.
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof MongoException mongoFailure && mongoFailure.getCode() == 112) {
                    throw new ConflictException("SUPPORT_CONCURRENT_UPDATE", "문의가 다른 관리자에 의해 변경되었습니다. 새로고침해 주세요");
                }
            }
            throw failure;
        }
    }

    @Override
    public List<SupportTicket> findByStatus(SupportStatus status, int limit) {
        var page = PageRequest.of(0, Math.clamp(limit, 1, 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<SupportTicketDocument> rows =
                status == null ? tickets.findBy(page) : tickets.findByStatus(status.name(), page);
        return rows.stream().map(MongoSupportTicketAdapter::toDomain).toList();
    }

    @Override
    public long countByStatus(SupportStatus status) {
        return tickets.countByStatus(status.name());
    }

    @Override
    public List<SupportTicket> findByNotice(
            SupportStatus status,
            SupportCategory category,
            SupportNoticeFilter notice,
            SupportNoticeScope scope,
            Instant now,
            int limit) {
        List<Criteria> conditions = new ArrayList<>();
        List<String> kinds =
                notice.kind() == SupportNoticeKind.PROGRESS ? List.of("PROGRESS", "RESULT") : List.of("RESULT");
        conditions.add(Criteria.where("notices.kind").nin(kinds));
        Instant cutoff = now.minus(notice.kind().targetAfter());
        if (!notice.missingOnly())
            conditions.add(
                    notice.overdue()
                            ? Criteria.where("createdAt").lt(cutoff)
                            : Criteria.where("createdAt")
                                    .gte(cutoff)
                                    .lte(cutoff.plus(notice.kind().warningWindow())));
        if (scope == SupportNoticeScope.ACTIVE)
            conditions.add(Criteria.where("status").ne(SupportStatus.RESOLVED.name()));
        if (scope == SupportNoticeScope.RESOLVED)
            conditions.add(Criteria.where("status").is(SupportStatus.RESOLVED.name()));
        if (status != null) conditions.add(Criteria.where("status").is(status.name()));
        if (category == SupportCategory.GENERAL) {
            conditions.add(new Criteria()
                    .orOperator(
                            Criteria.where("category").is(null),
                            Criteria.where("category").is("GENERAL")));
        } else if (category != null) {
            conditions.add(Criteria.where("category").is(category.name()));
        }
        Query query = Query.query(new Criteria().andOperator(conditions))
                .with(Sort.by(Sort.Direction.ASC, "createdAt", "_id"))
                .limit(Math.clamp(limit, 1, 100));
        return mongo.find(query, SupportTicketDocument.class).stream()
                .map(MongoSupportTicketAdapter::toDomain)
                .toList();
    }

    @Override
    public SupportNoticeSummary noticeSummary(Instant now) {
        Document project = new Document("createdAt", 1)
                .append("status", 1)
                .append("progressAt", firstNoticeExpression(List.of("PROGRESS", "RESULT")))
                .append("resultAt", firstNoticeExpression(List.of("RESULT")));
        Document states = new Document(
                        "progress", noticeStateExpression("$progressAt", SupportNoticeKind.PROGRESS, now))
                .append("result", noticeStateExpression("$resultAt", SupportNoticeKind.RESULT, now))
                .append("status", 1);
        Document group = new Document("_id", null).append("total", new Document("$sum", 1));
        for (String kind : List.of("progress", "result")) {
            for (String state : List.of("pending", "dueSoon", "overdue", "onTime", "late")) {
                group.append(
                        kind + state,
                        new Document(
                                "$sum",
                                new Document("$cond", List.of(new Document("$eq", List.of("$" + kind, state)), 1, 0))));
                group.append(
                        "active" + kind + state,
                        new Document(
                                "$sum",
                                new Document(
                                        "$cond",
                                        List.of(
                                                new Document(
                                                        "$and",
                                                        List.of(
                                                                new Document("$ne", List.of("$status", "RESOLVED")),
                                                                new Document("$eq", List.of("$" + kind, state)))),
                                                1,
                                                0))));
            }
            group.append(
                    "resolvedMissing" + kind,
                    new Document(
                            "$sum",
                            new Document(
                                    "$cond",
                                    List.of(
                                            new Document(
                                                    "$and",
                                                    List.of(
                                                            new Document("$eq", List.of("$status", "RESOLVED")),
                                                            new Document(
                                                                    "$in",
                                                                    List.of(
                                                                            "$" + kind,
                                                                            List.of(
                                                                                    "pending", "dueSoon",
                                                                                    "overdue"))))),
                                            1,
                                            0))));
        }
        Aggregation aggregation = Aggregation.newAggregation(
                context -> new Document("$project", project),
                context -> new Document("$project", states),
                context -> new Document("$group", group));
        Document row = mongo.aggregate(aggregation, SupportTicketDocument.class, Document.class)
                .getUniqueMappedResult();
        if (row == null) row = new Document();
        return new SupportNoticeSummary(
                now,
                number(row, "total"),
                counts(row, "progress"),
                counts(row, "result"),
                counts(row, "activeprogress"),
                counts(row, "activeresult"),
                number(row, "resolvedMissingprogress"),
                number(row, "resolvedMissingresult"));
    }

    private static Document firstNoticeExpression(List<String> kinds) {
        Document filtered = new Document(
                "$filter",
                new Document("input", new Document("$ifNull", List.of("$notices", List.of())))
                        .append("as", "notice")
                        .append("cond", new Document("$in", List.of("$$notice.kind", kinds))));
        return new Document(
                "$min",
                new Document(
                        "$map",
                        new Document("input", filtered).append("as", "notice").append("in", "$$notice.sentAt")));
    }

    private static Document noticeStateExpression(String sentAt, SupportNoticeKind kind, Instant now) {
        Document dueAt =
                new Document("$add", List.of("$createdAt", kind.targetAfter().toMillis()));
        Document missingState = new Document(
                "$cond",
                List.of(
                        new Document("$lt", List.of(dueAt, Date.from(now))),
                        "overdue",
                        new Document(
                                "$cond",
                                List.of(
                                        new Document("$lte", List.of(dueAt, Date.from(now.plus(kind.warningWindow())))),
                                        "dueSoon",
                                        "pending"))));
        // $min([])은 null이다. Java List.of는 null을 허용하지 않으므로 BSON 문서로 null 비교를 구성한다.
        Document missing = new Document("$eq", java.util.Arrays.asList(sentAt, null));
        return new Document(
                "$cond",
                List.of(
                        missing,
                        missingState,
                        new Document(
                                "$cond", List.of(new Document("$lte", List.of(sentAt, dueAt)), "onTime", "late"))));
    }

    private static SupportNoticeSummary.Counts counts(Document row, String prefix) {
        return new SupportNoticeSummary.Counts(
                number(row, prefix + "pending"),
                number(row, prefix + "dueSoon"),
                number(row, prefix + "overdue"),
                number(row, prefix + "onTime"),
                number(row, prefix + "late"));
    }

    private static long number(Document row, String key) {
        return row.get(key) instanceof Number value ? value.longValue() : 0;
    }

    @Override
    public List<SupportTicket> findByStatusAndCategory(SupportStatus status, SupportCategory category, int limit) {
        var page = PageRequest.of(0, Math.clamp(limit, 1, 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (category == null) {
            return findByStatus(status, limit);
        }
        List<SupportTicketDocument> rows;
        if (category == SupportCategory.GENERAL) {
            rows = status == null ? tickets.findGeneral(page) : tickets.findGeneralByStatus(status.name(), page);
        } else {
            rows = status == null
                    ? tickets.findByCategory(category.name(), page)
                    : tickets.findByStatusAndCategory(status.name(), category.name(), page);
        }
        return rows.stream().map(MongoSupportTicketAdapter::toDomain).toList();
    }

    private static SupportTicketDocument toDocument(SupportTicket ticket) {
        return new SupportTicketDocument(
                ticket.roomId(),
                ticket.requesterId(),
                ticket.category().name(),
                ticket.status().name(),
                ticket.assigneeId(),
                ticket.createdAt(),
                ticket.updatedAt(),
                ticket.resolvedAt(),
                ticket.version(),
                ticket.notices().stream()
                        .map(notice -> new SupportTicketDocument.NoticeDocument(
                                notice.kind().name(), notice.messageId(), notice.senderId(), notice.sentAt()))
                        .toList());
    }

    private static SupportTicket toDomain(SupportTicketDocument document) {
        return new SupportTicket(
                document.getRoomId(),
                document.getRequesterId(),
                document.getCategory() == null
                        ? SupportCategory.GENERAL
                        : SupportCategory.valueOf(document.getCategory()),
                SupportStatus.valueOf(document.getStatus()),
                document.getAssigneeId(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getResolvedAt(),
                document.getVersion(),
                document.getNotices().stream()
                        .map(notice -> new SupportNotice(
                                SupportNoticeKind.valueOf(notice.kind()),
                                notice.messageId(),
                                notice.senderId(),
                                notice.sentAt()))
                        .toList());
    }
}
