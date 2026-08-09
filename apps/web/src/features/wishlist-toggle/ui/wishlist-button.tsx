"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { addWishlist, type WishlistTargetType } from "@entities/discovery";
import { useSession } from "@entities/user";
import { ApiError } from "@shared/api";
import { Button, HeartIcon } from "@shared/ui";

export interface WishlistButtonProps {
  readonly targetType: WishlistTargetType;
  readonly targetId: string;
}

export function WishlistButton({ targetType, targetId }: WishlistButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  async function handleSave() {
    if (!session) {
      router.push("/login");
      return;
    }
    if (saved || busy) {
      return;
    }
    setBusy(true);
    try {
      await addWishlist(session.accountId, targetType, targetId);
      setSaved(true);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setSaved(true);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Button variant="secondary" disabled={busy} aria-pressed={saved} onClick={handleSave}>
      <HeartIcon className="h-4 w-4" filled={saved} />
      {saved ? "위시 담음" : "위시 담기"}
    </Button>
  );
}
