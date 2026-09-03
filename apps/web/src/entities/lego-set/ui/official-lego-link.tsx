export interface OfficialLegoLinkProps {
  readonly setNumber: string;
  readonly label?: string;
  readonly className?: string;
}

/** 공식 이미지를 복제하지 않고 LEGO 검색 페이지로만 연결하는 IP-safe 외부 링크. */
export function OfficialLegoLink({
  setNumber,
  label = "레고 공식 페이지에서 보기",
  className = "",
}: OfficialLegoLinkProps) {
  return (
    <a
      href={"https://www.lego.com/ko-kr/search?q=" + encodeURIComponent(setNumber)}
      target="_blank"
      rel="noopener noreferrer nofollow"
      className={className}
    >
      {label} <span aria-hidden="true">↗</span>
      <span className="sr-only">(새 탭)</span>
    </a>
  );
}
