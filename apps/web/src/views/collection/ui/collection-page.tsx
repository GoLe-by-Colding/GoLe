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
      <Container width="sm">
        <div className="flex flex-col items-start gap-4 pt-10">
          <Heading level={1}>내 컬렉션</Heading>
          <Text tone="secondary">컬렉션을 보려면 로그인이 필요합니다.</Text>
          <LinkButton href="/login">로그인하러 가기</LinkButton>
        </div>
      </Container>
    );
  }

  return (
    <Container width="md">
      <div className="flex flex-col gap-6 pt-8 pb-16">
        <div className="flex flex-col gap-1">
          <Heading level={1}>내 컬렉션</Heading>
          <Text tone="secondary">보유 세트의 추정 가치를 확인하세요.</Text>
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
          <Text tone="muted">아직 컬렉션이 비어 있습니다.</Text>
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
