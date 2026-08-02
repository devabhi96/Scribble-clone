import DrawingCanvas from "./DrawingCanvas.jsx";

import { useEffect, useState, useRef } from "react";
import { Client } from '@stomp/stompjs'

const API_BASE = 'http://localhost:8080'
const WS_URL = 'ws://localhost:8080/ws'

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
  const [players, setPlayers] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [stompClient, setStompClient] = useState(null)
  const [connected, setConnected] = useState(false)
  const [playerId] = useState(getOrCreatePlayerId)
  const [chatLog, setChatLog] = useState([])
  const [guessInput, setGuessInput] = useState('')

  const [gameState, setGameState] = useState('WAITING')
  const [maskedWord, setMaskedWord] = useState('')
  const [timeRemaining, setTimeRemaining] = useState(0)
  const [currentDrawerId, setCurrentDrawerId] = useState(null)
  const [wordChoices, setWordChoices] = useState([])

  const canvasRef = useRef(null)

  const isDrawer = currentDrawerId === playerId

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

  const prevStateRef = useRef(null)

  const applyGameState = (data) => {
    setGameState(data.state)
    setMaskedWord(data.maskedWord || '')
    setTimeRemaining(data.timeRemainingSeconds || 0)
    setCurrentDrawerId(data.currentDrawerId || null)
    if (data.state !== 'CHOOSING_WORD') setWordChoices([])

    if (data.state === 'CHOOSING_WORD' && prevStateRef.current !== 'CHOOSING_WORD') {
      canvasRef.current?.resetCanvas()
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
    stompClient.publish({ destination: `/app/room/${roomCode}/start` })
  }

  const handleChooseWord = (word) => {
    stompClient.publish({
      destination: `/app/room/${roomCode}/choose-word`,
      body: JSON.stringify({ playerId, chosenWord: word })
    })
    setWordChoices([])
  }

  if (!roomCode) {
    return (
      <div style={{ fontFamily: 'sans-serif', padding: '2rem', maxWidth: '400px' }}>
        <h1>Scribble Clone</h1>

        <input
          type="text"
          placeholder="Your name"
          value={playerName}
          onChange={(e) => setPlayerName(e.target.value)}
          style={{ display: 'block', marginBottom: '1rem', padding: '0.5rem', width: '100%' }}
        />

        <button onClick={handleCreateRoom} disabled={loading} style={{ width: '100%', padding: '0.5rem', marginBottom: '1rem' }}>
          {loading ? 'Working...' : 'Create Room'}
        </button>

        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <input
            type="text"
            placeholder="Room code"
            value={joinCodeInput}
            onChange={(e) => setJoinCodeInput(e.target.value)}
            style={{ flex: 1, padding: '0.5rem' }}
          />
          <button onClick={handleJoinRoom} disabled={loading}>
            Join
          </button>
        </div>

        {error && <p style={{ color: 'red' }}>{error}</p>}
      </div>
    )
  }

  return (
    <div style={{ fontFamily: 'sans-serif', padding: '2rem' }}>
      <h1>Room: {roomCode}</h1>

      <div style={{ marginBottom: '1rem' }}>
        <button onClick={handleStartGame}>Start Game</button>
        <span style={{ marginLeft: '1rem' }}>
          State: <strong>{gameState}</strong>
          {' · '}Time: <strong>{timeRemaining}s</strong>
          {' · '}Word: <strong style={{ letterSpacing: '2px' }}>{maskedWord}</strong>
        </span>
      </div>

      {isDrawer && gameState === 'CHOOSING_WORD' && wordChoices.length > 0 && (
        <div style={{ marginBottom: '1rem', padding: '0.5rem', border: '2px solid #333' }}>
          <strong>Choose a word to draw:</strong>{' '}
          {wordChoices.map((w) => (
            <button key={w} onClick={() => handleChooseWord(w)} style={{ marginLeft: '0.5rem' }}>
              {w}
            </button>
          ))}
        </div>
      )}

      {isDrawer ? (
        <p style={{ color: 'green' }}>It's your turn to draw!</p>
      ) : (
        <p>{players.find(p => p.id === currentDrawerId)?.name || 'Someone'} is drawing...</p>
      )}

      <DrawingCanvas
        ref={canvasRef}
        stompClient={stompClient}
        roomCode={roomCode}
        playerId={playerId}
        connected={connected}
        canDraw={isDrawer && gameState === 'DRAWING'}
      />

      <p>Share this code with friends to have them join.</p>

      <h3>Players ({players.length})</h3>
      <ul>
        {players.map((p) => (
          <li key={p.id}>
            {p.name} — {p.score} pts
            {p.isDrawing && " ✏️"}
            {p.hasGuessedCorrectly && " ✅"}
          </li>
        ))}
      </ul>

      <h3>Chat / Guesses</h3>
      <ul>
        {chatLog.map((entry, i) => (
          <li key={i} style={{ color: entry.wasCorrectGuess ? 'green' : 'black' }}>
            <strong>{entry.playerName}:</strong> {entry.message}
          </li>
        ))}
      </ul>

      <input
        type="text"
        placeholder="Type your guess"
        value={guessInput}
        onChange={(e) => setGuessInput(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Enter') handleSubmitGuess() }}
        disabled={isDrawer}
      />
      <button onClick={handleSubmitGuess} disabled={isDrawer}>Guess</button>
    </div>
  )
}

export default App;