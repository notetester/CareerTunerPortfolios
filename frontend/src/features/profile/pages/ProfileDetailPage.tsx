import { useState } from "react";
import { FileText, IdCard, UserCircle2 } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/app/components/ui/tabs";
import { AccountInfoCard } from "../components/AccountInfoCard";
import { NicknameProfileManager } from "../components/NicknameProfileManager";
import { PhoneVerificationCard } from "../components/PhoneVerificationCard";
import { ResumeDetailForm } from "../components/ResumeDetailForm";

/**
 * 프로필 상세 페이지 — W6.
 *
 * 계정 정보(로그인 아이디/전화번호/연결 계정), 닉네임 프로필 관리, 이력서 상세 스펙을
 * 한 화면 3개 탭으로 묶는다. 기존 /profile(app/pages/Profile.tsx, 다른 소유)과 별개 라우트.
 */
export function ProfileDetailPage() {
  const [tab, setTab] = useState("account");

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1100px] space-y-6 px-4 py-8 sm:px-6">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-950">
            <IdCard className="size-6 text-blue-600" />
            내 정보 관리
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            계정 정보, 커뮤니티/채팅용 닉네임 프로필, 이력서 상세 스펙을 관리합니다.
          </p>
        </div>

        <Tabs value={tab} onValueChange={setTab}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
            <TabsTrigger value="account">
              <IdCard className="mr-1.5 size-4" />
              계정
            </TabsTrigger>
            <TabsTrigger value="nicknames">
              <UserCircle2 className="mr-1.5 size-4" />
              닉네임 프로필
            </TabsTrigger>
            <TabsTrigger value="resume">
              <FileText className="mr-1.5 size-4" />
              이력서 상세
            </TabsTrigger>
          </TabsList>

          <TabsContent value="account" className="mt-5 space-y-5">
            <AccountInfoCard />
            <PhoneVerificationCard />
          </TabsContent>
          <TabsContent value="nicknames" className="mt-5">
            <NicknameProfileManager />
          </TabsContent>
          <TabsContent value="resume" className="mt-5">
            <ResumeDetailForm />
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}

export default ProfileDetailPage;
