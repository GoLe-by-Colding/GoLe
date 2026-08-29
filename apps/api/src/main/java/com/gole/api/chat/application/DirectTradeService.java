package com.gole.api.chat.application;

import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.application.port.out.DirectTradeNotifierPort;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.TradeMode;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 채팅 참여자 양쪽이 확인한 직거래만 완료한다. 결제 주문·체결가 원장과는 연결하지 않는다. */
@Service
public class DirectTradeService {

    private static final Logger log = LoggerFactory.getLogger(DirectTradeService.class);
    private static final int MAX_TRANSIENT_RETRIES = 3;

    private final ChatRoomMongoRepository rooms;
    private final MongoTemplate mongoTemplate;
    private final MarkListingSoldUseCase markListingSold;
    private final GetLaunchConfigUseCase launchConfig;
    private final DirectTradeNotifierPort notifier;
    private final TransactionTemplate confirmTransaction;
    private final Clock clock;

    public DirectTradeService(
            ChatRoomMongoRepository rooms,
            MongoTemplate mongoTemplate,
            MarkListingSoldUseCase markListingSold,
            GetLaunchConfigUseCase launchConfig,
            DirectTradeNotifierPort notifier,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.rooms = rooms;
        this.mongoTemplate = mongoTemplate;
        this.markListingSold = markListingSold;
        this.launchConfig = launchConfig;
        this.notifier = notifier;
        this.confirmTransaction = new TransactionTemplate(transactionManager);
        this.confirmTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public ChatRoomDocument confirm(String roomId, String actorId) {
        return executeWithTransientRetry("confirm", roomId, () -> confirmOnce(roomId, actorId));
    }

    private ChatRoomDocument executeWithTransientRetry(
            String operation, String roomId, Supplier<ChatRoomDocument> work) {
        int retries = 0;
        while (true) {
            try {
                ChatRoomDocument result = confirmTransaction.execute(ignored -> work.get());
                if (result == null) {
                    throw new IllegalStateException("직거래 상태 변경 트랜잭션이 결과 없이 종료되었습니다");
                }
                return result;
            } catch (RuntimeException failure) {
                if (!isTransientTransactionFailure(failure) || retries >= MAX_TRANSIENT_RETRIES) {
                    throw failure;
                }
                retries++;
                log.warn(
                        "Retrying direct trade operation after transient MongoDB transaction failure: operation={}, roomId={}, retry={}/{}",
                        operation,
                        roomId,
                        retries,
                        MAX_TRANSIENT_RETRIES);
                pauseBeforeRetry(retries);
            }
        }
    }

    private ChatRoomDocument confirmOnce(String roomId, String actorId) {
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
        boolean newlyConfirmed = mongoTemplate
                        .updateFirst(
                                Query.query(Criteria.where("_id")
                                        .is(roomId)
                                        .and(field)
                                        .is(null)
                                        .and("directTradeCompletedAt")
                                        .is(null)),
                                Update.update(field, Instant.now(clock)),
                                ChatRoomDocument.class)
                        .getModifiedCount()
                == 1L;

        ChatRoomDocument confirmed = requireParticipant(roomId, actorId);
        if (confirmed.getBuyerConfirmedAt() == null || confirmed.getSellerConfirmedAt() == null) {
            if (newlyConfirmed) {
                notifier.confirmationRequested(counterpartyId(confirmed, actorId), roomId);
            }
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
            notifyFirstConfirmer(completed);
            return completed;
        }
        return requireParticipant(roomId, actorId);
    }

    private static boolean isTransientTransactionFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof MongoException mongoFailure
                    && (mongoFailure.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                            || mongoFailure.getCode() == 112)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void pauseBeforeRetry(int retry) {
        try {
            Thread.sleep(10L << (retry - 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("직거래 확인 재시도가 중단되었습니다", interrupted);
        }
    }

    public ChatRoomDocument cancelConfirmation(String roomId, String actorId) {
        return executeWithTransientRetry("cancel", roomId, () -> cancelConfirmationOnce(roomId, actorId));
    }

    private ChatRoomDocument cancelConfirmationOnce(String roomId, String actorId) {
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

    private static String counterpartyId(ChatRoomDocument room, String actorId) {
        return actorId.equals(room.getBuyerId()) ? room.getSellerId() : room.getBuyerId();
    }

    private void notifyFirstConfirmer(ChatRoomDocument room) {
        int confirmationOrder = room.getBuyerConfirmedAt().compareTo(room.getSellerConfirmedAt());
        if (confirmationOrder < 0) {
            notifier.tradeCompleted(room.getBuyerId(), room.getId());
        } else if (confirmationOrder > 0) {
            notifier.tradeCompleted(room.getSellerId(), room.getId());
        } else {
            // 동시 요청이 같은 시각 정밀도로 저장되면 먼저 확인한 쪽을 판별할 수 없다.
            // 완료 CAS 승자 한 요청만 양쪽에 알려, 어느 참여자도 완료 사실을 놓치지 않게 한다.
            notifier.tradeCompleted(room.getBuyerId(), room.getId());
            notifier.tradeCompleted(room.getSellerId(), room.getId());
        }
    }
}
