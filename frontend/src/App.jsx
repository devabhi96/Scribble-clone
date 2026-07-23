import { useState } from "react";

const API_BASE = 'http://localhost:8080'

function App() {
  const [roomCode, setRoomCode] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const handleCreateRoom = () => {
    setLoading(true)
    setError(null)

    fetch(`${API_BASE}/api/rooms`, { method: 'POST' })
      .then((res) => {
        if (!res.ok) throw new Error(`Server responded with ${res.status}`)
        return res.json()
      })
      .then((data) => setRoomCode(data.roomCode))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }

  return (
    <div style={{ fontFamily: 'sans-serif', padding: '2rem' }}>
      <h1>Scribble Clone</h1>

      <button onClick={handleCreateRoom} disabled={loading}>
        {loading ? 'Creating...' : 'Create Room'}
      </button>

      {error && <p style={{ color: 'red' }}>Error: {error}</p>}

      {roomCode && (
        <div style={{ background: '#eef', padding: '1rem', borderRadius: '8px', marginTop: '1rem' }}>
          <p>Room created! Share this code with friends:</p>
          <h2>{roomCode}</h2>
        </div>
      )}
    </div>
  )
}

export default App;