import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import leftButtonUrl from '@/assets/images/auth/left-btn.webp'
import signupCompleteButtonUrl from '@/assets/images/auth/btn-go-login.webp'
import signupCompleteFrameUrl from '@/assets/images/auth/signup-complete-frame.webp'
import signupCompleteStarUrl from '@/assets/images/auth/signup-complete-star.webp'
import signupBoardUrl from '@/assets/images/auth/signup-board-v2.webp'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { ParentNoticeDialog } from '@/components/ParentNoticeDialog'
import { BirthDatePicker } from '@/components/ui/BirthDatePicker'
import { useAdventureBgm } from '@/hooks/useAdventureBgm'
import { COMPLETE_BUTTON_CLIP } from '@/lib/buttonClip'
import { useFitScale } from '@/hooks/useFitScale'
import { EMAIL_PATTERN } from '@/utils/validation'
import {
  checkEmailAvailability,
  markNewSignup,
  signupWithEmail,
} from '@/api/auth'

const jua = { fontFamily: "'Jua', sans-serif" } as const
/*
  판 크기는 캔버스 px로 고정한다.

  이 화면은 useFitScale이 1280x720 캔버스를 통째로 확대·축소해 보여 준다. 그런데
  transform: scale()은 vw·vh가 가리키는 값을 바꾸지 않는다. 판 너비를 100vw로
  잡아 두면 창이 작아질 때 캔버스가 줄면서 한 번, 판이 제 너비를 줄이며 또 한 번
  — 두 번 줄어 배경에 비해 판만 작아진다.

  게다가 판 너비는 100vh에도 묶여 있었는데 안쪽 글자는 vw만 봤다. 가로가 넓고
  세로가 짧은 창에서는 판만 작아지고 글자는 그대로라 "중복확인"이 상자를 넘쳤다.

  캔버스가 이미 contain으로 모든 화면비를 감당하므로 그 안에서 뷰포트를 다시 잴
  이유가 없다. 값은 이전 clamp의 최대값 — 데스크톱에서 실제로 적용되던 크기다.
  로그인 화면(EmailLoginPage)도 같은 방식이다.
*/
const boardSize = {
  aspectRatio: '835 / 1064',
  width: 580,
} as const
const SIGNUP_BOARD_SCALE = 0.9
const PASSWORD_PATTERN =
  /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~])[A-Za-z\d!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~]{8,16}$/
const MIN_SIGNUP_AGE = 19

function latestAllowedBirthDate() {
  const today = new Date()
  return new Date(
    today.getFullYear() - MIN_SIGNUP_AGE,
    today.getMonth(),
    today.getDate(),
  )
}

function ageOf(birth: string) {
  if (!birth) return null
  const [year, month, day] = birth.split('-').map(Number)
  const birthDate = new Date(year, month - 1, day)
  const now = new Date()
  let age = now.getFullYear() - birthDate.getFullYear()
  const monthDiff = now.getMonth() - birthDate.getMonth()
  if (
    monthDiff < 0 ||
    (monthDiff === 0 && now.getDate() < birthDate.getDate())
  ) {
    age -= 1
  }
  return age
}

function passwordValidationMessage(password: string) {
  if (!password) return ''
  if (password.length < 8) return '비밀번호는 8자 이상 입력해 주세요.'
  if (password.length > 16) return '비밀번호는 16자 이하로 입력해 주세요.'
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
    return '영문, 숫자, 특수문자를 모두 포함해 주세요.'
  }
  if (!/[!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~]/.test(password)) {
    return '영문, 숫자, 특수문자를 모두 포함해 주세요.'
  }
  return '사용할 수 없는 문자가 포함되어 있어요.'
}

