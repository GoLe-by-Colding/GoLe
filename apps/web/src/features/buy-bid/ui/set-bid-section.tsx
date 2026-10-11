"use client";

import { type FormEvent, useEffect, useState } from "react";
import {
  BID_CONDITIONS,
  BID_DURATIONS,
  BID_ERROR,
  BID_RULES,
  bidBookCondition,
  bidBookTotal,
  bidErrorMessage,
  fetchBidBook,
  isBidRenewal,
  placeBid,
  validateBidPrice,
  type BidBook,
  type BidCondition,
  type BidDurationDays,
} from "@entities/bid";
import { conditionLabel, formatWon } from "@entities/listing";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { loginHrefWithReturnTo } from "@shared/lib";
import { Button, Card, Field, Input, LinkButton, Select, Text } from "@shared/ui";

export interface SetBidSectionProps {
  readonly setNumber: string;
  /**
   * 서버 렌더 때 읽은 호가창(1분 재검증). 크롤러가 보는 첫 HTML에도 수요가 보이게 한다.
   * 못 읽었으면 `null`이고 브라우저에서 다시 읽는다.
   */
  readonly initialBook: BidBook | null;
}

/**
 * 세트 상세의 "구매 입찰" — 상태별 최고 입찰가(= 지금 팔면 받는 값)·건수·호가 단과 입찰 폼. (buy-bids F1)
 *
 * 입찰을 걸면 호가창을 브라우저에서 바로 다시 읽는다. 같은 세트·상태의 진행 중 입찰이 있으면
 * 서버가 새로 만들지 않고 가격·기간을 바꾼다(D3).
 */
