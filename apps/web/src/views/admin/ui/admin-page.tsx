"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  createAdminSet,
  fetchAdminOverview,
  fetchAdminSets,
  type AdminLegoSet,
  type CreateSetInput,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Badge, Button, Card, Container, Field, Heading, Input, Select, Text } from "@shared/ui";

const COUNT_LABEL: Record<string, string> = {
  accounts: "회원",
  lego_sets: "카탈로그 세트",
  listings: "매물",
  orders: "주문",
  posts: "게시글",
  reviews: "후기",
  price_transactions: "시세 체결",
};

const EMPTY_FORM: CreateSetInput = {
  setNumber: "",
  name: "",
  theme: "",
  pieceCount: 0,
  releaseYear: new Date().getFullYear(),
  retirementStatus: "ACTIVE",
  imageUrl: "",
  featured: false,
};

export function AdminPage() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;
  const isAdmin = session?.role === "ADMIN";

  const [counts, setCounts] = useState<Readonly<Record<string, number>> | null>(null);
  const [sets, setSets] = useState<readonly AdminLegoSet[]>([]);
  const [form, setForm] = useState<CreateSetInput>(EMPTY_FORM);
  const [error, setError] = useState<string | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (token === null || !isAdmin) {
      return;
    }
    let active = true;
    void (async () => {
      try {
        const [overview, list] = await Promise.all([
          fetchAdminOverview(token),
          fetchAdminSets(token),
        ]);
        if (active) {
          setCounts(overview.counts);
          setSets(list);
        }
      } catch (cause) {
        if (active) {
          setError(
            cause instanceof ApiError ? cause.message : "관리자 데이터를 불러오지 못했습니다.",
          );
        }
      }
    })();
    return () => {
      active = false;
    };
  }, [token, isAdmin, reloadKey]);

  async function submit() {
    if (token === null) {
      return;
    }
    setError(undefined);
    setBusy(true);
    try {
      await createAdminSet(token, { ...form, pieceCount: Number(form.pieceCount) });
      setForm(EMPTY_FORM);
      setReloadKey((k) => k + 1);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "세트 등록에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  if (session === null) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-center gap-4 pt-20 pb-16 text-center">
          <Heading level={2}>관리자 로그인이 필요합니다</Heading>
          <Text tone="muted">관리자 계정으로 로그인해 주세요.</Text>
          <Link href="/login">
            <Button>로그인</Button>
          </Link>
        </div>
      </Container>
    );
  }

  if (!isAdmin) {
    return (
      <Container width="sm">
        <div className="flex flex-col items-center gap-4 pt-20 pb-16 text-center">
          <Heading level={2}>접근 권한이 없습니다</Heading>
          <Text tone="muted">이 페이지는 관리자(ADMIN)만 이용할 수 있습니다.</Text>
          <Link href="/">
            <Button variant="secondary">홈으로</Button>
          </Link>
        </div>
      </Container>
    );
  }

  return (
    <Container width="xl">
      <div className="flex flex-col gap-8 pt-8 pb-16">
        <div className="flex items-center gap-3">
          <Heading level={1}>관리자 대시보드</Heading>
          <Badge tone="brand">ADMIN</Badge>
        </div>

        {error ? <p className="text-sm text-danger">{error}</p> : null}

        <section className="flex flex-col gap-3">
          <Heading level={3}>현황</Heading>
          <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(160px,1fr))]">
            {counts === null ? (
              <Text tone="muted">불러오는 중...</Text>
            ) : (
              Object.entries(counts).map(([key, value]) => (
                <Card key={key} padded className="flex flex-col gap-1">
                  <Text tone="secondary" size="sm">
                    {COUNT_LABEL[key] ?? key}
                  </Text>
                  <span className="text-2xl font-bold tracking-tight">
                    {value.toLocaleString("ko-KR")}
                  </span>
                </Card>
              ))
            )}
          </div>
        </section>

        <section className="grid gap-6 lg:[grid-template-columns:340px_1fr]">
          <Card padded className="flex h-fit flex-col gap-4">
            <Heading level={3}>카탈로그 세트 등록</Heading>
            <Field label="세트 번호">
              {({ inputId }) => (
                <Input
                  id={inputId}
                  value={form.setNumber}
                  onChange={(e) => setForm({ ...form, setNumber: e.target.value })}
                />
              )}
            </Field>
            <Field label="이름">
              {({ inputId }) => (
                <Input
                  id={inputId}
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              )}
            </Field>
            <Field label="테마">
              {({ inputId }) => (
                <Input
                  id={inputId}
                  value={form.theme}
                  onChange={(e) => setForm({ ...form, theme: e.target.value })}
                />
              )}
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="피스 수">
                {({ inputId }) => (
                  <Input
                    id={inputId}
                    type="number"
                    value={form.pieceCount}
                    onChange={(e) => setForm({ ...form, pieceCount: Number(e.target.value) })}
                  />
                )}
              </Field>
              <Field label="출시 연도">
                {({ inputId }) => (
                  <Input
                    id={inputId}
                    type="number"
                    value={form.releaseYear}
                    onChange={(e) => setForm({ ...form, releaseYear: Number(e.target.value) })}
                  />
                )}
              </Field>
            </div>
            <Field label="단종 상태">
              {({ inputId }) => (
                <Select
                  id={inputId}
                  value={form.retirementStatus}
                  onChange={(e) =>
                    setForm({ ...form, retirementStatus: e.target.value as "ACTIVE" | "RETIRED" })
                  }
                >
                  <option value="ACTIVE">판매중(ACTIVE)</option>
                  <option value="RETIRED">단종(RETIRED)</option>
                </Select>
              )}
            </Field>
            <Field label="이미지 URL">
              {({ inputId }) => (
                <Input
                  id={inputId}
                  value={form.imageUrl}
                  onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
                />
              )}
            </Field>
            <label className="flex items-center gap-2 text-sm text-neutral-600">
              <input
                type="checkbox"
                checked={form.featured}
                onChange={(e) => setForm({ ...form, featured: e.target.checked })}
              />
              홈 추천(featured)으로 노출
            </label>
            <Button disabled={busy} onClick={submit}>
              {busy ? "등록 중..." : "세트 등록"}
            </Button>
          </Card>

          <Card padded className="flex flex-col gap-3">
            <Heading level={3}>카탈로그 세트 ({sets.length})</Heading>
            <div className="flex flex-col divide-y divide-neutral-100">
              {sets.map((s) => (
                <div key={s.setNumber} className="flex items-center justify-between py-2 text-sm">
                  <span className="font-mono text-neutral-500">#{s.setNumber}</span>
                  <span className="flex-1 truncate px-3 font-medium">{s.name}</span>
                  <span className="text-neutral-500">{s.theme}</span>
                  <span className="ml-3">
                    <Badge tone={s.retirementStatus === "RETIRED" ? "warning" : "success"}>
                      {s.retirementStatus === "RETIRED" ? "단종" : "판매중"}
                    </Badge>
                  </span>
                </div>
              ))}
              {sets.length === 0 ? <Text tone="muted">등록된 세트가 없습니다.</Text> : null}
            </div>
          </Card>
        </section>
      </div>
    </Container>
  );
}
