import { useState } from 'react'
import { AVATAR_PRESETS, HAIR_STYLES, MOUTHS } from './avatarPresets.jsx'

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