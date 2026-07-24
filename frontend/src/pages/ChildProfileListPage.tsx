import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import completeButtonUrl from '@/assets/images/parent-profile/btn-complete.webp'
import deleteButtonUrl from '@/assets/images/parent-profile/btn-delete.webp'
import editButtonUrl from '@/assets/images/parent-profile/btn-edit.webp'
import frameUrl from '@/assets/images/parent-profile/frame-square.webp'
import kidCellUrl from '@/assets/images/parent-profile/kid-cell.webp'
import pagerLeftUrl from '@/assets/images/parent-profile/pager-left.webp'
import pagerPageUrl from '@/assets/images/parent-profile/pager-page.webp'
import pagerRightUrl from '@/assets/images/parent-profile/pager-right.webp'
import photoButtonUrl from '@/assets/images/parent-profile/btn-photo.webp'
import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import backgroundUrl from '@/assets/images/profile/background.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'
import {
  loadChildProfiles,
  saveChildProfiles,
  type ChildProfile,
} from '@/lib/childProfiles'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const PAGE_SIZE = 3

export function ChildProfileListPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const [profiles, setProfiles] = useState(loadChildProfiles)
  const [page, setPage] = useState(0)
  const [editingProfile, setEditingProfile] = useState<ChildProfile | null>(
    null,
  )
  const [editingName, setEditingName] = useState('')
  const pageCount = Math.max(1, Math.ceil(profiles.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount - 1)
  const visibleProfiles = useMemo(
    () =>
      profiles.slice(
        currentPage * PAGE_SIZE,
        currentPage * PAGE_SIZE + PAGE_SIZE,
      ),
    [currentPage, profiles],
  )

  const updateProfiles = (nextProfiles: ChildProfile[]) => {
    setProfiles(nextProfiles)
    saveChildProfiles(nextProfiles)
  }

  const removeProfile = (profile: ChildProfile) => {
    if (!window.confirm(`${profile.name} 프로필을 삭제할까요?`)) return
    updateProfiles(profiles.filter(({ id }) => id !== profile.id))
  }

  const openEdit = (profile: ChildProfile) => {
    setEditingProfile(profile)
    setEditingName(profile.name)
  }

  const saveEdit = () => {
    const nextName = editingName.trim()
    if (!editingProfile || !nextName) return
    updateProfiles(
      profiles.map((profile) =>
        profile.id === editingProfile.id
          ? { ...profile, name: nextName }
          : profile,
      ),
    )
    setEditingProfile(null)
  }

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[#0e1a12]">
      <div
        className="relative h-[720px] w-[1280px] origin-top overflow-hidden"
        ref={canvasRef}
      >
        <audio
          aria-label="초록 모험 시작 배경 음악"
          autoPlay
          loop
          preload="auto"
          ref={audioRef}
          src={bgmUrl}
        />
        <img
          alt=""
          className="absolute inset-0 size-full object-cover"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="absolute inset-0 bg-[rgba(10,26,15,.3)]" />

        <Link
          aria-label="뒤로"
          className="absolute top-4 left-[78px] z-30 size-[58px] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
          to="/profiles/new"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={backButtonUrl}
          />
        </Link>
        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-3 left-[78px] z-40 size-[62px] bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.3)] transition hover:scale-105"
          onClick={() => void toggleBgm()}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        <main className="absolute top-6 left-1/2 z-10 h-[672px] w-[640px] -translate-x-1/2">
          <img
            alt=""
            className="pointer-events-none absolute inset-0 size-full"
            src={frameUrl}
          />
          <h1
            className="absolute top-[11%] right-0 left-0 text-center text-[32px] text-[#3a7d2c]"
            style={jua}
          >
            아이 프로필 목록
          </h1>

          <div className="absolute top-[18%] right-[12%] left-[12%] flex flex-col">
            {visibleProfiles.length ? (
              visibleProfiles.map((profile) => (
                <article
                  className="relative -mb-3.5 flex h-[150px] flex-none items-center"
                  key={profile.id}
                >
                  <img
                    alt=""
                    className="pointer-events-none absolute inset-0 size-full"
                    src={kidCellUrl}
                  />
                  <button
                    className="relative z-10 flex min-w-0 flex-1 items-center bg-transparent text-left"
                    onClick={() => navigate('/child')}
                    type="button"
                  >
                    <img
                      alt=""
                      className="ml-[54px] size-[92px] rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_3px_#d9c79a]"
                      src={profile.photoUrl ?? childAvatarUrl}
                    />
                    <span
                      className="ml-[22px] truncate text-[33px] text-[#5a4a2a]"
                      style={jua}
                    >
                      {profile.name}
                    </span>
                  </button>
                  <div className="relative z-10 mr-[22px] flex flex-col">
                    <button
                      className="h-[72px] w-[184px] bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#7a5a1e] transition hover:scale-105"
                      onClick={() => openEdit(profile)}
                      style={{
                        ...jua,
                        backgroundImage: `url(${editButtonUrl})`,
                      }}
                      type="button"
                    >
                      수정
                    </button>
                    <button
                      className="-mt-[23px] h-[72px] w-[184px] bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#9a4a3e] transition hover:scale-105"
                      onClick={() => removeProfile(profile)}
                      style={{
                        ...jua,
                        backgroundImage: `url(${deleteButtonUrl})`,
                      }}
                      type="button"
                    >
                      삭제
                    </button>
                  </div>
                </article>
              ))
            ) : (
              <div
                className="mt-24 text-center text-[24px] text-[#8a7a55]"
                style={jua}
              >
                아직 만든 아이 프로필이 없어요.
              </div>
            )}
          </div>

          <Link
            className="absolute bottom-[16%] left-1/2 flex h-14 w-60 -translate-x-1/2 items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[20px] text-white active:translate-y-0.5"
            style={{
              ...jua,
              backgroundImage: `url(${completeButtonUrl})`,
              paintOrder: 'stroke fill',
              WebkitTextStroke: '3px #4a7d1e',
            }}
            to="/profiles/new?add=1"
          >
            + 새 프로필 생성
          </Link>

          <div className="absolute right-0 bottom-[6%] left-0 flex items-center justify-center gap-1.5">
            <button
              aria-label="이전"
              className="size-[54px] bg-transparent p-0 transition hover:scale-105 disabled:opacity-40"
              disabled={currentPage === 0}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
              type="button"
            >
              <img
                alt=""
                className="size-full object-contain"
                src={pagerLeftUrl}
              />
            </button>
            {Array.from({ length: pageCount }).map((_, index) => (
              <button
                className="relative size-[54px] bg-transparent p-0 transition hover:scale-105"
                key={index}
                onClick={() => setPage(index)}
                style={{ opacity: index === currentPage ? 1 : 0.5 }}
                type="button"
              >
                <img
                  alt=""
                  className="absolute inset-0 size-full object-contain"
                  src={pagerPageUrl}
                />
                <span
                  className="absolute inset-0 flex items-center justify-center text-[24px] text-white"
                  style={{
                    ...jua,
                    paintOrder: 'stroke fill',
                    WebkitTextStroke: '2px #3f7d1e',
                  }}
                >
                  {index + 1}
                </span>
              </button>
            ))}
            <button
              aria-label="다음"
              className="size-[54px] bg-transparent p-0 transition hover:scale-105 disabled:opacity-40"
              disabled={currentPage >= pageCount - 1}
              onClick={() =>
                setPage((value) => Math.min(pageCount - 1, value + 1))
              }
              type="button"
            >
              <img
                alt=""
                className="size-full object-contain"
                src={pagerRightUrl}
              />
            </button>
          </div>
        </main>

        {editingProfile ? (
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(10,26,15,.55)]">
            <div className="relative h-[280px] w-[430px] rounded-[32px] bg-[#fff7df] p-10 shadow-2xl">
              <h2
                className="text-center text-[28px] text-[#3a7d2c]"
                style={jua}
              >
                아이 이름 수정
              </h2>
              <input
                autoFocus
                className="mt-6 h-14 w-full rounded-full border-2 border-[#d9c79a] bg-white px-6 text-center text-[22px] text-[#5a4a2a] outline-none"
                maxLength={12}
                onChange={(event) => setEditingName(event.target.value)}
                style={jua}
                value={editingName}
              />
              <div className="mt-5 flex justify-center gap-3">
                <button
                  className="h-14 w-40 bg-[length:100%_100%] bg-center bg-no-repeat text-[18px] text-white"
                  onClick={saveEdit}
                  style={{
                    ...jua,
                    backgroundImage: `url(${completeButtonUrl})`,
                  }}
                  type="button"
                >
                  저장
                </button>
                <button
                  className="h-14 w-40 bg-[length:100%_100%] bg-center bg-no-repeat text-[18px] text-[#5a4a2a]"
                  onClick={() => setEditingProfile(null)}
                  style={{
                    ...jua,
                    backgroundImage: `url(${photoButtonUrl})`,
                  }}
                  type="button"
                >
                  취소
                </button>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}