export function EmailSignupPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { isBgmPlaying, toggleBgm } = useAdventureBgm()

  const [email, setEmail] = useState('')
  const [emailChecked, setEmailChecked] = useState(false)
  const [emailAvailable, setEmailAvailable] = useState(false)
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false)
  const [birth, setBirth] = useState('')
  const [privacyAgreed, setPrivacyAgreed] = useState(false)
  const [isCheckingEmail, setIsCheckingEmail] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [requestError, setRequestError] = useState('')
  const [isSignupComplete, setIsSignupComplete] = useState(false)
  /*
    입력이 덜 된 것을 알리는 쪽지.

    브라우저 기본 alert을 쓰면 주소가 박힌 흰 상자가 떠서 화면 밖 물건처럼 보인다.
    다른 화면에서 쓰는 나무 팝업(ParentNoticeDialog)과 같은 것을 쓴다.
  */
  const [notice, setNotice] = useState('')
  const maxBirthDate = useMemo(latestAllowedBirthDate, [])

  const emailValid = EMAIL_PATTERN.test(email)
  const passwordValid = PASSWORD_PATTERN.test(password)
  const passwordConfirmed =
    passwordConfirm.length > 0 && passwordConfirm === password
  const age = useMemo(() => ageOf(birth), [birth])
  const ageValid = age !== null && age >= MIN_SIGNUP_AGE
  const canSubmit =
    emailChecked &&
    emailAvailable &&
    passwordValid &&
    passwordConfirmed &&
    ageValid

  const checkEmail = async () => {
    if (!emailValid) {
      setEmailChecked(false)
      setEmailAvailable(false)
      setNotice('올바른 이메일 형식을 입력해요.')
      return
    }

    setIsCheckingEmail(true)
    setRequestError('')
    try {
      const result = await checkEmailAvailability(email.trim())
      setEmailChecked(true)
      setEmailAvailable(result.available)
    } catch (error) {
      setEmailChecked(false)
      setEmailAvailable(false)
      setRequestError(
        error instanceof Error ? error.message : '이메일 확인에 실패했어요.',
      )
    } finally {
      setIsCheckingEmail(false)
    }
  }

  const submit = async () => {
    if (!emailChecked || !emailAvailable) {
      setNotice('이메일 중복 확인을 해주세요.')
      return
    }
    if (!passwordValid) {
      setNotice('비밀번호는 8자 이상이어야 해요.')
      return
    }
    if (!passwordConfirmed) {
      setNotice('비밀번호 확인이 일치하지 않아요.')
      return
    }
    if (!ageValid) {
      setNotice('만 19세 이상만 가입할 수 있어요.')
      return
    }
    if (!privacyAgreed) {
      setNotice('개인정보 수집 및 이용에 동의해 주세요.')
      return
    }
    setIsSubmitting(true)
    setRequestError('')
    try {
      const normalizedEmail = email.trim().toLowerCase()
      await signupWithEmail({
        birthDate: birth,
        email: normalizedEmail,
        password,
      })
      markNewSignup(normalizedEmail)
      setIsSignupComplete(true)
    } catch (error) {
      setRequestError(
        error instanceof Error ? error.message : '회원가입에 실패했어요.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const emailMessage = emailChecked
    ? emailAvailable
      ? '사용할 수 있는 이메일이에요!'
      : '이미 사용 중인 이메일이에요.'
    : ''
  const passwordMessage = password
    ? passwordValid
      ? '사용할 수 있는 비밀번호예요!'
      : passwordValidationMessage(password)
    : '영문·숫자·특수문자를 포함해 8~16자로 입력해 주세요.'
  const passwordConfirmMessage = passwordConfirm
    ? passwordConfirmed
      ? '비밀번호가 일치해요!'
      : '비밀번호가 일치하지 않아요.'
    : ''
  const birthMessage =
    birth && !ageValid ? '만 19세 이상만 가입할 수 있어요.' : ''
  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,#12241a_0%,#0b140e_100%)]">
      <div
        ref={canvasRef}
        className="relative flex h-[720px] w-[1280px] items-center justify-center overflow-hidden text-white"
      >
        <img
          alt=""
          className="absolute inset-0 z-0 h-full w-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="pointer-events-none absolute inset-0 z-10 bg-[linear-gradient(180deg,rgba(10,26,15,0.28)_0%,rgba(10,26,15,0.46)_100%)]" />

        <Link
          aria-label="뒤로"
          className="absolute top-[25px] left-[25px] z-30 block size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,0.28)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          to="/"
        >
          <img
            alt=""
            className="block size-full object-contain"
            decoding="async"
            draggable={false}
            src={leftButtonUrl}
          />
        </Link>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
          onClick={() => {
            void toggleBgm()
          }}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        <div
          className="relative z-20"
          style={{
            ...boardSize,
            transform: `translateY(-4px) scale(${SIGNUP_BOARD_SCALE})`,
            transformOrigin: 'center center',
          }}
        >
          <img
            alt=""
            className="pointer-events-none absolute inset-0 z-10 h-full w-full object-contain"
            decoding="async"
            src={signupBoardUrl}
          />

          <img
            alt="빵긋"
            className="pointer-events-none absolute top-[12%] left-1/2 z-20 w-[39%] -translate-x-1/2 drop-shadow-[0_5px_10px_rgba(0,0,0,0.18)]"
            decoding="async"
            src={logoUrl}
          />
          <span
            className="pointer-events-none absolute top-[22%] left-1/2 z-20 -translate-x-1/2 text-[24px] whitespace-nowrap text-[#5a7d3a]"
            style={jua}
          >
            이메일로 가입하기
          </span>
          <input
            className="auth-transparent-input absolute top-[29.7%] left-[17%] z-20 h-[8.5%] w-[46.8%] appearance-none border-none bg-transparent px-[3%] text-[23px] text-[#5a4a2a] placeholder:text-[#9db089] focus:bg-transparent focus:outline-none"
            onChange={(event) => {
              setEmail(event.target.value)
              setEmailChecked(false)
              setEmailAvailable(false)
            }}
            placeholder="이메일"
            style={jua}
            type="email"
            value={email}
          />
          <button
            className="absolute top-[29.7%] left-[65%] z-20 h-[8.5%] w-[19.4%] rounded-full border-none bg-transparent text-[25px] text-white"
            disabled={isCheckingEmail || isSubmitting}
            onClick={() => void checkEmail()}
            style={{
              ...jua,
              paintOrder: 'stroke fill',
              WebkitTextStroke: '2.5px #4a7d1e',
            }}
            type="button"
          >
            중복확인
          </button>
          {emailMessage ? (
            <p
              className="absolute top-[38.9%] left-[17%] z-20 w-[67.6%] px-1 text-[14px] leading-none"
              style={{ ...jua, color: emailAvailable ? '#3a7d2c' : '#c7563a' }}
            >
              {emailMessage}
            </p>
          ) : null}
          <input
            className="auth-transparent-input absolute top-[41.9%] left-[17%] z-20 h-[8.5%] w-[57.5%] appearance-none border-none bg-transparent px-[3%] text-[23px] text-[#5a4a2a] placeholder:text-[#9db089] focus:bg-transparent focus:outline-none"
            onChange={(event) => setPassword(event.target.value)}
            placeholder="비밀번호 (8~16자)"
            style={jua}
            type={showPassword ? 'text' : 'password'}
            value={password}
          />
          <button
            aria-label="비밀번호 보기"
            className="absolute top-[41.9%] right-[17%] z-20 h-[8.5%] w-[10%] border-none bg-none text-[23px] leading-none text-[#6f8a5e]"
            onClick={() => setShowPassword((value) => !value)}
            style={jua}
            type="button"
          >
            {showPassword ? '숨김' : '보기'}
          </button>
          {passwordMessage ? (
            <p
              className="absolute top-[51.2%] left-[17%] z-20 w-[67.6%] px-1 text-[14px] leading-none"
              style={{
                ...jua,
                color: !password
                  ? '#7d8e5f'
                  : passwordValid
                    ? '#3a7d2c'
                    : '#c7563a',
              }}
            >
              {passwordMessage}
            </p>
          ) : null}
          <input
            className="auth-transparent-input absolute top-[54%] left-[16.3%] z-20 h-[8.5%] w-[57.5%] appearance-none border-none bg-transparent px-[3%] text-[23px] text-[#5a4a2a] placeholder:text-[#9db089] focus:bg-transparent focus:outline-none"
            onChange={(event) => setPasswordConfirm(event.target.value)}
            placeholder="비밀번호 확인"
            style={jua}
            type={showPasswordConfirm ? 'text' : 'password'}
            value={passwordConfirm}
          />
          <button
            aria-label="비밀번호 확인 보기"
            className="absolute top-[54.3%] right-[17%] z-20 h-[8.5%] w-[10%] border-none bg-none text-[23px] leading-none text-[#6f8a5e]"
            onClick={() => setShowPasswordConfirm((value) => !value)}
            style={jua}
            type="button"
          >
            {showPasswordConfirm ? '숨김' : '보기'}
          </button>
          {passwordConfirmMessage ? (
            <p
              className="absolute top-[63.3%] left-[17%] z-20 w-[67.6%] px-1 text-[14px] leading-none"
              style={{
                ...jua,
                color: passwordConfirmed ? '#3a7d2c' : '#c7563a',
              }}
            >
              {passwordConfirmMessage}
            </p>
          ) : null}

          <div
            className="absolute top-[66.2%] left-[16.8%] z-20 h-[8.6%] w-[68%] overflow-hidden rounded-full pr-2"
            style={jua}
          >
            <BirthDatePicker
              maxDate={maxBirthDate}
              onChange={setBirth}
              value={birth}
            />
          </div>
          {birthMessage ? (
            <p
              className="absolute top-[74.6%] left-[16.2%] z-20 w-[67.6%] px-1 text-[11px] leading-none text-[#c7563a]"
              style={jua}
            >
              {birthMessage}
            </p>
          ) : null}

          {/*
            개인정보 동의.

            체크하지 않으면 가입되지 않는다. 다만 버튼을 흐리게 막지는 않는다 —
            무엇이 모자란지 알 수 없으므로, 눌렀을 때 왜 안 되는지 알려 준다.
          */}
          <label
            className="absolute right-[14%] bottom-[8%] left-[14%] z-20 flex cursor-pointer items-center justify-center gap-2 text-[17px] leading-none text-[#7a693e]"
            style={jua}
          >
            <input
              checked={privacyAgreed}
              className="size-[17px] shrink-0 accent-[#3a7d2c]"
              onChange={(event) => setPrivacyAgreed(event.target.checked)}
              type="checkbox"
            />
            개인정보 수집 및 이용에 동의합니다 (필수)
          </label>

          <button
            className="absolute top-[77.8%] left-[17.1%] z-20 h-[10.9%] w-[64.3%] rounded-full border-none bg-transparent text-[35px] text-white transition active:translate-y-0.5"
            disabled={!canSubmit || isSubmitting}
            onClick={() => void submit()}
            style={{
              ...jua,
              cursor: canSubmit ? 'pointer' : 'not-allowed',
              opacity: canSubmit ? 1 : 0.55,
              paintOrder: 'stroke fill',
              WebkitTextStroke: '4px #4a7d1e',
            }}
            type="button"
          >
            가입하기
          </button>
          {requestError ? (
            <p
              className="absolute top-[89%] left-[16%] z-20 w-[68%] text-center text-[13px] text-[#c7563a]"
              style={jua}
            >
              {requestError}
            </p>
          ) : null}
        </div>
        {isSignupComplete ? (
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(10,26,15,.55)]">
            <section className="relative aspect-square w-[460px]">
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full object-contain"
                src={signupCompleteFrameUrl}
              />
              <img
                alt=""
                className="pointer-events-none absolute top-[10%] left-[238px] w-[25%] -translate-x-1/2 object-contain"
                src={signupCompleteStarUrl}
              />
              <h2
                className="absolute top-[35%] right-0 left-0 text-center text-[28px] text-[#5a7d3a]"
                style={jua}
              >
                회원가입 완료!
              </h2>
              <p
                className="absolute top-[47%] right-[14%] left-[14%] text-center text-[17px] leading-relaxed text-[#6f8a5e]"
                style={jua}
              >
                회원가입이 완료되었어요!
                <br />
                아래 버튼을 눌러 로그인해 주세요.
              </p>
              <button
                className="absolute top-[69%] left-1/2 flex h-[20%] w-[62%] -translate-x-1/2 items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-white [-webkit-text-stroke:3px_#4a7d1e] [paint-order:stroke_fill]"
                onClick={() =>
                  navigate('/auth/login', {
                    replace: true,
                    state: { email: email.trim().toLowerCase() },
                  })
                }
                style={{
                  ...jua,
                  backgroundImage: `url(${signupCompleteButtonUrl})`,
                  clipPath: COMPLETE_BUTTON_CLIP,
                }}
                type="button"
              >
                로그인하기
              </button>
            </section>
          </div>
        ) : null}
        {notice ? (
          <ParentNoticeDialog
            confirmLabel="확인"
            onConfirm={() => setNotice('')}
            title={notice}
          />
        ) : null}
      </div>
    </div>
  )
}
