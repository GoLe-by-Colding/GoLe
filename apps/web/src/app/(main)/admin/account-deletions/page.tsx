import type { Metadata } from "next";
import { AdminAccountDeletionsView } from "@views/admin";

export const metadata: Metadata = { title: "회원 탈퇴 검토" };

export default function Page() {
  return <AdminAccountDeletionsView />;
}
