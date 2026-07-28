import DrawingCanvas from "./DrawingCanvas.jsx";

import { useEffect, useState, useRef } from "react";
import { Client } from '@stomp/stompjs'

const API_BASE = 'http://localhost:8080'
const WS_URL = 'ws://localhost:8080/ws'

function App() {
  const [roomCode, setRoomCode] = useState(null)
  const [playerName, setPlayerName] = useState('')
  const [joinCodeInput, setJoinCodeInput] = useState('')
  const [players, setPlayers] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [stompClient, setStompClient] = useState(null)
  const [playerId] = useState(() => crypto.randomUUID())
  const [chatLog, setChatLog] = useState([])
  const [guessInput, setGuessInput] = useState('')

  useEffect(() => {
    if (!roomCode) return

    const client = new Client({
      brokerURL: WS_URL,
      onConnect: () => {
        client.subscribe(`/topic/room/${roomCode}/players`, (message) => {
          const data = JSON.parse(message.body)
          setPlayers(data.players || [])
        })

        client.subscribe(`/topic/room/${roomCode}/state`, (message) => {
          console.log('GAME STATE:', JSON.parse(message.body))
        })

        client.subscribe(`/topic/room/${roomCode}/word-choices`, (message) => {
          console.log('WORD CHOICES:', JSON.parse(message.body))
        })

        client.subscribe(`/topic/room/${roomCode}/chat`, (message) => {
          const data = JSON.parse(message.body)
          setChatLog((prev) => [...prev, data])
        })

        client.publish({
          destination: `/app/room/${roomCode}/join`,
          body: JSON.stringify({ roomCode, playerName: playerName.trim(), playerId })
        })
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame)
      }
    })

    client.activate()
    setStompClient(client)

    return () => {
      client.deactivate()
    }
  }, [roomCode])

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
      <button onClick={() => {
        stompClient.publish({ destination: `/app/room/${roomCode}/start` })
      }}>
        Start Game (test)
      </button>

      <button onClick={() => {
        stompClient.publish({
          destination: `/app/room/${roomCode}/choose-word`,
          body: JSON.stringify({ chosenWord: 'apple' })
        })
      }}>
        Choose "apple" (test)
      </button>

      <DrawingCanvas stompClient={stompClient} roomCode={roomCode}/>
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
      />
      <button onClick={handleSubmitGuess}>Guess</button>
    </div>
  )
}

export default App;