const SKIN_TONES = ['#f2c49b', '#d9a066', '#a8683f', '#5c3a21']
const HAIR_COLORS = ['#3a2b1e', '#8a4b2b', '#d4b62c', '#e35b5b', '#5b5be3']

export const HAIR_STYLES = [
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

export const MOUTHS = [
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