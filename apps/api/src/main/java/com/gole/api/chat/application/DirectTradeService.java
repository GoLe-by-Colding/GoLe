package com.gole.api.chat.application;

import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.TradeMode;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 채팅 참여자 양쪽이 확인한 직거래만 완료한다. 결제 주문·체결가 원장과는 연결하지 않는다. */
@Service
public class DirectTradeService {

    private final ChatRoomMongoRepository rooms;
    private final MongoTemplate mongoTemplate;
    private final MarkListingSoldUseCase markListingSold;
    private final GetLaunchConfigUseCase launchConfig;
    private final Clock clock;

    public DirectTradeService(
            ChatRoomMongoRepository rooms,
            MongoTemplate mongoTemplate,
            MarkListingSoldUseCase markListingSold,
            GetLaunchConfigUseCase launchConfig,
            Clock clock) {
        this.rooms = rooms;
        this.mongoTemplate = mongoTemplate;
        this.markListingSold = markListingSold;
        this.launchConfig = launchConfig;
        this.clock = clock;
    }

    @Transactional
    public ChatRoomDocument confirm(String roomId, String actorId) {
        if (launchConfig.current().tradeMode() != TradeMode.DIRECT_CHAT) {
            throw new ConflictException("DIRECT_TRADE_MODE_CLOSED", "현재는 플랫폼 결제 거래 단계라 직거래 완료를 새로 확인할 수 없습니다");
        }
        ChatRoomDocument room = requireParticipant(roomId, actorId);
        if (room.getListingId() == null || room.getListingId().isBlank()) {
            throw new ConflictException("DIRECT_TRADE_LISTING_ROOM_REQUIRED", "매물에 연결된 채팅방에서만 거래를 완료할 수 있습니다");
        }
        if (room.getDirectTradeCompletedAt() != null) {
            return room;
        }

        String field = actorId.equals(room.getBuyerId()) ? "buyerConfirmedAt" : "sellerConfirmedAt";
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id")
                        .is(roomId)
                        .and(field)
                        .is(null)
                        .and("directTradeCompletedAt")
                        .is(null)),
                Update.update(field, Instant.now(clock)),
                ChatRoomDocument.class);

        ChatRoomDocument confirmed = requireParticipant(roomId, actorId);
        if (confirmed.getBuyerConfirmedAt() == null || confirmed.getSellerConfirmedAt() == null) {
            return confirmed;
        }

        ChatRoomDocument completed = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id")
                        .is(roomId)
                        .and("buyerConfirmedAt")
                        .ne(null)
                        .and("sellerConfirmedAt")
                        .ne(null)
                        .and("directTradeCompletedAt")
                        .is(null)),
                Update.update("directTradeCompletedAt", Instant.now(clock)),
                FindAndModifyOptions.options().returnNew(true),
                ChatRoomDocument.class);
        if (completed != null) {
            if (!markListingSold.markDirectTradeSoldIfActive(completed.getListingId())) {
                throw new ConflictException("DIRECT_TRADE_LISTING_UNAVAILABLE", "이미 주문되었거나 판매 완료된 매물입니다");
            }
            return completed;
        }
        return requireParticipant(roomId, actorId);
    }

    @Transactional
    public ChatRoomDocument cancelConfirmation(String roomId, String actorId) {
        ChatRoomDocument room = requireParticipant(roomId, actorId);
        if (room.getDirectTradeCompletedAt() != null) {
            throw new ConflictException("DIRECT_TRADE_ALREADY_COMPLETED", "양쪽이 확인한 거래는 되돌릴 수 없습니다");
        }
        String field = actorId.equals(room.getBuyerId()) ? "buyerConfirmedAt" : "sellerConfirmedAt";
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id")
                        .is(roomId)
                        .and("directTradeCompletedAt")
                        .is(null)),
                new Update().unset(field),
                ChatRoomDocument.class);
        return requireParticipant(roomId, actorId);
    }

    private ChatRoomDocument requireParticipant(String roomId, String actorId) {
        ChatRoomDocument room = rooms.findById(roomId)
                .orElseThrow(() -> new NotFoundException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다"));
        if (!actorId.equals(room.getBuyerId()) && !actorId.equals(room.getSellerId())) {
            throw new ForbiddenException("CHAT_ROOM_ACCESS_DENIED", "참여 중인 채팅방만 볼 수 있습니다");
        }
        return room;
    }
}
