import { expect, test } from "@playwright/test";

const BUSINESS = {
  name: "콜딩(Colding)",
  representative: "김승찬",
  registrationNumber: "457-49-00942",
  address: "경기도 김포시 김포한강9로75번길 66, 5층 (구래동, 국제프라자)",
  phone: "010-6545-6502",
  developerEmail: "developerkscold@gmail.com",
  contactEmail: "coldingcontact@gmail.com",
  hostingProvider: "Google Cloud Platform",
  copyright: "© 2026 Colding. All rights reserved.",
} as const;

test("푸터에 사업자 정보와 실제 연락 링크를 모두 노출한다", async ({ page }) => {
  await page.goto("/terms");

  const footer = page.locator("footer");
  await expect(footer).toContainText(`상호 ${BUSINESS.name}`);
  await expect(footer).toContainText(`대표 ${BUSINESS.representative}`);
  await expect(footer).toContainText(`사업자등록번호 ${BUSINESS.registrationNumber}`);
  await expect(footer).toContainText(`주소 ${BUSINESS.address}`);
  await expect(footer).toContainText(BUSINESS.copyright);
  await expect(footer.getByRole("link", { name: BUSINESS.phone })).toHaveAttribute(
    "href",
    `tel:${BUSINESS.phone}`,
  );
  await expect(footer.getByRole("link", { name: BUSINESS.developerEmail })).toHaveAttribute(
    "href",
    `mailto:${BUSINESS.developerEmail}`,
  );
  await expect(footer.getByRole("link", { name: BUSINESS.contactEmail })).toHaveAttribute(
    "href",
    `mailto:${BUSINESS.contactEmail}`,
  );
  await expect(footer.getByRole("link", { name: "개인정보처리방침" })).toHaveAttribute(
    "href",
    "/privacy",
  );
  await expect(footer.getByRole("link", { name: "후기 운영정책" })).toHaveAttribute(
    "href",
    "/review-policy",
  );
  await expect(footer).toContainText(`호스팅서비스 제공자 ${BUSINESS.hostingProvider}`);
  await expect(footer.getByRole("link", { name: "공정거래위원회에서 확인" })).toHaveAttribute(
    "href",
    "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=4574900942",
  );
});

test("약관과 개인정보처리방침이 동일한 운영 주체를 안내한다", async ({ page }) => {
  await page.goto("/terms");
  const terms = page.locator("article");
  await expect(terms).toContainText(`상호: ${BUSINESS.name}`);
  await expect(terms).toContainText(`대표: ${BUSINESS.representative}`);
  await expect(terms).toContainText(`사업자등록번호: ${BUSINESS.registrationNumber}`);

  await page.goto("/privacy");
  const privacy = page.locator("article");
  await expect(privacy).toContainText(BUSINESS.name);
  await expect(privacy).toContainText(`개인정보 보호책임자: ${BUSINESS.representative}`);
  const contactLinks = privacy.getByRole("link", { name: BUSINESS.contactEmail });
  await expect(contactLinks).toHaveCount(2);
  for (const link of await contactLinks.all()) {
    await expect(link).toHaveAttribute("href", `mailto:${BUSINESS.contactEmail}`);
  }
});

test("공개 약관에서 불만·분쟁 기준과 법정 안내 기한을 사전에 확인할 수 있다", async ({ page }) => {
  await page.goto("/terms#complaint-resolution-policy");
  const policy = page.locator("#complaint-resolution-policy");

  await expect(policy.getByRole("heading", { name: "불만·분쟁 처리기준" })).toBeVisible();
  await expect(policy).toContainText("3영업일 이내에 진행 경과");
  await expect(policy).toContainText("10영업일 이내에 조사 결과 또는 처리방안");
  await expect(policy).toContainText("분쟁 자체의 종결을 보장하는 기간이 아니며");
  await expect(policy).toContainText("운영 문의로 재검토를 요청");
});

