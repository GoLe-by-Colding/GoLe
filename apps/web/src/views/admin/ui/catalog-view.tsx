"use client";

import { useCallback, useEffect, useState } from "react";
import {
  createAdminSet,
  fetchAdminSets,
  setAdminSetFeatured,
  updateAdminSet,
  type AdminLegoSet,
  type CreateSetInput,
} from "@entities/admin";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Badge, Button, Card, Field, Heading, Input, Select, Text } from "@shared/ui";
import { AdminStatus, AdminTable } from "./table";

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

function validateCatalogForm(form: CreateSetInput, editing: string | null): string | undefined {
  if (editing === null && form.setNumber.trim() === "") return "세트 번호를 입력해 주세요.";
  if (form.name.trim() === "") return "세트 이름을 입력해 주세요.";
  if (form.theme.trim() === "") return "테마를 입력해 주세요.";
  if (!Number.isInteger(form.pieceCount) || form.pieceCount < 0)
    return "피스 수는 0 이상의 정수여야 합니다.";
  const maxYear = new Date().getFullYear() + 1;
  if (!Number.isInteger(form.releaseYear) || form.releaseYear < 1949 || form.releaseYear > maxYear)
    return `출시 연도는 1949~${maxYear} 사이여야 합니다.`;
  const imageUrl = form.imageUrl.trim();
  if (
    imageUrl !== "" &&
    !/^\/api\/v1\/media\/catalog\/[A-Za-z0-9][A-Za-z0-9._-]{0,127}\.svg$/.test(imageUrl)
  )
    return "이미지는 /api/v1/media/catalog/...svg 내부 경로만 사용할 수 있습니다.";
  return undefined;
}

