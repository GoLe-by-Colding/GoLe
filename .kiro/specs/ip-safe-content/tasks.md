# IP 안전 콘텐츠 — 구현 태스크

## 백엔드
- [ ] 1. `CatalogSeeder` 시드 세트 `imageUrl`을 null로(공식 이미지 미호스팅) (R1.2)
      ※ 기존 시드 데이터 정정을 위해 lego_sets 재시드 필요(빈 컬렉션 조건)

## 프론트
- [ ] 2. `widgets/site-footer` 상표 고지 푸터 + `(main)/layout`에 추가 (R4)
- [ ] 3. `LegoSetCard`에 레고 공식 페이지 외부 링크(새 탭, noopener noreferrer nofollow) (R2)
- [ ] 4. `create-listing-form` 사진 필드 안내를 "직접 촬영 사진 필수, 공식 이미지 금지"로 (R3.1)

## 검증/배포
- [ ] 5. 프론트 build/lint + e2e(home/mobile) 통과
- [ ] 6. lego_sets 재시드(imageUrl null) 후 배포(deploy.sh) + 푸터/링크 확인

## 후속
- [ ] 실제 사진 업로드(스토리지) 도입
- [ ] 매물 상세/커뮤니티에도 고지/링크 일관 적용