export function SetBidSection({ setNumber, initialBook }: SetBidSectionProps) {
  const { session } = useSession();
  const [book, setBook] = useState<BidBook | null>(initialBook);
  const [bookFailed, setBookFailed] = useState(false);
  // 0이면 서버 렌더 값을 그대로 쓴다. 서버가 못 읽었으면 처음부터 브라우저에서 읽는다.
  const [bookRequest, setBookRequest] = useState(initialBook === null ? 1 : 0);
  const [condition, setCondition] = useState<BidCondition>("new_sealed");
  const [priceInput, setPriceInput] = useState("");
  const [duration, setDuration] = useState<BidDurationDays>(BID_RULES.defaultDurationDays);
  const [submitting, setSubmitting] = useState(false);
  const [priceError, setPriceError] = useState<string | undefined>(undefined);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (bookRequest === 0) return;
    const controller = new AbortController();
    fetchBidBook(setNumber, controller.signal).then(
      (next) => {
        if (controller.signal.aborted) return;
        setBook(next);
        setBookFailed(false);
      },
      () => {
        if (!controller.signal.aborted) setBookFailed(true);
      },
    );
    return () => controller.abort();
  }, [bookRequest, setNumber]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const price = Number(priceInput.trim());
    const invalid =
      priceInput.trim().length === 0 ? "입찰가를 입력해 주세요." : validateBidPrice(price);
    setPriceError(invalid);
    setFormError(null);
    setNotice(null);
    if (invalid !== undefined) return;
    setSubmitting(true);
    try {
      const bid = await placeBid({ setNumber, condition, price, durationDays: duration });
      setNotice(
        isBidRenewal(bid)
          ? `${conditionLabel(bid.condition)} 입찰가를 ${formatWon(bid.price)}으로 바꿨어요. 기간은 ${bid.durationDays}일로 다시 잡혔어요.`
          : `${conditionLabel(bid.condition)} 상태에 ${formatWon(bid.price)} 입찰을 걸었어요. 판매자가 수락하면 알려 드려요.`,
      );
      setPriceInput("");
      setBookRequest((current) => current + 1);
    } catch (cause) {
      const message = bidErrorMessage(cause, "place");
      if (cause instanceof ApiError && cause.code === BID_ERROR.PRICE_INVALID) {
        setPriceError(message);
      } else {
        setFormError(message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  const total = book === null ? 0 : bidBookTotal(book);

  return (
    <div className="flex flex-col gap-4">
      <Card padded className="flex flex-col gap-3" data-testid="bid-book">
        {book === null ? (
          <div className="flex flex-wrap items-center gap-2 py-2">
            <Text size="sm" tone="muted">
              {bookFailed ? "입찰 현황을 불러오지 못했어요." : "입찰 현황을 불러오는 중이에요."}
            </Text>
            {bookFailed ? (
              <Button
                size="sm"
                variant="ghost"
                onClick={() => setBookRequest((current) => current + 1)}
              >
                다시 시도
              </Button>
            ) : null}
          </div>
        ) : (
          <>
            {total === 0 ? (
              <Text size="sm" tone="muted">
                아직 걸린 입찰이 없어요. 첫 입찰을 걸면 판매자에게 &ldquo;지금 팔면 받는
                값&rdquo;으로 보여요.
              </Text>
            ) : null}
            <ul className="flex flex-col divide-y divide-neutral-100">
              {BID_CONDITIONS.map((key) => {
                const row = bidBookCondition(book, key);
                return (
                  <li
                    key={key}
                    data-testid="bid-book-row"
                    data-condition={key}
                    className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1 py-2.5 first:pt-0 last:pb-0"
                  >
                    <div className="flex min-w-0 flex-col gap-1">
                      <span className="text-sm font-semibold text-neutral-900">
                        {conditionLabel(key)}
                      </span>
                      <span className="text-xs text-neutral-500">입찰 {row.bidCount}건</span>
                      {row.levels.length > 0 ? (
                        <ul
                          aria-label={`${conditionLabel(key)} 호가`}
                          className="flex flex-wrap gap-1"
                        >
                          {row.levels.map((level) => (
                            <li
                              key={level.price}
                              className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs tabular-nums text-neutral-600"
                            >
                              {formatWon(level.price)}
                              {level.count > 1 ? ` ×${level.count}` : ""}
                            </li>
                          ))}
                        </ul>
                      ) : null}
                    </div>
                    <div className="flex flex-col items-end gap-0.5">
                      <span className="text-xs text-neutral-500">지금 팔면 받는 값</span>
                      <span className="text-lg font-bold tabular-nums text-neutral-900">
                        {row.highestPrice === null ? "—" : formatWon(row.highestPrice)}
                      </span>
                    </div>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </Card>

      <Card padded className="flex flex-col gap-3">
        <div className="flex flex-col gap-1">
          <h3 id="bid-form-heading" className="text-base font-semibold text-neutral-900">
            입찰하기
          </h3>
          <Text size="sm" tone="muted">
            원하는 상태와 가격을 걸어 두면 판매자가 그 가격에 바로 팔 수 있어요. 수락되면 72시간
            안에 그 가격으로 거래를 진행해요. 같은 상태에 진행 중인 입찰이 있으면 가격·기간이
            바뀌어요.
          </Text>
        </div>
        {session === null ? (
          <LinkButton
            href={loginHrefWithReturnTo(`/sets/${encodeURIComponent(setNumber)}`)}
            size="sm"
            variant="secondary"
            className="self-start"
          >
            로그인하고 입찰하기
          </LinkButton>
        ) : (
          <form
            aria-labelledby="bid-form-heading"
            onSubmit={(event) => void handleSubmit(event)}
            className="flex flex-col gap-3"
            noValidate
          >
            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="상태">
                {({ inputId, describedBy }) => (
                  <Select
                    id={inputId}
                    aria-describedby={describedBy}
                    value={condition}
                    onChange={(event) => setCondition(event.target.value as BidCondition)}
                  >
                    {BID_CONDITIONS.map((key) => (
                      <option key={key} value={key}>
                        {conditionLabel(key)}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
              <Field label="입찰가 (원)" error={priceError}>
                {({ inputId, describedBy }) => (
                  <Input
                    id={inputId}
                    aria-describedby={describedBy}
                    invalid={priceError !== undefined}
                    type="number"
                    inputMode="numeric"
                    min={BID_RULES.minPrice}
                    max={BID_RULES.maxPrice}
                    step={1}
                    value={priceInput}
                    onChange={(event) => setPriceInput(event.target.value)}
                    placeholder="예: 250000"
                  />
                )}
              </Field>
              <Field label="기간">
                {({ inputId, describedBy }) => (
                  <Select
                    id={inputId}
                    aria-describedby={describedBy}
                    value={duration}
                    onChange={(event) => setDuration(Number(event.target.value) as BidDurationDays)}
                  >
                    {BID_DURATIONS.map((days) => (
                      <option key={days} value={days}>
                        {days}일
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
            </div>
            <Button type="submit" size="sm" className="self-start" disabled={submitting}>
              {submitting ? "거는 중…" : "입찰하기"}
            </Button>
            {notice !== null ? (
              <p role="status" className="text-sm text-success">
                {notice} 프로필의 &lsquo;입찰&rsquo; 탭에서 확인하고 취소할 수 있어요.
              </p>
            ) : null}
            {formError !== null ? (
              <p role="alert" className="text-sm text-danger">
                {formError}
              </p>
            ) : null}
          </form>
        )}
      </Card>
    </div>
  );
}
