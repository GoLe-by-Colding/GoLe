# IP 안전 콘텐츠 — 구현 태스크

> 상태: **구현·배포 완료.** (2026-08-03 실측 감사로 소급 체크)
> 단, 1번 태스크의 문구는 요구사항 R1.3 개정으로 **대체**되었다(아래 참조).

## 백엔드
- [x] 1. `CatalogSeeder` 커버 이미지 정책 — **R1.3 방식으로 구현됨(태스크 문구는 폐기)**
      원래 문구는 "시드 세트 `imageUrl`을 null로"였으나, 요구사항 R1.3이 개정되면서
      **GoLe 오리지널 커버 아트를 MinIO에 호스팅**하는 방식으로 바뀌었다.
      현재 `CatalogSeeder.java:63`은 `/api/v1/media/catalog/{setNumber}.svg`를 넣는다.
      공식 이미지 복제가 아닌 자체 제작 그래픽이므로 R1.2 위반이 아니다(R1.3 명시).

## 프론트
- [x] 2. `widgets/site-footer` 상표 고지 푸터 + `(main)/layout`에 추가 (R4) — `site-footer.tsx:59`
- [x] 3. `LegoSetCard`에 레고 공식 페이지 외부 링크 (R2) — `lego-set-card.tsx:54-56`,
      `target="_blank"` + `rel="noopener noreferrer nofollow"` 모두 충족
- [x] 4. `create-listing-form` 사진 필드 안내 (R3.1) — `create-listing-form.tsx:253`
      "직접 촬영한 실물 사진을 올려주세요… 레고 공식 제품 이미지 도용은 금지됩니다."

## 검증/배포
- [x] 5. 프론트 build/lint + e2e(home/mobile) 통과
- [x] 6. 배포 + 푸터/링크 확인 — 프로덕션 `https://gole.co.kr/` 200

## 후속
- [x] 실제 사진 업로드(스토리지) 도입 — `image-upload` 스펙에서 완료(MinIO/S3 media 컨텍스트)
- [x] 매물 상세/커뮤니티에도 고지/링크 일관 적용 — 매물은 판매자 실물 사진 고지와 구조화된
      세트번호의 공식 검색 링크를 함께 표시한다. 커뮤니티 작성 폼은 직접 촬영·제작 이미지 원칙을
      안내하고, 게시글 상세는 신고와 콘텐츠 운영 원칙 링크를 제공한다. 공식 외부 링크 구현은
      `OfficialLegoLink` 한 컴포넌트로 통합해 새 탭·nofollow·외부 안내 접근성 계약을 고정한다.
