import DrawingCanvas from "./DrawingCanvas.jsx";
import AvatarPicker from "./AvatarPicker.jsx";
import "./Landing.css";
import "./Game.css";

import { useEffect, useState, useRef } from "react";
import { Client } from '@stomp/stompjs'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';


const STATE_LABELS = {
  WAITING: 'Waiting',
  CHOOSING_WORD: 'Choosing Word',
  DRAWING: 'Drawing',
  ROUND_END: 'Round Over',
  GAME_OVER: 'Game Over'
}

const ROUND_OPTIONS = [1, 2, 3, 5, 8, 10]

function getOrCreatePlayerId() {
  const key = 'scribble-playerId'
  let id = sessionStorage.getItem(key)
  if (!id) {
    id = crypto.randomUUID()
    sessionStorage.setItem(key, id)
  }
  return id
}

function App() {
  const [roomCode, setRoomCode] = useState(null)
  const [playerName, setPlayerName] = useState('')
  const [joinCodeInput, setJoinCodeInput] = useState('')
  const [avatarIndex, setAvatarIndex] = useState(0)
  const [players, setPlayers] = useState([])
  const [hostPlayerId, setHostPlayerId] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [stompClient, setStompClient] = useState(null)
  const [connected, setConnected] = useState(false)
  const [playerId] = useState(getOrCreatePlayerId)
  const [chatLog, setChatLog] = useState([])
  const [guessInput, setGuessInput] = useState('')

  const [gameState, setGameState] = useState('WAITING')
  const [maskedWord, setMaskedWord] = useState('')
  const [actualWord, setActualWord] = useState(null)
  const [timeRemaining, setTimeRemaining] = useState(0)
  const [currentDrawerId, setCurrentDrawerId] = useState(null)
  const [wordChoices, setWordChoices] = useState([])
  const [currentRound, setCurrentRound] = useState(0)
  const [totalRounds, setTotalRounds] = useState(3)
  const [infiniteRounds, setInfiniteRounds] = useState(false)
  const [revealedWord, setRevealedWord] = useState(null)

  const canvasRef = useRef(null)
  const prevStateRef = useRef(null)
  const chatLogRef = useRef(null)

  const isDrawer = currentDrawerId === playerId
  const isHost = hostPlayerId === playerId
  const settingsLocked = gameState === 'DRAWING' || gameState === 'CHOOSING_WORD' || gameState === 'ROUND_END'

  useEffect(() => {
    if (!roomCode) return

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { playerId },
      onConnect: () => {
        setConnected(true)

        client.subscribe(`/topic/room/${roomCode}/players`, (message) => {
          const data = JSON.parse(message.body)
          setPlayers(data.players || [])
          setHostPlayerId(data.hostPlayerId || null)
        })

        client.subscribe(`/topic/room/${roomCode}/state`, (message) => {
          applyGameState(JSON.parse(message.body))
        })

        client.subscribe(`/user/queue/state-sync`, (message) => {
          applyGameState(JSON.parse(message.body))
        })

        client.subscribe(`/user/queue/word-choices`, (message) => {
          const data = JSON.parse(message.body)
          setWordChoices(data.options || [])
        })

        client.subscribe(`/user/queue/current-word`, (message) => {
          const data = JSON.parse(message.body)
          setActualWord(data.word || null)
        })

        client.subscribe(`/user/queue/sync`, (message) => {
          const data = JSON.parse(message.body)
          canvasRef.current?.loadStrokeHistory(data.strokes || [])
        })

        client.subscribe(`/topic/room/${roomCode}/chat`, (message) => {
          const data = JSON.parse(message.body)
          setChatLog((prev) => [...prev, data])
        })

        client.subscribe(`/topic/room/${roomCode}/draw`, (message) => {
          const data = JSON.parse(message.body)
          canvasRef.current?.drawRemoteBatch(data)
        })

        client.publish({
          destination: `/app/room/${roomCode}/join`,
          body: JSON.stringify({ roomCode, playerName: playerName.trim(), playerId })
        })
      },
      onStompError: (frame) => console.error('STOMP error', frame)
    })

    client.activate()
    setStompClient(client)

    return () => {
      client.deactivate()
      setConnected(false)
    }
  }, [roomCode])

  useEffect(() => {
    if (chatLogRef.current) {
      chatLogRef.current.scrollTop = chatLogRef.current.scrollHeight
    }
  }, [chatLog])

  const applyGameState = (data) => {
    setGameState(data.state)
    setMaskedWord(data.maskedWord || '')
    setTimeRemaining(data.timeRemainingSeconds || 0)
    setCurrentDrawerId(data.currentDrawerId || null)
    setCurrentRound(data.currentRound || 0)
    setTotalRounds(data.totalRounds || 3)
    setInfiniteRounds(!!data.infiniteRounds)
    setRevealedWord(data.revealedWord || null)
    if (data.state !== 'CHOOSING_WORD') setWordChoices([])

  
    if (data.state === 'CHOOSING_WORD' && prevStateRef.current !== 'CHOOSING_WORD') {
      canvasRef.current?.resetCanvas()
      setActualWord(null)
    }
    prevStateRef.current = data.state
  }

  const handleCreateRoom = () => {
    if (!playerName.trim()) { setError('Enter your name first'); return }
    setLoading(true); setError(null)

    fetch(`${API_BASE}/api/rooms`, { method: 'POST' })
      .then((res) => res.json())
      .then((data) => setRoomCode(data.roomCode))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }

  const handleJoinRoom = () => {
    if (!playerName.trim()) { setError('Enter your name first'); return }
    if (!joinCodeInput.trim()) { setError('Enter a room code'); return }
    setRoomCode(joinCodeInput.trim().toUpperCase())
  }

  const handleSubmitGuess = () => {
    if (!guessInput.trim()) return
    stompClient.publish({
      destination: `/app/room/${roomCode}/guess`,
      body: JSON.stringify({ playerId, text: guessInput.trim() })
    })
    setGuessInput('')
  }

  const handleStartGame = () => {
    if (!isHost) return
    stompClient.publish({
      destination: `/app/room/${roomCode}/start`,
      body: JSON.stringify({ playerId })
    })
  }

  const handleChooseWord = (word) => {
    stompClient.publish({
      destination: `/app/room/${roomCode}/choose-word`,
      body: JSON.stringify({ playerId, chosenWord: word })
    })
    setWordChoices([])
  }

  const publishSettings = (nextTotalRounds, nextInfinite) => {
    if (!isHost || !stompClient) return
    stompClient.publish({
      destination: `/app/room/${roomCode}/settings`,
      body: JSON.stringify({ playerId, totalRounds: nextTotalRounds, infiniteRounds: nextInfinite })
    })
  }

  const handleRoundsChange = (e) => {
    const val = e.target.value
    if (val === 'infinite') {
      publishSettings(totalRounds, true)
    } else {
      publishSettings(Number(val), false)
    }
  }

  const getStatusMessage = () => {
    switch (gameState) {
      case 'WAITING':
        return { text: 'Game has not started yet — click Start Game when everyone has joined.', color: 'var(--chalk-white-dim)' }
      case 'CHOOSING_WORD':
        return isDrawer
          ? { text: "It's your turn — pick a word above!", color: '#7cd68c' }
          : { text: `${players.find(p => p.id === currentDrawerId)?.name || 'A player'} is choosing a word...`, color: 'var(--chalk-white-dim)' }
      case 'DRAWING':
        return isDrawer
          ? { text: "It's your turn to draw!", color: '#7cd68c' }
          : { text: `${players.find(p => p.id === currentDrawerId)?.name || 'Someone'} is drawing...`, color: 'var(--chalk-white)' }
      case 'ROUND_END':
        return { text: 'Round over! Next turn starting soon...', color: 'var(--chalk-white-dim)' }
      case 'GAME_OVER':
        return { text: 'Game over! Check the final scores below.', color: 'var(--marker-yellow)' }
      default:
        return { text: '', color: 'var(--chalk-white)' }
    }
  }

  const status = getStatusMessage()
  const sortedPlayers = [...players].sort((a, b) => b.score - a.score)
  const roundLabel = infiniteRounds
    ? `${currentRound + 1}`
    : `${Math.min(currentRound + 1, totalRounds)}/${totalRounds}`

  if (!roomCode) {
    return (
      <div className="landing">
        <div className="landing-doodles">
          <svg className="doodle-1" viewBox="0 0 60 60"><path d="M5 30 Q15 5 30 30 T55 30" /></svg>
          <svg className="doodle-2" viewBox="0 0 60 60"><circle cx="30" cy="30" r="20" /></svg>
          <svg className="doodle-3" viewBox="0 0 60 60"><path d="M10 50 L30 10 L50 50 Z" /></svg>
          <svg className="doodle-4" viewBox="0 0 60 60"><path d="M5 15 L55 15 M5 30 L55 30 M5 45 L30 45" /></svg>
        </div>

        <div className="landing-card">
          <svg className="landing-title-svg" viewBox="0 0 320 90">
            <path
              className="landing-title-path"
              d="M12 60 Q8 30 25 25 Q45 20 40 45 Q35 65 55 60 Q70 55 65 35
                 M85 55 L85 25 M85 25 Q100 22 100 35 Q100 48 85 45 Q102 45 102 58
                 M120 25 L120 60 M120 25 L145 25 M120 42 L140 42
                 M165 25 Q155 25 155 42 Q155 60 175 60 Q188 60 188 48
                 M205 60 L205 25 L228 60 L228 25
                 M250 25 Q265 25 265 37 Q265 45 250 45 L250 60 M250 45 L268 60
                 M285 60 L285 25 L308 25 M285 42 L302 42 M285 60 L308 60"
            />
            <path
              className="landing-underline"
              d="M15 78 Q100 68 160 78 T310 76"
            />
          </svg>

          <p className="landing-subtitle">Draw. Guess. Repeat.</p>

          <AvatarPicker index={avatarIndex} onChange={setAvatarIndex} />

          <input
            type="text"
            className="landing-input"
            placeholder="Your name"
            value={playerName}
            onChange={(e) => setPlayerName(e.target.value)}
            maxLength={20}
          />

          <button className="marker-btn" onClick={handleCreateRoom} disabled={loading}>
            {loading ? 'Working...' : 'Create Room'}
          </button>

          <div className="landing-divider">or join a friend's room</div>

          <div className="join-row">
            <input
              type="text"
              className="landing-input"
              placeholder="Room code"
              value={joinCodeInput}
              onChange={(e) => setJoinCodeInput(e.target.value)}
              maxLength={6}
            />
            <button className="marker-btn secondary" onClick={handleJoinRoom} disabled={loading}>
              Join
            </button>
          </div>

          {error && <p className="landing-error">{error}</p>}
        </div>
      </div>
    )
  }

  return (
    <div className="game-page">
      <div className="game-topbar">
        <div className="room-code-block">
          <h1>Room: {roomCode}</h1>
          <p className="share-hint">Share this code so friends can join!</p>
        </div>

        <button
          className="marker-btn"
          style={{ width: 'auto', padding: '0.55rem 1.1rem' }}
          onClick={handleStartGame}
          disabled={!isHost}
        >
          {gameState === 'GAME_OVER' ? 'Play Again' : 'Start Game'}
        </button>
        {!isHost && <span className="host-hint">(Only the host can start)</span>}

        <label className="rounds-setting">
          Rounds:
          <select
            value={infiniteRounds ? 'infinite' : totalRounds}
            disabled={!isHost || settingsLocked}
            onChange={handleRoundsChange}
          >
            {ROUND_OPTIONS.map((n) => (
              <option key={n} value={n}>{n}</option>
            ))}
            <option value="infinite">Infinite</option>
          </select>
        </label>

        <div className="game-meta">
          <span>State: <strong>{STATE_LABELS[gameState] || gameState}</strong></span>
          <span>Round: <strong>{roundLabel}{infiniteRounds ? ' (∞)' : ''}</strong></span>
          <span>Time: <strong>{timeRemaining}s</strong></span>
          {!(isDrawer && actualWord) && (
            <span>Word: <strong style={{ letterSpacing: '3px' }}>{maskedWord}</strong></span>
          )}
        </div>
      </div>

      <div className="game-body">
        <div className="panel players-panel">
          <h3>Players ({players.length})</h3>
          {sortedPlayers.map((p) => (
            <div key={p.id} className={`player-row ${p.isDrawing ? 'drawing' : ''}`}>
              <span className="name">
                {p.name}
                {p.id === hostPlayerId && ' 👑'}
                {p.isDrawing && ' ✏️'}
                {p.hasGuessedCorrectly && ' ✅'}
              </span>
              <span>{p.score}</span>
            </div>
          ))}
        </div>

        <div className="panel canvas-panel">
          {gameState === 'ROUND_END' && revealedWord && (
            <div className="round-end-banner">
              The word was: <strong>{revealedWord}</strong>
            </div>
          )}

          {gameState === 'GAME_OVER' && (
            <div className="gameover-panel">
              <h3>🏆 Final Scores</h3>
              <ol>
                {sortedPlayers.map((p) => (
                  <li key={p.id}>{p.name} — {p.score} pts</li>
                ))}
              </ol>
            </div>
          )}

          {isDrawer && gameState === 'CHOOSING_WORD' && wordChoices.length > 0 && (
            <div className="word-choices-banner">
              <strong>Choose a word to draw:</strong>
              <div>
                {wordChoices.map((w) => (
                  <button
                    key={w}
                    className="marker-btn word-choice-btn"
                    onClick={() => handleChooseWord(w)}
                  >
                    {w}
                  </button>
                ))}
              </div>
              <p className="choose-hint">Auto-picks a word in {timeRemaining}s if you don't choose.</p>
            </div>
          )}

          {isDrawer && gameState === 'DRAWING' && actualWord && (
            <div>
              <p className="drawer-word-line">Your word: {actualWord}</p>
              <p className="drawer-word-note">Only you can see this — everyone else sees the blanks above.</p>
            </div>
          )}

          <p className="status-line" style={{ color: status.color }}>{status.text}</p>

          <div className="canvas-wrap">
            <DrawingCanvas
              ref={canvasRef}
              stompClient={stompClient}
              roomCode={roomCode}
              playerId={playerId}
              connected={connected}
              canDraw={isDrawer && gameState === 'DRAWING'}
            />
          </div>
        </div>

        <div className="panel chat-panel">
          <h3>Chat / Guesses</h3>
          <div className="chat-log" ref={chatLogRef}>
            {chatLog.map((entry, i) => (
              <div key={i} className={`chat-entry ${entry.wasCorrectGuess ? 'correct' : ''}`}>
                <span className="author">{entry.playerName}:</span> {entry.message}
              </div>
            ))}
          </div>
          <div className="guess-row">
            <input
              type="text"
              className="landing-input"
              placeholder="Type your guess"
              value={guessInput}
              onChange={(e) => setGuessInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') handleSubmitGuess() }}
              disabled={isDrawer}
            />
            <button className="marker-btn secondary" onClick={handleSubmitGuess} disabled={isDrawer}>
              Guess
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default App;