import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const scriptUrl = new URL(
  "../migrate-production-demo-content.js",
  import.meta.url,
);
const source = readFileSync(fileURLToPath(scriptUrl), "utf8");

const SELLER_COUNTS = {
  "seller-aurora": 5,
  "seller-brickbank": 4,
  "seller-minifig": 4,
};
const AUTHOR_COUNTS = {
  "user-builder": 2,
  "user-collector": 3,
  "user-moc": 2,
  "user-newbie": 1,
};

function matches(document, query) {
  return Object.entries(query).every(([field, expected]) => {
    const actual = document[field];
    if (expected !== null && typeof expected === "object") {
      if (Object.hasOwn(expected, "$in")) return expected.$in.includes(actual);
      if (Object.hasOwn(expected, "$ne")) return actual !== expected.$ne;
    }
    return actual === expected;
  });
}

class FakeCursor {
  constructor(documents, projection) {
    this.documents = documents.map((document) => {
      if (projection === undefined) return { ...document };
      return Object.fromEntries(
        Object.entries(projection)
          .filter(([, included]) => included === 1)
          .map(([field]) => [field, document[field]]),
      );
    });
  }

  sort(specification) {
    const [[field, direction]] = Object.entries(specification);
    this.documents.sort(
      (left, right) =>
        String(left[field]).localeCompare(String(right[field])) * direction,
    );
    return this;
  }

  toArray() {
    return this.documents;
  }
}

class FakeCollection {
  constructor(documents = []) {
    this.documents = documents;
  }

  find(query, projection) {
    return new FakeCursor(
      this.documents.filter((document) => matches(document, query)),
      projection,
    );
  }

  findOne(query) {
    return this.documents.find((document) => matches(document, query)) ?? null;
  }

  countDocuments(query) {
    return this.documents.filter((document) => matches(document, query)).length;
  }

  updateMany(query, update) {
    let matchedCount = 0;
    let modifiedCount = 0;
    for (const document of this.documents) {
      if (!matches(document, query)) continue;
      matchedCount += 1;
      const before = JSON.stringify(document);
      Object.assign(document, update.$set);
      if (JSON.stringify(document) !== before) modifiedCount += 1;
    }
    return { matchedCount, modifiedCount };
  }
}

function actorDocuments(counts, actorField, prefix, status) {
  let sequence = 1;
  return Object.entries(counts).flatMap(([actorId, count]) =>
    Array.from({ length: count }, () => ({
      _id: `${prefix}-${String(sequence++).padStart(2, "0")}`,
      [actorField]: actorId,
      status,
      // 출력 금지 계약을 확인하기 위한 민감할 수 있는 평문 필드다.
      title: "출력되면 안 되는 제목",
      content: "출력되면 안 되는 본문",
      email: "private@example.test",
    })),
  );
}

function fixture() {
  return {
    listings: new FakeCollection(
      actorDocuments(SELLER_COUNTS, "sellerId", "listing", "ACTIVE"),
    ),
    posts: new FakeCollection(
      actorDocuments(AUTHOR_COUNTS, "authorId", "post", "PUBLISHED"),
    ),
    orders: new FakeCollection(),
    chat_rooms: new FakeCollection(),
    social_chat_rooms: new FakeCollection(),
    chat_messages: new FakeCollection(),
    accounts: new FakeCollection(),
  };
}

function execute(database, environment = {}) {
  const output = [];
  const context = vm.createContext({
    db: database,
    process: { env: environment },
    print: (message) => output.push(String(message)),
  });
  vm.runInContext(source, context, { filename: fileURLToPath(scriptUrl) });
  return output;
}

test("기본 실행은 dry-run이며 데이터와 평문 콘텐츠를 바꾸거나 출력하지 않는다", () => {
  const database = fixture();

  const output = execute(database);

  assert.match(output.join("\n"), /DRY_RUN=true/);
  assert.match(output.join("\n"), /예상 변경: 매물 13건, 게시글 8건/);
  assert.doesNotMatch(
    output.join("\n"),
    /출력되면 안 되는|private@example\.test/,
  );
  assert.ok(
    database.listings.documents.every(({ status }) => status === "ACTIVE"),
  );
  assert.ok(
    database.posts.documents.every(({ status }) => status === "PUBLISHED"),
  );
});

test("명시적 적용은 소프트 비활성화하고 재실행 시 변경 건수가 0이다", () => {
  const database = fixture();

  const firstOutput = execute(database, { DRY_RUN: "false" });
  const secondOutput = execute(database, { DRY_RUN: "false" });

  assert.match(firstOutput.join("\n"), /변경 완료: 매물 13건, 게시글 8건/);
  assert.match(secondOutput.join("\n"), /예상 변경: 매물 0건, 게시글 0건/);
  assert.match(secondOutput.join("\n"), /변경 완료: 매물 0건, 게시글 0건/);
  assert.ok(
    database.listings.documents.every(({ status }) => status === "DELETED"),
  );
  assert.ok(
    database.posts.documents.every(({ status }) => status === "DELETED"),
  );
  assert.ok(
    database.listings.documents.every(
      ({ demoContentDeactivation }) =>
        demoContentDeactivation.migrationId ===
        "2026-09-04-disable-legacy-demo-content",
    ),
  );
});

test("주문 또는 어느 채팅 저장소든 참조하면 전체 변경 없이 실패한다", () => {
  for (const collectionName of ["orders", "chat_rooms", "social_chat_rooms"]) {
    const database = fixture();
    database[collectionName].documents.push({
      _id: `ref-${collectionName}`,
      listingId: "listing-01",
    });

    assert.throws(
      () => execute(database, { DRY_RUN: "false" }),
      /주문\/채팅 참조가 있는 데모 매물/,
    );
    assert.ok(
      database.listings.documents.every(({ status }) => status === "ACTIVE"),
    );
    assert.ok(
      database.posts.documents.every(({ status }) => status === "PUBLISHED"),
    );
  }
});

test("검증된 빈 고아 채팅 한 건은 보존하고 해당 데모 매물만 비활성화한다", () => {
  const database = fixture();
  const listing = database.listings.documents[0];
  listing._id = "71f0dc28-9683-4855-9982-56ec15feda77";
  database.chat_rooms.documents.push({
    _id: "empty-orphan-room",
    listingId: listing._id,
    buyerId: "missing-buyer",
    sellerId: listing.sellerId,
  });

  const output = execute(database, { DRY_RUN: "false" });

  assert.match(output.join("\n"), /검증된 빈 고아 채팅 참조 보존: 매물 1건/);
  assert.match(output.join("\n"), /변경 완료: 매물 13건, 게시글 8건/);
  assert.equal(listing.status, "DELETED");
  assert.equal(database.chat_rooms.documents[0].listingId, listing._id);
});

test("예상 actor 개수 분포와 다른 데이터는 변경 전에 거부한다", () => {
  const database = fixture();
  database.listings.documents.push({
    _id: "listing-unexpected",
    sellerId: "seller-aurora",
    status: "ACTIVE",
  });

  assert.throws(
    () => execute(database, { DRY_RUN: "false" }),
    /데모 개수 분포가 예상과 다름/,
  );
  assert.ok(
    database.listings.documents.every(({ status }) => status === "ACTIVE"),
  );
  assert.ok(
    database.posts.documents.every(({ status }) => status === "PUBLISHED"),
  );
});