/** 카탈로그 관리 — 세트 등록/수정/추천 토글. (요구사항 7.2~7.4) */
export function AdminCatalogView() {
  const { session } = useSession();
  const token = session?.sessionToken ?? null;

  // null = 아직 불러오지 않음. 로딩 상태를 파생시켜 effect 안에서 setState를 동기 호출하지 않는다.
  const [sets, setSets] = useState<readonly AdminLegoSet[] | null>(null);
  const [form, setForm] = useState<CreateSetInput>(EMPTY_FORM);
  /** 값이 있으면 수정 모드(해당 setNumber를 갱신), 없으면 신규 등록. */
  const [editing, setEditing] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const validationError = validateCatalogForm(form, editing);
  const normalizedQuery = query.trim().toLocaleLowerCase("ko-KR");
  const visibleSets = (sets ?? []).filter((set) => {
    if (normalizedQuery === "") return true;
    return [set.setNumber, set.name, set.theme].some((value) =>
      value.toLocaleLowerCase("ko-KR").includes(normalizedQuery),
    );
  });

  const load = useCallback(() => {
    if (token === null) {
      return;
    }
    void fetchAdminSets(token)
      .then(setSets)
      .catch((cause: unknown) => {
        setSets([]);
        setError(cause instanceof ApiError ? cause.message : "카탈로그를 불러오지 못했습니다.");
      });
  }, [token]);

  useEffect(load, [load]);

  function startEdit(set: AdminLegoSet) {
    setEditing(set.setNumber);
    setForm({
      setNumber: set.setNumber,
      name: set.name,
      theme: set.theme,
      pieceCount: set.pieceCount,
      releaseYear: set.releaseYear,
      retirementStatus: set.retirementStatus === "RETIRED" ? "RETIRED" : "ACTIVE",
      imageUrl: set.imageUrl ?? "",
      featured: set.featured,
    });
  }

  function cancelEdit() {
    setEditing(null);
    setForm(EMPTY_FORM);
  }

  async function submit() {
    if (token === null || validationError !== undefined) {
      return;
    }
    setError(undefined);
    setBusy(true);
    try {
      const payload = {
        ...form,
        pieceCount: Number(form.pieceCount),
        imageUrl: form.imageUrl.trim(),
      };
      if (editing !== null) {
        await updateAdminSet(token, editing, {
          name: payload.name,
          theme: payload.theme,
          pieceCount: payload.pieceCount,
          releaseYear: payload.releaseYear,
          retirementStatus: payload.retirementStatus,
          imageUrl: payload.imageUrl,
          featured: payload.featured,
        });
      } else {
        await createAdminSet(token, payload);
      }
      cancelEdit();
      load();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "저장에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function toggleFeatured(setNumber: string) {
    if (token === null) {
      return;
    }
    const target = sets?.find((set) => set.setNumber === setNumber);
    if (target === undefined) {
      return;
    }
    const next = !target.featured;
    setError(undefined);
    try {
      const updated = await setAdminSetFeatured(token, setNumber, next);
      setSets(
        (current) =>
          current?.map((set) => (set.setNumber === updated.setNumber ? updated : set)) ?? null,
      );
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "추천 설정에 실패했습니다.");
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Heading level={2}>카탈로그 관리</Heading>
      <AdminStatus error={error} loading={sets === null} />

      <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-6 xl:[grid-template-columns:320px_minmax(0,1fr)]">
        <Card padded className="flex min-w-0 h-fit flex-col gap-4 max-sm:p-4">
          <Heading level={3}>{editing !== null ? `세트 수정 · #${editing}` : "세트 등록"}</Heading>
          <Field label="세트 번호">
            {({ inputId }) => (
              <Input
                id={inputId}
                value={form.setNumber}
                disabled={editing !== null}
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
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
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
          <Field label="내부 이미지 경로">
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
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button disabled={busy || validationError !== undefined} onClick={submit} fullWidth>
              {busy ? "저장 중..." : editing !== null ? "수정 저장" : "세트 등록"}
            </Button>
            {editing !== null ? (
              <Button
                variant="secondary"
                onClick={cancelEdit}
                disabled={busy}
                className="max-sm:w-full"
              >
                취소
              </Button>
            ) : null}
          </div>
          {validationError !== undefined ? (
            <Text size="sm" className="text-danger" role="status">
              {validationError}
            </Text>
          ) : null}
        </Card>

        <div className="flex min-w-0 flex-col gap-3">
          <div className="flex flex-col items-stretch gap-2 sm:flex-row sm:items-end sm:justify-between sm:gap-3">
            <div className="w-full sm:max-w-md">
              <Field label="번호·이름·테마 검색">
                {({ inputId }) => (
                  <Input
                    id={inputId}
                    type="search"
                    value={query}
                    placeholder="예: 10307, Eiffel, Icons"
                    onChange={(event) => setQuery(event.target.value)}
                  />
                )}
              </Field>
            </div>
            <Text tone="muted" size="sm">
              {normalizedQuery === ""
                ? `최근 등록 세트 ${visibleSets.length}개`
                : `검색 결과 ${visibleSets.length}개 / 조회 ${(sets ?? []).length}개`}
            </Text>
          </div>
          <AdminTable
            caption="브릭 세트 카탈로그 목록"
            headers={["번호", "이름", "테마", "피스", "상태", "관리"]}
            alignRight={[3, 5]}
            minWidth={640}
            empty="등록된 세트가 없습니다."
            rowCount={visibleSets.length}
          >
            {visibleSets.map((s) => (
              <tr key={s.setNumber} className="border-t border-neutral-100">
                <td className="px-3 py-2.5 font-mono text-neutral-500">#{s.setNumber}</td>
                <td className="max-w-[220px] truncate px-3 py-2.5 font-medium">{s.name}</td>
                <td className="px-3 py-2.5 text-neutral-600">{s.theme}</td>
                <td className="px-3 py-2.5 text-right tabular-nums">
                  {s.pieceCount.toLocaleString("ko-KR")}
                </td>
                <td className="px-3 py-2.5">
                  <Badge tone={s.retirementStatus === "RETIRED" ? "warning" : "success"}>
                    {s.retirementStatus === "RETIRED" ? "단종" : "판매중"}
                  </Badge>
                </td>
                <td className="px-3 py-2.5 text-right">
                  <span className="inline-flex gap-1">
                    <Button size="sm" variant="ghost" onClick={() => startEdit(s)}>
                      수정
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => toggleFeatured(s.setNumber)}
                      aria-pressed={s.featured}
                    >
                      {s.featured ? "추천 해제" : "추천"}
                    </Button>
                  </span>
                </td>
              </tr>
            ))}
          </AdminTable>
        </div>
      </div>
    </div>
  );
}
