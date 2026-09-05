import { requireConfig } from "./config";
import { ApiError, type ApiErrorBody } from "./api-error";
import { getSessionStore } from "./session-store";

export interface UploadedImage {
  readonly key: string;
  readonly url: string;
}

/**
 * 업로드 가능한 이미지. 웹은 `File`, 앱은 `{ uri, name, type }`이다.
 *
 * <p>`FormData`는 두 플랫폼 모두 전역으로 있고 아래 두 형태를 그대로 받으므로, 분기는
 * <b>타입 수준에서만</b> 필요하고 런타임 코드는 한 벌이다.
 */
export interface NativeImageFile {
  readonly uri: string;
  readonly name: string;
  readonly type: string;
}

export type UploadableImage = File | NativeImageFile;

/**
 * `FormData.append`의 값 타입으로 좁힌다.
 *
 * RN의 FormData는 `{ uri, name, type }` 객체를 파일 값으로 받도록 구현돼 있지만 표준 타입에는
 * 그 형태가 없다. 캐스팅은 여기 한 곳에 가둔다.
 */
function toFormValue(image: UploadableImage): Blob {
  return image as unknown as Blob;
}

/**
 * 이미지 파일을 백엔드(MinIO 경유)로 업로드한다.
 *
 * <p>JSON 클라이언트({@link apiRequest})와 분리한다: multipart/form-data 는
 * 런타임이 boundary 를 포함한 Content-Type 을 자동 설정해야 하므로 헤더를 지정하지 않는다.
 */
export async function uploadImage(
  file: UploadableImage,
  signal?: AbortSignal,
): Promise<UploadedImage> {
  const formData = new FormData();
  formData.append("file", toFormValue(file));

  return postImages<UploadedImage>("/api/v1/media/images", formData, signal);
}

/**
 * 여러 이미지 파일을 한 번에 업로드한다. (백로그 N2 — 다중 이미지)
 */
export async function uploadImages(
  files: readonly UploadableImage[],
  signal?: AbortSignal,
): Promise<readonly UploadedImage[]> {
  const formData = new FormData();
  for (const file of files) {
    formData.append("files", toFormValue(file));
  }

  return postImages<readonly UploadedImage[]>("/api/v1/media/images/batch", formData, signal);
}

async function postImages<TResponse>(
  path: string,
  formData: FormData,
  signal: AbortSignal | undefined,
): Promise<TResponse> {
  const response = await fetch(`${requireConfig().apiBaseUrl}${path}`, {
    method: "POST",
    credentials: "include",
    headers: getSessionStore().readAuthorizationHeader(),
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

  return (await response.json()) as TResponse;
}
