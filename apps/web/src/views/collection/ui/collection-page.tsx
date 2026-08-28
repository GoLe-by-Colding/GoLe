"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  addCollectionItem,
  fetchCollection,
  fetchOwnedEstimate,
  ownershipLabel,
  removeCollectionItem,
  type CollectionItem,
  type OwnershipStatus,
} from "@entities/collection";
import { useSession } from "@entities/user";
import { formatKrw } from "@shared/lib";
import {
  Badge,
  Button,
  Card,
  Container,
  EmptyState,
  Heading,
  Input,
  LinkButton,
  Select,
  Text,
} from "@shared/ui";

const STATUSES: readonly OwnershipStatus[] = ["owned", "wanted", "sold"];

export function CollectionPage() {
  const { session } = useSession();
  const accountId = session?.accountId ?? null;

  const [items, setItems] = useState<readonly CollectionItem[]>([]);
  const [estimate, setEstimate] = useState(0);
  const [setNumber, setSetNumber] = useState("");
  const [status, setStatus] = useState<OwnershipStatus>("owned");
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async () => {
    if (accountId === null) {
      return;
    }
    const [list, est] = await Promise.all([
      fetchCollection(accountId),
      fetchOwnedEstimate(accountId),
    ]);
    setItems(list);
    setEstimate(est);
  }, [accountId]);

  useEffect(() => {
    let active = true;
    void (async () => {
      if (accountId === null) {
        return;
      }
      const [list, est] = await Promise.all([
        fetchCollection(accountId),
        fetchOwnedEstimate(accountId),
      ]);
      if (active) {
        setItems(list);
        setEstimate(est);
      }
    })();
    return () => {
      active = false;
    };
  }, [accountId]);

  async function handleAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (accountId === null || setNumber.trim().length === 0) {
      return;
    }
    setBusy(true);
    try {
      await addCollectionItem(accountId, setNumber.trim(), status);
      setSetNumber("");
      await reload();
    } finally {
      setBusy(false);
    }
  }

  async function handleRemove(itemId: string) {
    if (accountId === null) {
      return;
    }
    await removeCollectionItem(itemId, accountId);
    await reload();
  }

  if (accountId === null) {
    return (
      <Container width="lg">
        <div className="flex flex-col gap-7 pt-10 pb-16 sm:pt-14">
          <div className="flex flex-col gap-2">
            <Heading level={1}>내 컬렉션</Heading>
            <Text tone="secondary">좋아하는 세트를 모으고 시세 변화를 한눈에 확인하세요.</Text>
          </div>
          <EmptyState
            eyebrow="나만의 브릭 선반"
            title="로그인하고 첫 컬렉션을 시작하세요"
            description="보유·위시·판매 완료 세트를 한곳에 모으면 현재 추정 가치와 거래 흐름을 계속 이어서 볼 수 있어요."
            details={["보유 세트 추정가", "위시·판매 상태 관리"]}
            action={
              <LinkButton href={`/login?returnTo=${encodeURIComponent("/collection")}`}>
                로그인하고 시작하기
              </LinkButton>
            }
          />
        </div>
      </Container>
    );
  }

  return (
    <Container width="lg">
      <div className="flex flex-col gap-6 pt-10 pb-16">
        <div className="flex flex-col gap-1">
          <Heading level={1}>내 컬렉션</Heading>
          <Text tone="secondary">보유·위시 세트와 현재 추정 가치.</Text>
        </div>

        <Card padded className="flex items-center justify-between">
          <Text tone="secondary">보유 추정가</Text>
          <span className="text-2xl font-bold">{formatKrw(estimate)}</span>
        </Card>

        <Card padded>
          <form className="flex flex-wrap items-end gap-3" onSubmit={handleAdd}>
            <div className="flex flex-1 min-w-[160px] flex-col gap-1">
              <label className="text-sm font-medium text-neutral-600" htmlFor="c-set">
                세트 번호
              </label>
              <Input
                id="c-set"
                value={setNumber}
                placeholder="10307"
                onChange={(e) => setSetNumber(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-sm font-medium text-neutral-600" htmlFor="c-status">
                상태
              </label>
              <Select
                id="c-status"
                value={status}
                onChange={(e) => setStatus(e.target.value as OwnershipStatus)}
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {ownershipLabel(s)}
                  </option>
                ))}
              </Select>
            </div>
            <Button type="submit" disabled={busy}>
              추가
            </Button>
          </form>
        </Card>

        {items.length === 0 ? (
          <EmptyState
            title="아직 담은 세트가 없어요"
            description="위 칸에 세트 번호를 넣고 상태를 고르면 목록에 추가됩니다."
            details={["세트별 보유 상태 기록", "컬렉션 추정가 자동 합산"]}
          />
        ) : (
          <ul className="flex flex-col gap-2">
            {items.map((item) => (
              <li key={item.id}>
                <Card padded className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <Badge tone={item.status === "owned" ? "brand" : "neutral"}>
                      {ownershipLabel(item.status)}
                    </Badge>
                    <span className="font-mono text-sm">#{item.setNumber}</span>
                  </div>
                  <Button variant="ghost" size="sm" onClick={() => void handleRemove(item.id)}>
                    삭제
                  </Button>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Container>
  );
}
