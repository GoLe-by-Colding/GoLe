package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.DirectTradeUseCase;
import com.gole.api.chat.application.port.out.ChatSellerVerificationPort;
import com.gole.api.chat.application.port.out.DirectTradeGatePort;
import com.gole.api.chat.application.port.out.DirectTradeListingPort;
import com.gole.api.chat.application.port.out.DirectTradeNotifierPort;
import com.gole.api.chat.application.port.out.ListingChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.RetryingTransactionPort;
import com.gole.api.chat.domain.model.ChatRoom;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 채팅 참여자 양쪽이 확인한 직거래만 완료한다. 결제 주문·체결가 원장과는 연결하지 않는다.
 *
 * <p>확인·취소는 각각 독립 트랜잭션에서 돌고, 일시적 트랜잭션 충돌이면 {@link RetryingTransactionPort} 가 다시 시도한다. 확인·완료
 * 시각은 저장소가 조건부로만 기록하므로 동시 요청이 겹쳐도 알림과 판매 완료 전환은 한 번이다.
 */
@Service
public class DirectTradeService implements DirectTradeUseCase {

    private final ListingChatRoomRepositoryPort rooms;
    private final RetryingTransactionPort transactions;
    private final DirectTradeListingPort listings;
    private final DirectTradeGatePort gate;
    private final DirectTradeNotifierPort notifier;
    private final SocialChatService socialChats;
    private final ChatSellerVerificationPort sellerVerification;
    private final Clock clock;

    public DirectTradeService(
            ListingChatRoomRepositoryPort rooms,
            RetryingTransactionPort transactions,
            DirectTradeListingPort listings,
            DirectTradeGatePort gate,
            DirectTradeNotifierPort notifier,
            SocialChatService socialChats,
            ChatSellerVerificationPort sellerVerification,
            Clock clock) {
        this.rooms = rooms;
        this.transactions = transactions;
        this.listings = listings;
        this.gate = gate;
        this.notifier = notifier;
        this.socialChats = socialChats;
        this.sellerVerification = sellerVerification;
        this.clock = clock;
    }

    @Override
    public ChatRoom confirm(String roomId, String actorId) {
        socialChats.requireReadable(roomId, actorId).requireDirectTradeAllowed();
        ChatRoom room = rooms.findById(roomId)
                .orElseThrow(() -> new NotFoundException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다"));
        // 직거래 완료는 매물을 판매 완료로 바꾸는 새 판매 행동이라 주문·제안과 같은 판매자 신원확인을 건다.
        sellerVerification.requireVerifiedSeller(room.sellerId());
        return transactions.inNewTransaction("direct-trade-confirm", roomId, () -> confirmOnce(roomId, actorId));
    }

    @Override
    public ChatRoom cancelConfirmation(String roomId, String actorId) {
        socialChats.requireReadable(roomId, actorId).requireDirectTradeAllowed();
        return transactions.inNewTransaction(
                "direct-trade-cancel", roomId, () -> cancelConfirmationOnce(roomId, actorId));
    }

    private ChatRoom confirmOnce(String roomId, String actorId) {
        if (!gate.directTradeOpen()) {
            throw new ConflictException("DIRECT_TRADE_MODE_CLOSED", "현재는 플랫폼 결제 거래 단계라 직거래 완료를 새로 확인할 수 없습니다");
        }
        ChatRoom room = requireParticipant(roomId, actorId);
        if (!room.hasListing()) {
            throw new ConflictException("DIRECT_TRADE_LISTING_ROOM_REQUIRED", "매물에 연결된 채팅방에서만 거래를 완료할 수 있습니다");
        }
        if (room.directTradeCompleted()) {
            return room;
        }

        boolean newlyConfirmed = rooms.recordConfirmation(roomId, room.partyOf(actorId), Instant.now(clock));
        ChatRoom confirmed = requireParticipant(roomId, actorId);
        if (!confirmed.bothConfirmed()) {
            if (newlyConfirmed) {
                notifier.confirmationRequested(confirmed.counterpartyOf(actorId), roomId);
            }
            return confirmed;
        }

        Optional<ChatRoom> completed = rooms.completeIfBothConfirmed(roomId, Instant.now(clock));
        if (completed.isPresent()) {
            ChatRoom done = completed.get();
            if (!listings.markSoldIfActive(done.listingId())) {
                throw new ConflictException("DIRECT_TRADE_LISTING_UNAVAILABLE", "이미 주문되었거나 판매 완료된 매물입니다");
            }
            notifyFirstConfirmer(done);
            return done;
        }
        return requireParticipant(roomId, actorId);
    }

    private ChatRoom cancelConfirmationOnce(String roomId, String actorId) {
        ChatRoom room = requireParticipant(roomId, actorId);
        if (room.directTradeCompleted()) {
            throw new ConflictException("DIRECT_TRADE_ALREADY_COMPLETED", "양쪽이 확인한 거래는 되돌릴 수 없습니다");
        }
        rooms.clearConfirmation(roomId, room.partyOf(actorId));
        return requireParticipant(roomId, actorId);
    }

    private ChatRoom requireParticipant(String roomId, String actorId) {
        ChatRoom room = rooms.findById(roomId)
                .orElseThrow(() -> new NotFoundException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다"));
        if (!room.isParticipant(actorId)) {
            throw new ForbiddenException("CHAT_ROOM_ACCESS_DENIED", "참여 중인 채팅방만 볼 수 있습니다");
        }
        return room;
    }

    private void notifyFirstConfirmer(ChatRoom room) {
        int confirmationOrder = room.buyerConfirmedAt().compareTo(room.sellerConfirmedAt());
        if (confirmationOrder < 0) {
            notifier.tradeCompleted(room.buyerId(), room.id());
        } else if (confirmationOrder > 0) {
            notifier.tradeCompleted(room.sellerId(), room.id());
        } else {
            // 동시 요청이 같은 시각 정밀도로 저장되면 먼저 확인한 쪽을 판별할 수 없다.
            // 완료 CAS 승자 한 요청만 양쪽에 알려, 어느 참여자도 완료 사실을 놓치지 않게 한다.
            notifier.tradeCompleted(room.buyerId(), room.id());
            notifier.tradeCompleted(room.sellerId(), room.id());
        }
    }
}
