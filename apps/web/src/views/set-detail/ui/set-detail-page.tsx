import Link from "next/link";
import type { LegoSet } from "@entities/lego-set";
import { isRetired } from "@entities/lego-set";
import type { Listing } from "@entities/listing";
import { formatPriceKrw } from "@entities/listing";
import { CONDITION_LABEL, priceEvidenceWarning, type PriceSnapshot } from "@entities/pricing";
import { ListingGrid } from "@widgets/listing-grid";
import { Badge, Card, Container, Heading, MediaImage, Text } from "@shared/ui";
import { thumbnailUrl } from "@shared/lib";

export interface SetDetailPageProps {
  readonly set: LegoSet;
  readonly listings: readonly Listing[];
  readonly snapshot: PriceSnapshot | null;
}

function StatCell({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-xs text-neutral-500">{label}</dt>
      <dd className="text-lg font-semibold text-neutral-900">{value}</dd>
    </div>
  );
}

/**
 * 세트 상세 — 롱테일 검색 유입의 착지 페이지.
 *
 * 서버 컴포넌트로 유지한다. 크롤러가 보는 첫 HTML에 세트 정보·시세·매물이 모두 들어 있어야
 * 색인 가치가 생긴다(클라이언트 로딩이면 빈 페이지가 색인된다).
 */
