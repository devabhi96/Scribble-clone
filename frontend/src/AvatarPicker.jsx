import { useState } from 'react'

const SKIN_TONES = ['#f2c49b', '#d9a066', '#a8683f', '#5c3a21']
const HAIR_COLORS = ['#3a2b1e', '#8a4b2b', '#d4b62c', '#e35b5b', '#5b5be3']

const HAIR_STYLES = [
  // spiky
  (color) => (
    <path d="M20 40 L28 20 L34 34 L42 16 L48 34 L56 20 L64 40 Z" fill={color} />
  ),
  // curly
  (color) => (
    <>
      <circle cx="26" cy="30" r="9" fill={color} />
      <circle cx="38" cy="22" r="10" fill={color} />
      <circle cx="52" cy="24" r="9" fill={color} />
      <circle cx="60" cy="34" r="8" fill={color} />
    </>
  ),
  // bald
  () => null,
  // bun
  (color) => (
    <>
      <path d="M20 38 Q42 10 64 38 L64 30 Q42 20 20 30 Z" fill={color} />
      <circle cx="42" cy="12" r="8" fill={color} />
    </>
  ),
]

const MOUTHS = [
  <path key="smile" d="M32 58 Q42 68 52 58" stroke="#3a2b1e" strokeWidth="3" fill="none" strokeLinecap="round" />,
  <path key="grin" d="M30 56 Q42 70 54 56 Q42 62 30 56" fill="#3a2b1e" />,
  <line key="flat" x1="33" y1="60" x2="51" y2="60" stroke="#3a2b1e" strokeWidth="3" strokeLinecap="round" />,
]

// A small, fixed set of preset combinations so cycling feels intentional, not random-noisy
export const AVATAR_PRESETS = Array.from({ length: 8 }, (_, i) => ({
  skin: SKIN_TONES[i % SKIN_TONES.length],
  hairColor: HAIR_COLORS[(i * 2) % HAIR_COLORS.length],
  hairStyle: i % HAIR_STYLES.length,
  mouth: i % MOUTHS.length,
}))

function AvatarFace({ preset, size = 72 }) {
  const { skin, hairColor, hairStyle, mouth } = preset
  return (
    <svg
      className="avatar-face"
      width={size}
      height={size}
      viewBox="0 0 84 84"
    >
      <circle cx="42" cy="44" r="26" fill={skin} />
      {HAIR_STYLES[hairStyle](hairColor)}
      <ellipse className="avatar-eye left" cx="34" cy="42" rx="3" ry="4" fill="#3a2b1e" />
      <ellipse className="avatar-eye right" cx="50" cy="42" rx="3" ry="4" fill="#3a2b1e" />
      {MOUTHS[mouth]}
    </svg>
  )
}

export default function AvatarPicker({ index, onChange }) {
  const [spinning, setSpinning] = useState(false)

  const cycle = (delta) => {
    const next = (index + delta + AVATAR_PRESETS.length) % AVATAR_PRESETS.length
    onChange(next)
  }

  const shuffle = () => {
    setSpinning(true)
    const random = Math.floor(Math.random() * AVATAR_PRESETS.length)
    onChange(random)
    setTimeout(() => setSpinning(false), 200)
  }

  return (
    <div className="avatar-picker">
      <button type="button" className="avatar-arrow" onClick={() => cycle(-1)} aria-label="Previous avatar">
        ‹
      </button>

      <div className="avatar-frame">
        <AvatarFace preset={AVATAR_PRESETS[index]} />
        <button
          type="button"
          className="avatar-dice"
          onClick={shuffle}
          aria-label="Random avatar"
          style={{ transform: spinning ? 'rotate(180deg) scale(1.1)' : undefined }}
        >
          🎲
        </button>
      </div>

      <button type="button" className="avatar-arrow" onClick={() => cycle(1)} aria-label="Next avatar">
        ›
      </button>
    </div>
  )
}

export { AvatarFace }