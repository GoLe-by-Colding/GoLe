"""홍보 초안의 러너(`gole_promotion_agent`)와 지인 서버(`gole_promotion_deliver`)가 함께 쓰는 계약과 규칙(D29).

모델 출력 계약·성찰 검증·상한·실행 원장·백엔드 클라이언트만 둔다. 어느 한쪽에서만 쓰는 것은 그쪽 패키지에 둔다.
이 패키지는 agent·deliver 를 import 하지 않는다.
"""

# 주문서에 실어 서버의 deliver 와 맞춘다. 출력 계약·주문서 모양을 바꾸면 올린다(D29).
CONTRACT_VERSION = 1