export function SetDetailPage({ set, listings, snapshot }: SetDetailPageProps) {
  const activeCount = listings.length;
  const evidenceWarning = snapshot === null ? null : priceEvidenceWarning(snapshot.provenance);

  return (
    <Container>
      <nav aria-label="탐색 경로" className="py-4 text-sm text-neutral-500">
        <Link href="/" className="hover:text-brand-600 hover:underline">
          홈
        </Link>
        <span className="mx-2" aria-hidden="true">
          ›
        </span>
        <span className="text-neutral-700">{set.name}</span>
      </nav>

      <header className="flex flex-col gap-6 pb-8 md:flex-row md:items-start">
        <div className="w-full shrink-0 overflow-hidden rounded-lg border border-neutral-200 bg-neutral-50 md:w-72">
          <MediaImage
            src={set.imageUrl === null ? null : thumbnailUrl(set.imageUrl, 640)}
            alt={`${set.name} (${set.setNumber})`}
            className="aspect-[4/3] w-full object-cover"
            fallback={
              <span className="flex flex-col items-center gap-1 font-mono">
                <span className="text-sm font-bold tracking-[0.2em]">SET</span>
                <span className="text-xs">#{set.setNumber}</span>
              </span>
            }
          />
        </div>

        <div className="flex flex-1 flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone="brand">{set.theme}</Badge>
            {isRetired(set) ? <Badge tone="danger">단종</Badge> : null}
          </div>

          <Heading level={1}>
            레고 {set.setNumber} {set.name}
          </Heading>

          <Text tone="muted">
            {set.name} 중고 매물과 실제 체결가 기반 시세를 한눈에 확인하세요.
          </Text>

          <dl className="mt-2 grid grid-cols-2 gap-4 sm:grid-cols-4">
            <StatCell label="세트번호" value={set.setNumber} />
            <StatCell label="부품 수" value={`${set.pieceCount.toLocaleString()}피스`} />
            <StatCell label="출시" value={`${set.releaseYear}년`} />
            <StatCell label="판매 중" value={`${activeCount}건`} />
          </dl>

          <a
            href={`https://www.lego.com/ko-kr/search?q=${encodeURIComponent(set.setNumber)}`}
            target="_blank"
            rel="noopener noreferrer nofollow"
            className="mt-2 inline-flex w-fit items-center gap-1 text-sm font-medium text-brand-600 hover:text-brand-700 hover:underline"
          >
            레고 공식 페이지에서 보기 ↗
          </a>
        </div>
      </header>

      <section className="pb-10" aria-labelledby="set-pricing-heading">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Heading level={2} id="set-pricing-heading">
            {set.name} 시세
          </Heading>
          {evidenceWarning === null ? null : <Badge tone="warning">{evidenceWarning}</Badge>}
        </div>
        <Card padded className="mt-4">
          {snapshot === null ? (
            <div className="flex flex-col gap-2 py-4">
              <Text weight="semibold">시세를 불러오지 못했어요</Text>
              <Text size="sm" tone="muted">
                체결 데이터가 없는 상태와 구분되는 일시적인 조회 오류입니다. 잠시 후 다시 확인해
                주세요.
              </Text>
            </div>
          ) : snapshot.state === "EMPTY" ? (
            <div className="flex flex-col gap-2 py-4">
              <Badge tone="neutral" className="w-fit">
                체결 전
              </Badge>
              <Text weight="semibold">아직 체결 시세가 없어요</Text>
              <Text size="sm" tone="muted">
                GoLe에서 결제하고 구매를 확정한 거래가 생기면 실제 체결가가 여기에 쌓여요.
              </Text>
            </div>
          ) : snapshot.state === "OBSERVATIONS_ONLY" ? (
            <div className="flex flex-col gap-4">
              <div className="flex flex-wrap items-center gap-3">
                <span className="text-2xl font-bold tabular-nums text-neutral-900">
                  {snapshot.observations[0] === undefined
                    ? "—"
                    : formatPriceKrw(snapshot.observations[0].price)}
                </span>
                <Badge tone="warning">체결 {snapshot.sampleCount}건 · 참고 단계</Badge>
              </div>
              <Text size="sm" tone="muted">
                실제 체결가는 확인됐지만 {snapshot.minimumSamples}건이 쌓이기 전까지 고저가와 추정
                시세는 표시하지 않아요.
              </Text>
              <ul className="divide-y divide-neutral-100 rounded-lg border border-neutral-200">
                {snapshot.observations.map((point, index) => (
                  <li
                    key={`${point.executedAt}-${index}`}
                    className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                  >
                    <span className="text-neutral-500">
                      {new Intl.DateTimeFormat("ko-KR", {
                        year: "numeric",
                        month: "2-digit",
                        day: "2-digit",
                      }).format(new Date(point.executedAt))}{" "}
                      · {CONDITION_LABEL[point.condition]}
                    </span>
                    <span className="font-semibold tabular-nums text-neutral-900">
                      {formatPriceKrw(point.price)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ) : snapshot.statistics?.hasData ? (
            <>
              <dl className="grid grid-cols-2 gap-6 sm:grid-cols-4">
                <StatCell
                  label="최근 체결가"
                  value={
                    snapshot.statistics.latestPrice === null
                      ? "—"
                      : formatPriceKrw(snapshot.statistics.latestPrice)
                  }
                />
                <StatCell
                  label="최저"
                  value={
                    snapshot.statistics.lowestPrice === null
                      ? "—"
                      : formatPriceKrw(snapshot.statistics.lowestPrice)
                  }
                />
                <StatCell
                  label="최고"
                  value={
                    snapshot.statistics.highestPrice === null
                      ? "—"
                      : formatPriceKrw(snapshot.statistics.highestPrice)
                  }
                />
                <StatCell label="체결 건수" value={`${snapshot.statistics.transactionCount}건`} />
              </dl>
              <Text size="sm" tone="muted" className="mt-4">
                {evidenceWarning === null
                  ? "GoLe에서 결제하고 구매확정된 거래 기준입니다."
                  : "데모·테스트 또는 출처 확인 전 체결이 포함된 참고용 시세입니다."}{" "}
                <Link href="/prices" className="text-brand-600 hover:underline">
                  전체 시세 보기
                </Link>
              </Text>
            </>
          ) : (
            <Text size="sm" tone="muted">
              검증된 체결 표본을 계산하는 중입니다.
            </Text>
          )}
        </Card>
      </section>

      <section className="pb-16" aria-labelledby="set-listings-heading">
        <Heading level={2} id="set-listings-heading">
          {set.name} 중고 매물
        </Heading>
        <div className="mt-4">
          <ListingGrid
            listings={listings}
            emptyMessage={`아직 등록된 ${set.name} 매물이 없습니다. 가장 먼저 판매해 보세요.`}
          />
        </div>
      </section>
    </Container>
  );
}