test("개인정보처리방침이 실제 처리 항목·위탁·국외이전·파기 절차를 안내한다", async ({ page }) => {
  await page.goto("/privacy");
  const privacy = page.locator("article");

  await expect(privacy).toContainText("닉네임");
  await expect(privacy).toContainText("관심 태그");
  await expect(privacy).toContainText("판매자 CS 연락처");
  await expect(privacy).toContainText("원래 거래 당사자 계정 식별자");
  await expect(privacy).toContainText("구분된 주문·배송·정산 컬렉션");
  await expect(privacy).toContainText("인증번호 원문은 메일 발송에만 사용");
  await expect(privacy).toContainText("가입 인증용 단방향 해시");
  await expect(privacy).toContainText("비공개 저장소(MinIO)");
  await expect(privacy).toContainText("정책 확인이 적용되는 공개 조회 경로");
  await expect(privacy).toContainText("채팅·운영 문의·신고·후기");
  await expect(privacy).toContainText("정책 확인 이력");
  await expect(privacy).toContainText("Google LLC(Gmail)");
  await expect(privacy).toContainText("Discord Inc.");
  await expect(privacy).toContainText("SOLAPI/CoolSMS");
  await expect(privacy).toContainText("거래 상대방(구매자 또는 판매자)");
  await expect(privacy).toContainText("정보주체의 전체 전화번호를 제공합니다");
  await expect(privacy).toContainText(
    /주소나 카드번호·유효기간·\s*CVC는 상대방에게 제공하지 않으며/,
  );
  await expect(privacy).toContainText("이전 항목");
  await expect(privacy).toContainText("이전 국가");
  await expect(privacy).toContainText("시기·방법");
  await expect(privacy).toContainText("수령자·연락처");
  await expect(privacy).toContainText("거부 방법·효과");
  await expect(
    privacy.getByRole("link", { name: "Google 개인정보처리방침 및 문의" }),
  ).toHaveAttribute("href", "https://policies.google.com/privacy");
  await expect(privacy.getByRole("link", { name: "privacy@discord.com" })).toHaveAttribute(
    "href",
    "mailto:privacy@discord.com",
  );
  await expect(
    privacy.getByRole("heading", { name: "8. 개인정보 파기 절차 및 방법" }),
  ).toBeVisible();
  await expect(privacy).toContainText("별도 순환 정책이 만료되면 파기");
  await expect(privacy).toContainText("완료된 삭제 기록을 다시 적용");
  await expect(privacy).toContainText("rules-v1");
  await expect(privacy).toContainText("입력이나 생성 결과를 모델 학습·개선에 사용하지 않으며");
  await expect(privacy).toContainText("신고·이의 제기·처리정지를 요청");

  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", /\/privacy$/);
});

test("공개 법적 문서가 자기 URL을 canonical로 사용한다", async ({ page }) => {
  await page.goto("/terms");
  await expect(page).toHaveTitle("이용약관 · GoLe");
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", /\/terms$/);
  await expect(page.locator('meta[property="og:url"]')).toHaveAttribute("content", /\/terms$/);

  await page.goto("/privacy");
  await expect(page).toHaveTitle("개인정보처리방침 · GoLe");
  await expect(page.locator('meta[property="og:url"]')).toHaveAttribute("content", /\/privacy$/);

  await page.goto("/review-policy");
  await expect(page).toHaveTitle("후기 운영정책 · GoLe");
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", /\/review-policy$/);
  await expect(page.locator('meta[property="og:url"]')).toHaveAttribute(
    "content",
    /\/review-policy$/,
  );
});

test("후기 운영정책이 작성·게시·평점·삭제·이의제기 기준을 공개한다", async ({ page }) => {
  await page.goto("/review-policy");
  const policy = page.locator("article");

  await expect(policy.getByRole("heading", { name: "1. 작성 권한과 수집 방법" })).toBeVisible();
  await expect(policy).toContainText("완료 상태가 된 주문의 구매자만");
  await expect(policy).toContainText("한 주문에는 후기를 한 번만");
  await expect(policy.getByRole("heading", { name: "2. 게시기간" })).toBeVisible();
  await expect(policy).toContainText("최신 후기 최대 100건을 최신순으로 게시");
  await expect(policy.getByRole("heading", { name: "3. 평점 기준과 표시 효과" })).toBeVisible();
  await expect(policy).toContainText("최신 후기 최대 100건");
  await expect(policy).toContainText("검색 노출·계정 제한·판매자 혜택을 자동으로 바꾸지 않습니다");
  await expect(policy.getByRole("heading", { name: "4. 공개 중단·삭제 기준" })).toBeVisible();
  await expect(policy).toContainText("삭제하는 조치를 공개 중단(숨김)으로 처리");
  await expect(policy).toContainText("낮은 평점");
  await expect(
    policy.getByRole("heading", { name: "5. 공개 중단·삭제 조치 이의제기" }),
  ).toBeVisible();
  await expect(policy.getByRole("link", { name: "운영 문의" })).toHaveAttribute(
    "href",
    "/chat?compose=support&category=GENERAL",
  );
});

test("주요 공개 목록 화면의 OG URL이 홈이 아니라 자기 canonical을 가리킨다", async ({ page }) => {
  for (const path of ["/search", "/prices", "/community"] as const) {
    await page.goto(path);
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute(
      "href",
      new RegExp(path + "$"),
    );
    await expect(page.locator('meta[property="og:url"]')).toHaveAttribute(
      "content",
      new RegExp(path + "$"),
    );
  }
});

test("홈만 루트 canonical을 쓰고 작성·주문·결제 결과 화면은 색인을 막는다", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute(
    "href",
    new URL(page.url()).origin,
  );

  for (const path of [
    "/community/new",
    "/orders/nonexistent",
    "/payments/portone/return",
  ] as const) {
    await page.goto(path);
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
    await expect(page.locator('link[rel="canonical"]')).toHaveCount(0);
  }
});
