import { useEffect, useState, type FormEvent } from "react";
import { registerMascotAsset } from "@gole/core/mascot";
import { uploadImage } from "@shared/api";
import { cn } from "@shared/lib";
import { Button, Field, Input, Textarea } from "@shared/ui";
import { errorText } from "../lib/mascot-format";

/** 미디어 서버와 같은 업로드 범위. */
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
const ACCEPTED_TYPES = ["image/png", "image/jpeg"];
const MIN_SIDE = 16;
const MAX_SIDE = 4096;

interface PickedImage {
  readonly file: File;
  readonly url: string;
  readonly width: number;
  readonly height: number;
}

function readImage(file: File): Promise<PickedImage> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () =>
      resolve({ file, url, width: image.naturalWidth, height: image.naturalHeight });
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("decode"));
    };
    image.src = url;
  });
}

function checkFile(file: File): string | null {
  if (!ACCEPTED_TYPES.includes(file.type)) return "PNG 또는 JPEG 파일만 올릴 수 있습니다.";
  if (file.size > MAX_UPLOAD_BYTES) return "5MB 이하 파일만 올릴 수 있습니다.";
  return null;
}

function checkSize(image: PickedImage): string | null {
  return image.width < MIN_SIDE ||
    image.height < MIN_SIDE ||
    image.width > MAX_SIDE ||
    image.height > MAX_SIDE
    ? `가로·세로 ${MIN_SIDE}~${MAX_SIDE}px 이미지만 올릴 수 있습니다.`
    : null;
}

