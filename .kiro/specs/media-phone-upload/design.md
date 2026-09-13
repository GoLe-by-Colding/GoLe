# 정규화 경계

MediaService는 업로드 MIME/signature 검증과 저장을 담당한다. NormalizeImageUseCase는 저장 없이 바이트를 정규화하는 공개 입력 포트이고 NormalizeImageService가 ImageProcessorPort에 위임한다. 반환 계약은 바이트·정규화 MIME·너비·높이이며 원본 파일명을 전파하지 않는다.

ImageIoImageProcessorAdapter는 JPEG/PNG 및 HEIF 변환 결과의 검증·재인코딩을 맡는다. HEIF는 제한된 자식 프로세스에서 고정 decoder 스크립트를 호출한다. 자식 환경변수를 비우고 네트워크를 요구하지 않으며 임시 파일을 종료 경로에서 정리한다. 프로세스 resource limit은 컨테이너나 OS 보안 sandbox와 동일하지 않다.

기존 5MiB/16MP 정책과 native 버전 고정, 운영 배포 및 로컬 설치 한계는 verification.md를 따른다. 브릭 필터는 공개 포트를 호출한 뒤 자체 4MiB PNG 입력 정책을 추가한다.
