package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.ConflictException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class MongoSupportTicketAdapter implements SupportTicketRepositoryPort {

    private final SupportTicketMongoRepository tickets;

    public MongoSupportTicketAdapter(SupportTicketMongoRepository tickets) {
        this.tickets = tickets;
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
                ticket.version());
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
                document.getVersion());
    }
}
