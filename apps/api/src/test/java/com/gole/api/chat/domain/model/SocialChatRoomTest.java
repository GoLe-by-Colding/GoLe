package com.gole.api.chat.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SocialChatRoomTest {

    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @Nested
    @DisplayName("DIRECT")
    class Direct {

        @Test
        @DisplayName("자기 자신과는 만들 수 없다")
        void selfDmIsRejected() {
            assertThatThrownBy(() -> SocialChatRoom.direct("r1", "user-1", "user-1", NOW))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("자기 자신");
        }

        @Test
        @DisplayName("멱등 키는 참여자 순서와 무관하게 같다")
        void dedupeKeyIsOrderIndependent() {
            assertThat(SocialChatRoom.directDedupeKey("b", "a"))
                    .isEqualTo(SocialChatRoom.directDedupeKey("a", "b"))
                    .isEqualTo("DIRECT:a:b");
        }

        @ParameterizedTest(name = "빈 참여자 id [{0}] 는 거부한다")
        @ValueSource(strings = {"", "   "})
        @DisplayName("빈 참여자 id 로는 키를 만들지 않는다")
        void blankParticipantIsRejectedForKey(String blank) {
            assertThatThrownBy(() -> SocialChatRoom.directDedupeKey("user-1", blank))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> SocialChatRoom.directDedupeKey(blank, "user-1"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("null 참여자 id 는 거부한다")
        void nullParticipantIsRejected() {
            assertThatThrownBy(() -> SocialChatRoom.directDedupeKey("user-1", null))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> SocialChatRoom.direct("r1", null, "user-2", NOW))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("나갈 수 없다 — 1:1 대화는 한쪽만 남을 수 없다")
        void directCannotBeLeft() {
            SocialChatRoom room = SocialChatRoom.direct("r1", "user-1", "user-2", NOW);

            assertThatThrownBy(() -> room.leave("user-1", NOW)).isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("GROUP")
    class Group {

        @Test
        @DisplayName("2명짜리 그룹은 거부한다 — 그건 1:1 대화다")
        void twoMemberGroupIsRejected() {
            assertThatThrownBy(() -> SocialChatRoom.group("r1", "owner", List.of("user-2"), "둘만", NOW))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("3명 이상");
        }

        @Test
        @DisplayName("방장 포함 3명이면 만들어진다")
        void threeMembersAreEnough() {
            SocialChatRoom room = SocialChatRoom.group("r1", "owner", List.of("user-2", "user-3"), "모임", NOW);

            assertThat(room.memberIds()).containsExactly("owner", "user-2", "user-3");
            assertThat(room.ownerId()).isEqualTo("owner");
            assertThat(room.type()).isEqualTo(ChatRoomType.GROUP);
        }

        @Test
        @DisplayName("중복 초대자는 한 명으로 접힌다")
        void duplicateInviteesCollapse() {
            assertThatThrownBy(() -> SocialChatRoom.group("r1", "owner", List.of("user-2", "user-2"), "모임", NOW))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("3명 이상");
        }

        @Test
        @DisplayName("제목이 없으면 거부한다")
        void titleIsRequired() {
            assertThatThrownBy(() -> SocialChatRoom.group("r1", "owner", List.of("u2", "u3"), "  ", NOW))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("제목");
        }

        @Test
        @DisplayName("초대는 멱등하다")
        void inviteIsIdempotent() {
            SocialChatRoom room = SocialChatRoom.group("r1", "owner", List.of("u2", "u3"), "모임", NOW);

            SocialChatRoom once = room.invite("owner", "u4", NOW);
            SocialChatRoom twice = once.invite("owner", "u4", NOW);

            assertThat(twice.memberIds()).containsExactly("owner", "u2", "u3", "u4");
        }

        @Test
        @DisplayName("비멤버는 초대할 수 없다")
        void nonMemberCannotInvite() {
            SocialChatRoom room = SocialChatRoom.group("r1", "owner", List.of("u2", "u3"), "모임", NOW);

            assertThatThrownBy(() -> room.invite("outsider", "u4", NOW)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("그룹 생성과 추가 초대는 50명 정원을 넘길 수 없다")
        void groupCapacityIsEnforcedOnCreationAndInvite() {
            List<String> fortyNineInvitees = java.util.stream.IntStream.rangeClosed(1, 49)
                    .mapToObj(index -> "user-" + index)
                    .toList();
            SocialChatRoom full = SocialChatRoom.group("r1", "owner", fortyNineInvitees, "모임", NOW);

            assertThat(full.memberIds()).hasSize(ChatRoomType.GROUP.maxMembers());
            assertThatThrownBy(() -> full.invite("owner", "overflow", NOW))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("정원");
            assertThatThrownBy(() -> SocialChatRoom.group(
                            "r2",
                            "owner",
                            java.util.stream.IntStream.rangeClosed(1, 50)
                                    .mapToObj(index -> "user-" + index)
                                    .toList(),
                            "초과",
                            NOW))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("최대");
        }

        @Test
        @DisplayName("정원 미달이 되어도 나갈 수 있다 — 최소 인원은 생성 시점 규칙이다")
        void leavingBelowMinimumIsAllowed() {
            SocialChatRoom room = SocialChatRoom.group("r1", "owner", List.of("u2", "u3"), "모임", NOW);

            SocialChatRoom afterLeave = room.leave("u3", NOW);

            assertThat(afterLeave.memberIds()).containsExactly("owner", "u2");
            assertThat(afterLeave.isClosed()).isFalse();
        }

        @Test
        @DisplayName("방장이 나가면 가장 오래된 잔여 멤버가 승계한다")
        void ownerLeavingTransfersOwnership() {
            SocialChatRoom room = SocialChatRoom.group("r1", "owner", List.of("u2", "u3"), "모임", NOW);

            SocialChatRoom afterLeave = room.leave("owner", NOW);

            assertThat(afterLeave.ownerId()).isEqualTo("u2");
            assertThat(afterLeave.memberIds()).containsExactly("u2", "u3");
        }

        @Test
        @DisplayName("마지막 멤버가 나가면 방이 닫히고 멤버가 비워진다")
        void lastMemberLeavingClosesTheRoom() {
            SocialChatRoom room = SocialChatRoom.group("r1", "owner", List.of("u2", "u3"), "모임", NOW)
                    .leave("u3", NOW)
                    .leave("u2", NOW)
                    .leave("owner", NOW);

            assertThat(room.isClosed()).isTrue();
            assertThat(room.memberIds()).isEmpty();
            assertThat(room.closedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("닫힌 방에는 아무도 보낼 수 없다")
        void closedRoomRejectsSending() {
            SocialChatRoom closed =
                    new SocialChatRoom("r1", ChatRoomType.GROUP, List.of("owner"), "owner", "모임", null, NOW, NOW);

            assertThatThrownBy(() -> closed.requireCanSend("owner"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("종료된");
        }

        @Test
        @DisplayName("닫힌 방도 멤버였던 사람은 계속 읽을 수 있다")
        void closedRoomStaysReadableForRemainingMembers() {
            SocialChatRoom closed =
                    new SocialChatRoom("r1", ChatRoomType.GROUP, List.of("owner"), "owner", "모임", null, NOW, NOW);

            closed.requireMember("owner"); // 예외 없음
            assertThatThrownBy(() -> closed.requireMember("outsider")).isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("SUPPORT")
    class Support {

        @Test
        @DisplayName("담당자 배정은 관리자를 멤버로 넣는다")
        void assigningAddsAgentAsMember() {
            SocialChatRoom room = SocialChatRoom.support("r1", "user-1", "문의", NOW);

            SocialChatRoom assigned = room.withSupportAgent(null, "admin-1");

            assertThat(assigned.memberIds()).containsExactly("user-1", "admin-1");
        }

        @Test
        @DisplayName("이관하면 이전 담당자는 빠지고 문의자는 남는다")
        void transferDropsPreviousAgentButKeepsRequester() {
            SocialChatRoom assigned =
                    SocialChatRoom.support("r1", "user-1", "문의", NOW).withSupportAgent(null, "admin-1");

            SocialChatRoom transferred = assigned.withSupportAgent("admin-1", "admin-2");

            assertThat(transferred.memberIds()).containsExactly("user-1", "admin-2");
            assertThat(transferred.memberIds()).doesNotContain("admin-1");
        }

        @Test
        @DisplayName("이전 담당자 자리에 문의자가 잘못 들어와도 문의자는 빠지지 않는다")
        void requesterIsNeverRemovedByTransfer() {
            SocialChatRoom room = SocialChatRoom.support("r1", "user-1", "문의", NOW);

            SocialChatRoom transferred = room.withSupportAgent("user-1", "admin-1");

            assertThat(transferred.memberIds()).contains("user-1", "admin-1");
        }

        @Test
        @DisplayName("SUPPORT 가 아닌 방에는 담당자를 붙일 수 없다")
        void onlySupportRoomsTakeAgents() {
            SocialChatRoom direct = SocialChatRoom.direct("r1", "u1", "u2", NOW);

            assertThatThrownBy(() -> direct.withSupportAgent(null, "admin-1")).isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("권한 경계")
    class Access {

        @Test
        @DisplayName("비멤버는 관리자여도 읽을 수 없다 — 도메인은 역할을 모른다")
        void nonMemberIsRejectedRegardlessOfRole() {
            SocialChatRoom room = SocialChatRoom.direct("r1", "u1", "u2", NOW);

            assertThatThrownBy(() -> room.requireMember("admin-1"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("접근할 수 없습니다");
        }

        @Test
        @DisplayName("직거래 완료 확인은 매물 방에서만 허용한다")
        void directTradeIsListingOnly() {
            SocialChatRoom listing = SocialChatRoom.listing("r1", "listing-1", "buyer", "seller", NOW);
            listing.requireDirectTradeAllowed(); // 예외 없음

            for (SocialChatRoom other : List.of(
                    SocialChatRoom.direct("r2", "u1", "u2", NOW),
                    SocialChatRoom.group("r3", "owner", List.of("u2", "u3"), "모임", NOW),
                    SocialChatRoom.support("r4", "u1", "문의", NOW))) {
                assertThatThrownBy(other::requireDirectTradeAllowed)
                        .isInstanceOf(BadRequestException.class)
                        .hasMessageContaining("매물 대화방에서만");
            }
        }

        @Test
        @DisplayName("관리자 참여가 허용되는 유형은 SUPPORT 뿐이다")
        void onlySupportAllowsAdminParticipation() {
            assertThat(ChatRoomType.SUPPORT.allowsAdminParticipation()).isTrue();
            assertThat(ChatRoomType.DIRECT.allowsAdminParticipation()).isFalse();
            assertThat(ChatRoomType.GROUP.allowsAdminParticipation()).isFalse();
            assertThat(ChatRoomType.LISTING.allowsAdminParticipation()).isFalse();
        }
    }

    @Nested
    @DisplayName("레거시 호환")
    class Legacy {

        @Test
        @DisplayName("type 이 없는 문서는 매물 방으로 읽는다")
        void missingTypeReadsAsListing() {
            assertThat(ChatRoomType.ofNullable(null)).isEqualTo(ChatRoomType.LISTING);
            assertThat(ChatRoomType.ofNullable("")).isEqualTo(ChatRoomType.LISTING);
            assertThat(ChatRoomType.ofNullable("GROUP")).isEqualTo(ChatRoomType.GROUP);
        }

        @Test
        @DisplayName("레거시 매물 방의 멤버는 buyer 와 seller 다")
        void listingMembersAreBuyerAndSeller() {
            SocialChatRoom room = SocialChatRoom.listing("r1", "listing-1", "buyer", "seller", NOW);

            assertThat(room.memberIds()).containsExactly("buyer", "seller");
            assertThat(room.isMember("buyer")).isTrue();
            assertThat(room.isMember("someone")).isFalse();
            assertThat(room.dedupeKey()).isNull();
        }

        @Test
        @DisplayName("매물 방에는 listingId 가 반드시 있다")
        void listingRoomRequiresListingId() {
            assertThatThrownBy(() -> new SocialChatRoom(
                            "r1", ChatRoomType.LISTING, List.of("b", "s"), null, null, null, NOW, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