export function MascotUploadForm({
  disabled,
  onUploaded,
  onError,
  setBusy,
}: {
  readonly disabled: boolean;
  readonly onUploaded: (assetId: string, name: string) => Promise<void>;
  readonly onError: (text: string) => void;
  readonly setBusy: (busy: boolean) => void;
}) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [image, setImage] = useState<PickedImage | null>(null);
  const [darkImage, setDarkImage] = useState<PickedImage | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [formKey, setFormKey] = useState(0);

  // 고른 파일의 미리보기 주소는 바뀌거나 화면을 떠날 때 돌려준다.
  useEffect(() => {
    return () => {
      if (image !== null) URL.revokeObjectURL(image.url);
    };
  }, [image]);
  useEffect(() => {
    return () => {
      if (darkImage !== null) URL.revokeObjectURL(darkImage.url);
    };
  }, [darkImage]);

  async function pick(file: File | undefined, target: "main" | "dark") {
    const set = target === "main" ? setImage : setDarkImage;
    setFileError(null);
    if (file === undefined) {
      set(null);
      return;
    }
    const fileProblem = checkFile(file);
    if (fileProblem !== null) {
      set(null);
      setFileError(fileProblem);
      return;
    }
    try {
      const picked = await readImage(file);
      const sizeProblem = checkSize(picked);
      if (sizeProblem !== null) {
        URL.revokeObjectURL(picked.url);
        set(null);
        setFileError(sizeProblem);
        return;
      }
      set(picked);
    } catch {
      set(null);
      setFileError("이미지를 읽지 못했습니다. 다른 파일을 골라 주세요.");
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (disabled || image === null || name.trim() === "") return;
    setBusy(true);
    try {
      const main = await uploadImage(image.file);
      const dark = darkImage === null ? null : await uploadImage(darkImage.file);
      const asset = await registerMascotAsset({
        name: name.trim(),
        description: description.trim(),
        imageKey: main.key,
        darkImageKey: dark?.key ?? null,
        width: image.width,
        height: image.height,
      });
      setName("");
      setDescription("");
      setImage(null);
      setDarkImage(null);
      setFormKey((key) => key + 1);
      await onUploaded(asset.id, asset.name);
    } catch (error) {
      onError(errorText(error, "올리지 못했습니다. 파일과 연결 상태를 확인해 주세요."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="min-w-0 space-y-3">
      <div className="space-y-1">
        <h3 className="font-semibold">새 마스코트 올리기</h3>
        <p className="text-sm break-keep text-neutral-600">
          PNG·JPEG, 5MB 이하. 배경이 투명한 PNG를 권장합니다. SVG는 보안상 받지 않습니다. 올린
          뒤에도 적용하기 전까지는 사이트에 보이지 않습니다.
        </p>
      </div>
      <form
        key={formKey}
        className="grid min-w-0 gap-4 rounded-xl border border-neutral-200 bg-white p-4 lg:grid-cols-2"
        onSubmit={(event) => void submit(event)}
      >
        <div className="min-w-0 space-y-4">
          <Field label="이름 (필수)" hint="최대 40자">
            {({ inputId, describedBy }) => (
              <Input
                id={inputId}
                aria-describedby={describedBy}
                maxLength={40}
                value={name}
                disabled={disabled}
                onChange={(event) => setName(event.target.value)}
              />
            )}
          </Field>
          <Field label="설명" hint="최대 200자">
            {({ inputId, describedBy }) => (
              <Textarea
                id={inputId}
                aria-describedby={describedBy}
                rows={2}
                maxLength={200}
                value={description}
                disabled={disabled}
                onChange={(event) => setDescription(event.target.value)}
              />
            )}
          </Field>
          <FileField
            label="마스코트 이미지 (필수)"
            disabled={disabled}
            onPick={(file) => void pick(file, "main")}
          />
          <FileField
            label="어두운 배경용 이미지 (선택)"
            hint="푸터처럼 어두운 배경에서 대신 씁니다. 없으면 위 이미지를 그대로 씁니다."
            disabled={disabled}
            onPick={(file) => void pick(file, "dark")}
          />
          {fileError !== null ? (
            <p role="alert" className="text-sm text-danger">
              {fileError}
            </p>
          ) : null}
        </div>
        <div className="min-w-0 space-y-3">
          <p className="text-xs font-medium text-neutral-500">
            {image === null
              ? "이미지를 고르면 여기서 미리 봅니다."
              : `${image.width}×${image.height}px`}
          </p>
          <div className="grid grid-cols-2 gap-3">
            <LocalPreview image={image} dark={false} />
            <LocalPreview image={darkImage ?? image} dark />
          </div>
          <Button
            type="submit"
            fullWidth
            disabled={disabled || image === null || name.trim() === ""}
          >
            올리기
          </Button>
        </div>
      </form>
    </section>
  );
}

function FileField({
  label,
  hint,
  disabled,
  onPick,
}: {
  readonly label: string;
  readonly hint?: string;
  readonly disabled: boolean;
  readonly onPick: (file: File | undefined) => void;
}) {
  return (
    <Field label={label} {...(hint === undefined ? {} : { hint })}>
      {({ inputId, describedBy }) => (
        <input
          id={inputId}
          aria-describedby={describedBy}
          type="file"
          accept={ACCEPTED_TYPES.join(",")}
          disabled={disabled}
          className="block w-full min-w-0 text-sm text-neutral-700 file:mr-3 file:rounded-md file:border file:border-neutral-300 file:bg-white file:px-3 file:py-2 file:text-sm file:font-medium"
          onChange={(event) => onPick(event.target.files?.[0])}
        />
      )}
    </Field>
  );
}

function LocalPreview({
  image,
  dark,
}: {
  readonly image: PickedImage | null;
  readonly dark: boolean;
}) {
  return (
    <div
      className={cn(
        "flex aspect-[4/3] min-w-0 items-center justify-center rounded-lg p-3",
        dark ? "bg-brand-950" : "border border-neutral-200 bg-neutral-50",
      )}
    >
      {image === null ? (
        <span className={cn("text-xs", dark ? "text-brand-200" : "text-neutral-400")}>
          {dark ? "어두운 배경" : "밝은 배경"}
        </span>
      ) : (
        // 아직 서버에 올리지 않은 로컬 파일(blob:) 미리보기다.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={image.url}
          width={image.width}
          height={image.height}
          alt=""
          className="max-h-full max-w-full object-contain"
        />
      )}
    </div>
  );
}
