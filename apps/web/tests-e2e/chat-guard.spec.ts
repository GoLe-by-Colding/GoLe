import { test, expect, request } from "@playwright/test";

/**
 * 채팅 API 접근 가드.
 *
 * <p>1:1 대화는 "누가 볼 수 있는가"가 기능의 일부다. 이전에는 인증이 전혀 없어서 방 id만 알면
 * 누구나 남의 대화를 읽고 SSE로 실시간 구독할 수 있었고, 보낸 사람도 요청 본문의 senderId를
 * 그대로 믿어 사칭이 가능했다.
 *
 * <p>여기서는 토큰 없는 접근이 막히는지만 확인한다. 참여자 판정 자체는 서버 단위 테스트
 * (ChatRoomTest)가 담당한다.
 */

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const ROOM = "any-room-id";

test.describe("채팅 API 가드", () => {
  test.skip(
    () => !process.env.E2E_WITH_BACKEND,
    "백엔드 대상 테스트는 E2E_WITH_BACKEND=1 에서만 실행",
  );

  test("토큰이 없으면 목록·조회·전송이 모두 401", async () => {
    const ctx = await request.newContext({ baseURL: API_BASE });

    expect((await ctx.get("/api/v1/chat/rooms")).status()).toBe(401);
    expect((await ctx.get(`/api/v1/chat/rooms/${ROOM}/messages`)).status()).toBe(401);
    expect(
      (
        await ctx.post(`/api/v1/chat/rooms/${ROOM}/messages`, {
          data: { content: "안녕하세요" },
        })
      ).status(),
    ).toBe(401);

    await ctx.dispose();
  });

  test("잘못된 토큰이면 401", async () => {
    const ctx = await request.newContext({ baseURL: API_BASE });
    const headers = { Authorization: "Bearer not-a-real-token" };

    expect((await ctx.get("/api/v1/chat/rooms", { headers })).status()).toBe(401);
    expect((await ctx.get(`/api/v1/chat/rooms/${ROOM}/messages`, { headers })).status()).toBe(401);

    await ctx.dispose();
  });

  /** SSE는 EventSource가 헤더를 못 붙여 쿼리로 토큰을 받는다. 그래도 검증은 똑같이 해야 한다. */
  test("SSE 스트림도 토큰 없이는 열리지 않는다", async () => {
    const ctx = await request.newContext({ baseURL: API_BASE });

    // 토큰이 없는 것도 틀린 것도 "인증되지 않음"이다. 파라미터 누락을 400으로 흘리면
    // 클라이언트가 "요청 형식 문제"로 오해하고, 서버 오류(500)로 새면 운영 알림이 울린다.
    expect((await ctx.get(`/api/v1/chat/rooms/${ROOM}/stream`)).status()).toBe(401);
    expect((await ctx.get(`/api/v1/chat/rooms/${ROOM}/stream?token=bogus`)).status()).toBe(401);

    await ctx.dispose();
  });

  /** 남의 이름으로 보내는 경로가 아예 없어야 한다 — 본문의 senderId는 더 이상 쓰이지 않는다. */
  test("본문에 senderId를 넣어도 인증 없이는 통하지 않는다", async () => {
    const ctx = await request.newContext({ baseURL: API_BASE });

    const res = await ctx.post(`/api/v1/chat/rooms/${ROOM}/messages`, {
      data: { senderId: "victim-account", content: "사칭 시도" },
    });

    expect(res.status()).toBe(401);
    await ctx.dispose();
  });
});
