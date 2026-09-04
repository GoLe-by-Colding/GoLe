/**
 * 운영에 남은 레거시 데모 매물·게시글을 소프트 비활성화한다.
 *
 * 데이터는 지우지 않는다. 기존 공개 조회 규칙이 이미 제외하는 `DELETED` 상태로만
 * 바꾸고, 어떤 마이그레이션이 내렸는지 추적할 수 있는 비식별 메타데이터를 남긴다.
 *
 * 안전장치:
 * - 기본값은 DRY_RUN=true다. `DRY_RUN=false`를 명시해야 변경한다.
 * - 저장소에서 확인한 데모 actor와 개수 분포만 허용한다.
 * - 주문 또는 구·신 채팅방이 하나라도 매물을 참조하면 전체 변경을 중단한다.
 * - 이미 DELETED인 문서는 대상에서 빠지므로 재실행해도 안전하다.
 * - 제목, 본문, 이메일 등 평문 개인정보·사용자 콘텐츠는 출력하지 않는다.
 *
 * 사용:
 *   mongosh "mongodb://localhost:27017/gole?replicaSet=rs0" \
 *     scripts/migrate-production-demo-content.js
 *   DRY_RUN=false mongosh "mongodb://localhost:27017/gole?replicaSet=rs0" \
 *     scripts/migrate-production-demo-content.js
 */

const MIGRATION_ID = "2026-09-04-disable-legacy-demo-content";
const DRY_RUN = process.env.DRY_RUN !== "false";

const SEED_SELLER_IDS = Object.freeze([
  "seller-aurora",
  "seller-brickbank",
  "seller-minifig",
]);
const SEED_AUTHOR_IDS = Object.freeze([
  "user-builder",
  "user-collector",
  "user-moc",
  "user-newbie",
]);

// 2026-09-04 운영 드라이런에서 확인한 빈 고아 채팅 참조다. 이 매물의 레거시 방은
// 메시지·거래확정·구매자 계정이 모두 없고 주문/신형 채팅 참조도 없다. 방 자체는 증빙 보존을
// 위해 건드리지 않되, 이 정확한 매물만 공개 비활성화를 허용한다.
const KNOWN_EMPTY_ORPHAN_CHAT_LISTING_IDS = new Set([
  "71f0dc28-9683-4855-9982-56ec15feda77",
]);

// 현재 소스(11건)와 운영에 한 번 적재됐던 이전 소스(13건)만 허용한다.
// 문서를 삭제하지 않으므로 적용 후에도 같은 분포이며, 재실행 검증도 그대로 통과한다.
const EXPECTED_LISTING_PROFILES = Object.freeze([
  Object.freeze({
    "seller-aurora": 4,
    "seller-brickbank": 3,
    "seller-minifig": 4,
  }),
  Object.freeze({
    "seller-aurora": 5,
    "seller-brickbank": 4,
    "seller-minifig": 4,
  }),
]);
const EXPECTED_POST_PROFILES = Object.freeze([
  Object.freeze({
    "user-builder": 2,
    "user-collector": 3,
    "user-moc": 2,
    "user-newbie": 1,
  }),
]);

function countByActor(documents, actorField, allowlist) {
  const counts = Object.fromEntries(allowlist.map((actorId) => [actorId, 0]));
  for (const document of documents) {
    const actorId = document[actorField];
    if (!Object.hasOwn(counts, actorId)) {
      throw new Error(`허용되지 않은 데모 actor가 조회됨: ${String(actorId)}`);
    }
    counts[actorId] += 1;
  }
  return counts;
}

function sameProfile(actual, expected, allowlist) {
  return allowlist.every(
    (actorId) => actual[actorId] === (expected[actorId] ?? 0),
  );
}

function profileText(counts, allowlist) {
  return allowlist.map((actorId) => `${actorId}=${counts[actorId]}`).join(", ");
}

function assertExpectedProfile(
  label,
  counts,
  allowlist,
  expectedProfiles,
  total,
) {
  if (total === 0) {
    return;
  }
  if (
    !expectedProfiles.some((expected) =>
      sameProfile(counts, expected, allowlist),
    )
  ) {
    throw new Error(
      `${label} 데모 개수 분포가 예상과 다름: ${profileText(counts, allowlist)}`,
    );
  }
}

function assertKnownStatuses(label, documents, allowedStatuses) {
  const unexpected = documents.filter(
    (document) => !allowedStatuses.includes(document.status),
  );
  if (unexpected.length > 0) {
    throw new Error(
      `${label}에 예상하지 못한 상태 ${unexpected.length}건이 있어 변경을 중단함`,
    );
  }
}

const listingFilter = { sellerId: { $in: SEED_SELLER_IDS } };
const postFilter = { authorId: { $in: SEED_AUTHOR_IDS } };

const listings = db.listings
  .find(listingFilter, { _id: 1, sellerId: 1, status: 1 })
  .sort({ _id: 1 })
  .toArray();
const posts = db.posts
  .find(postFilter, { _id: 1, authorId: 1, status: 1 })
  .sort({ _id: 1 })
  .toArray();

const listingCounts = countByActor(listings, "sellerId", SEED_SELLER_IDS);
const postCounts = countByActor(posts, "authorId", SEED_AUTHOR_IDS);

