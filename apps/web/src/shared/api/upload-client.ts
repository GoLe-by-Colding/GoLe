import { env } from "@shared/config";
import { ApiError, type ApiErrorBody } from "./http-client";
import { readSessionAuthorization } from "./session-auth";

export interface UploadedImage {
  readonly key: string;
  readonly url: string;
}

/**
 * 이미지 파일을 백엔드(MinIO 경유)로 업로드한다.
 *
 * <p>JSON 클라이언트({@link apiRequest})와 분리한다: multipart/form-data 는
 * 브라우저가 boundary 를 포함한 Content-Type 을 자동 설정해야 하므로 헤더를 지정하지 않는다.
 */
export async function uploadImage(file: File, signal?: AbortSignal): Promise<UploadedImage> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${env.apiBaseUrl}/api/v1/media/images`, {
    method: "POST",
    credentials: "include",
    headers: readSessionAuthorization(),
    body: formData,
    signal: signal ?? null,
  });

  if (!response.ok) {
    const fallback: ApiErrorBody = {
      code: "UPLOAD_FAILED",
      message: `Image upload failed with status ${response.status}`,
    };
    const parsed = (await response.json().catch(() => fallback)) as ApiErrorBody;
    throw new ApiError(response.status, parsed);
  }

  return (await response.json()) as UploadedImage;
}

/**
 * 여러 이미지 파일을 한 번에 업로드한다. (백로그 N2 — 다중 이미지)
 */
export async function uploadImages(
  files: readonly File[],
  signal?: AbortSignal,
): Promise<readonly UploadedImage[]> {
  const formData = new FormData();
  for (const file of files) {
    formData.append("files", file);
  }

  const response = await fetch(`${env.apiBaseUrl}/api/v1/media/images/batch`, {
    method: "POST",
    credentials: "include",
    headers: readSessionAuthorization(),
    body: formData,
    signal: signal ?? null,
  });

  if (!response.ok) {
    const fallback: ApiErrorBody = {
      code: "UPLOAD_FAILED",
      message: `Image upload failed with status ${response.status}`,
    };
    const parsed = (await response.json().catch(() => fallback)) as ApiErrorBody;
    throw new ApiError(response.status, parsed);
  }

  return (await response.json()) as readonly UploadedImage[];
}
