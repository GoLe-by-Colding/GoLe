# 제작자 표시

- 사용자 확정 팀원은 김승찬(kscold), 김가원(wongakim-99), 김수민(codemaker-kim) 3명뿐이다. 이메일·전화번호는 제작자 표시에 넣지 않는다.
- 공개 팀 소개 `https://kscold.com/info/gole`에서 확인한 김승찬의 창립자/기술 리드, 김가원·김수민의 풀스택 개발자 역할만 쓴다.
- 콘솔은 사용자 제공 최종 `colding-melt.txt` 8줄을 그대로 사용한다. 각 줄 70자 이내, 고정폭·line-height:1, 지정된 8색을 쓴다. 별도 캐릭터를 넣지 않는다.
- 하나의 console.log로 아트·🧊 Made by COLDING·팀원별 이름과 GitHub·김승찬 블로그·연락 안내 https://kscold.com/info를 출력한다. 문서 로드당 한 번, 서버 및 NODE_ENV=test에서는 출력하지 않는다.
- metadata authors/creator, 기존 JSON-LD 그래프의 WebSite.creator와 Organization.founder/member를 연결한다. 김승찬 Person은 name=김승찬, url=https://kscold.com, sameAs=https://github.com/kscold여야 한다.
- /humans.txt는 비인증 200 text/plain; TEAM에 세 명 모두, 김승찬에는 Blog: https://kscold.com을 넣는다. head에 rel=author 링크를 둔다.
- 푸터 저작권 옆에 작은 회색 Made by kscold 일반 링크를 넣는다. 숨김 링크·nofollow·과장 표현은 넣지 않는다.
- 운영에는 이 작업만 배포한다. 직접 main 푸시·강제 푸시 없이 저장소 규약의 dev→main PR을 사용한다. 다른 미배포 변경이 생기면 병합 전에 중단한다.