assertExpectedProfile(
  "매물",
  listingCounts,
  SEED_SELLER_IDS,
  EXPECTED_LISTING_PROFILES,
  listings.length,
);
assertExpectedProfile(
  "게시글",
  postCounts,
  SEED_AUTHOR_IDS,
  EXPECTED_POST_PROFILES,
  posts.length,
);
assertKnownStatuses("매물", listings, ["ACTIVE", "DELETED"]);
assertKnownStatuses("게시글", posts, ["PUBLISHED", "DELETED"]);

const listingCandidates = listings.filter(
  (listing) => listing.status !== "DELETED",
);
const postCandidates = posts.filter((post) => post.status !== "DELETED");

print(`[${MIGRATION_ID}] DRY_RUN=${DRY_RUN}`);
print(`허용 seller: ${SEED_SELLER_IDS.join(", ")}`);
print(`허용 author: ${SEED_AUTHOR_IDS.join(", ")}`);
print(`확인한 매물: ${profileText(listingCounts, SEED_SELLER_IDS)}`);
print(`확인한 게시글: ${profileText(postCounts, SEED_AUTHOR_IDS)}`);
print(
  `예상 변경: 매물 ${listingCandidates.length}건, 게시글 ${postCandidates.length}건`,
);

function inspectReferences(listing) {
  const orderRefs = db.orders.countDocuments({ listingId: listing._id });
  const legacyRooms = db.chat_rooms
    .find(
      { listingId: listing._id },
      {
        _id: 1,
        buyerId: 1,
        sellerId: 1,
        buyerConfirmedAt: 1,
        sellerConfirmedAt: 1,
        directTradeCompletedAt: 1,
      },
    )
    .toArray();
  const socialChatRefs = db.social_chat_rooms.countDocuments({
    listingId: listing._id,
  });

  const knownEmptyOrphan =
    KNOWN_EMPTY_ORPHAN_CHAT_LISTING_IDS.has(String(listing._id)) &&
    orderRefs === 0 &&
    socialChatRefs === 0 &&
    legacyRooms.length === 1 &&
    legacyRooms[0].sellerId === listing.sellerId &&
    !legacyRooms[0].buyerConfirmedAt &&
    !legacyRooms[0].sellerConfirmedAt &&
    !legacyRooms[0].directTradeCompletedAt &&
    db.chat_messages.countDocuments({ roomId: legacyRooms[0]._id }) === 0 &&
    db.accounts.countDocuments({ _id: legacyRooms[0].buyerId }) === 0;

  return {
    id: listing._id,
    orderRefs,
    legacyChatRefs: legacyRooms.length,
    socialChatRefs,
    knownEmptyOrphan,
  };
}

const references = listingCandidates.map(inspectReferences);
const allowedOrphanCount = references.filter(
  ({ knownEmptyOrphan }) => knownEmptyOrphan,
).length;
const blockedListings = references.filter(
  ({ orderRefs, legacyChatRefs, socialChatRefs, knownEmptyOrphan }) =>
    !knownEmptyOrphan &&
    [orderRefs, legacyChatRefs, socialChatRefs].some((count) => count > 0),
);

if (allowedOrphanCount > 0) {
  print(`검증된 빈 고아 채팅 참조 보존: 매물 ${allowedOrphanCount}건`);
}

if (blockedListings.length > 0) {
  print(`참조 때문에 중단: 매물 ${blockedListings.length}건 (변경 0건)`);
  for (const blocked of blockedListings) {
    print(
      `  - listingId=${blocked.id} orders=${blocked.orderRefs} ` +
        `chat_rooms=${blocked.legacyChatRefs} social_chat_rooms=${blocked.socialChatRefs}`,
    );
  }
  throw new Error("주문/채팅 참조가 있는 데모 매물은 자동 비활성화하지 않음");
}

if (DRY_RUN) {
  print(
    "DRY_RUN=true이므로 변경하지 않음. 적용하려면 DRY_RUN=false를 명시해야 함.",
  );
} else {
  const deactivatedAt = new Date();
  const marker = { migrationId: MIGRATION_ID, deactivatedAt };

  const listingResult = db.listings.updateMany(
    {
      _id: { $in: listingCandidates.map((listing) => listing._id) },
      sellerId: { $in: SEED_SELLER_IDS },
      status: "ACTIVE",
    },
    { $set: { status: "DELETED", demoContentDeactivation: marker } },
  );
  if (listingResult.modifiedCount !== listingCandidates.length) {
    throw new Error(
      `매물 변경 건수 불일치: expected=${listingCandidates.length}, actual=${listingResult.modifiedCount}`,
    );
  }

  const postResult = db.posts.updateMany(
    {
      _id: { $in: postCandidates.map((post) => post._id) },
      authorId: { $in: SEED_AUTHOR_IDS },
      status: "PUBLISHED",
    },
    { $set: { status: "DELETED", demoContentDeactivation: marker } },
  );
  if (postResult.modifiedCount !== postCandidates.length) {
    throw new Error(
      `게시글 변경 건수 불일치: expected=${postCandidates.length}, actual=${postResult.modifiedCount}`,
    );
  }

  print(
    `변경 완료: 매물 ${listingResult.modifiedCount}건, 게시글 ${postResult.modifiedCount}건`,
  );
  print("문서는 보존됐으며 공개 조회에서는 DELETED 상태로 제외됨.");
}
