import { NicknameProfileManager } from "@/features/profile/components/NicknameProfileManager";

/**
 * 설정 화면용 닉네임 프로필 관리 카드.
 *
 * profile 기능의 NicknameProfileManager 를 설정 페이지에 그대로 노출한다(중복 구현 방지).
 * 설정 페이지가 이 컴포넌트를 섹션으로 끼워 넣는다.
 */
export function NicknameProfileSettingsCard() {
  return (
    <section className="space-y-3">
      <NicknameProfileManager />
    </section>
  );
}
