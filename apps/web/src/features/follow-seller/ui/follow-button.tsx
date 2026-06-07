"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { fetchFollowing, followSeller, unfollowSeller } from "@entities/discovery";
import { useSession } from "@entities/user";
import { Button } from "@shared/ui";

export interface FollowButtonProps {
  readonly sellerId: string;
}

export function FollowButton({ sellerId }: FollowButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const [following, setFollowing] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    void (async () => {
      if (accountId === null) {
        return;
      }
      try {
        const sellers = await fetchFollowing(accountId);
        if (active) {
          setFollowing(sellers.includes(sellerId));
        }
      } catch {
        /* ignore */
      }
    })();
    return () => {
      active = false;
    };
  }, [accountId, sellerId]);

  async function toggle() {
    if (accountId === null) {
      router.push("/login");
      return;
    }
    setBusy(true);
    try {
      if (following) {
        await unfollowSeller(accountId, sellerId);
        setFollowing(false);
      } else {
        await followSeller(accountId, sellerId);
        setFollowing(true);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Button variant={following ? "secondary" : "primary"} disabled={busy} onClick={toggle}>
      {following ? "팔로잉" : "팔로우"}
    </Button>
  );
}
