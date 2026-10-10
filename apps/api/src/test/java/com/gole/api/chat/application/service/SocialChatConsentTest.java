package com.gole.api.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gole.api.chat.application.port.out.ChatAccountPort;
import com.gole.api.chat.application.port.out.ChatBlockRepositoryPort;
import com.gole.api.chat.application.port.out.ChatConsentPort;
import com.gole.api.chat.application.port.out.ChatReadStatePort;
import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 새 대화 상대에게 내 정보를 내보이는 행동(1:1 시작·그룹 생성·초대)의 제3자 제공 동의 관문. */
class SocialChatConsentTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    private final ChatConsentPort consents = mock(ChatConsentPort.class);
    private final SocialChatService chats = spy(new SocialChatService(
            mock(SocialChatRoomRepositoryPort.class),
            mock(ChatBlockRepositoryPort.class),
            mock(SupportTicketRepositoryPort.class),
            mock(ChatAccountPort.class),
            consents,
            mock(ChatReadStatePort.class),
            Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    @DisplayName("본인 동의가 없으면 1:1 시작·그룹 생성·초대 모두 바꾸기 전에 거부한다")
    void startingOrJoiningRequiresActorsConsentBeforeMutation() {
        doReturn(Optional.empty()).when(chats).findExistingDirect("legacy-user", "peer");
        SocialChatRoom group =
                SocialChatRoom.group("room-1", "legacy-user", List.of("member-1", "member-2"), "group", NOW);
        doReturn(group).when(chats).requireReadable("room-1", "legacy-user");
        doThrow(new ForbiddenException("THIRD_PARTY_PROVISION_CONSENT_REQUIRED", "consent required"))
                .when(consents)
                .requireCurrent("legacy-user");

        assertThatThrownBy(() -> chats.startDirect("legacy-user", "peer")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> chats.startGroup("legacy-user", "group", List.of("a", "b")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> chats.inviteMember("room-1", "legacy-user", "peer"))
                .isInstanceOf(ForbiddenException.class);

        verify(chats, never()).createDirect("legacy-user", "peer");
        verify(chats, never()).createGroup("legacy-user", "group", List.of("a", "b"));
        verify(chats, never()).invite("room-1", "legacy-user", "peer");
    }

    @Test
    @DisplayName("이미 있는 1:1 방은 동의 철회 뒤에도 동의를 묻지 않고 다시 연다")
    void existingDirectRoomCanBeReenteredAfterWithdrawal() {
        SocialChatRoom room = SocialChatRoom.direct("direct-1", "legacy-user", "peer", NOW);
        doReturn(Optional.of(room)).when(chats).findExistingDirect("legacy-user", "peer");

        assertThat(chats.startDirect("legacy-user", "peer")).isSameAs(room);
        verifyNoInteractions(consents);
        verify(chats, never()).createDirect("legacy-user", "peer");
    }

    @Test
    @DisplayName("그룹 생성·초대는 새로 노출되는 모든 사람의 동의를 확인한다")
    void groupCreationAndInviteRequireEveryNewlyExposedSubjectsConsent() {
        doThrow(new ForbiddenException("THIRD_PARTY_PROVISION_SUBJECT_CONSENT_REQUIRED", "subject consent"))
                .when(consents)
                .requireCurrentSubject("member-2");

        assertThatThrownBy(() -> chats.startGroup("owner", "group", List.of("member-1", "member-2")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("THIRD_PARTY_PROVISION_SUBJECT_CONSENT_REQUIRED");
        verify(chats, never()).createGroup("owner", "group", List.of("member-1", "member-2"));

        SocialChatRoom existing =
                SocialChatRoom.group("room-1", "owner", List.of("member-1", "member-2"), "group", NOW);
        doReturn(existing).when(chats).requireReadable("room-1", "owner");

        assertThatThrownBy(() -> chats.inviteMember("room-1", "owner", "new-member"))
                .isInstanceOf(ForbiddenException.class);
        verify(chats, never()).invite("room-1", "owner", "new-member");
    }

    @Test
    @DisplayName("이미 멤버인 사람을 초대하면 동의를 묻지 않고 방을 그대로 돌려준다")
    void invitingExistingMemberIsNoOp() {
        SocialChatRoom existing =
                SocialChatRoom.group("room-1", "owner", List.of("member-1", "member-2"), "group", NOW);
        doReturn(existing).when(chats).requireReadable("room-1", "owner");

        assertThat(chats.inviteMember("room-1", "owner", "member-1")).isSameAs(existing);
        verifyNoInteractions(consents);
    }
}
