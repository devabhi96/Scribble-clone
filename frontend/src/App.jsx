import { useEffect, useState } from "react";

const API_BASE = 'http://localhost:8080'

function App(){
  const [pingResult,setPingResult] = useState(null)
  const [error,setError] = useState(null)

  useEffect(() => {
    fetch(`${API_BASE}/api/ping`)
    .then((res) => {
      if(!res.ok) throw new Error(`Server responded with ${res.status}`)
        return res.json()
})
.then((data)=> setPingResult(data))
.catch((err) => setError(err.message))
},[])


return (
    <div style={{ fontFamily: 'sans-serif', padding: '2rem' }}>
      <h1>Scribble Clone — Phase 0 Checkpoint</h1>

      {error && <p style={{ color: 'red' }}>Could not reach backend: {error}</p>}
      {!error && !pingResult && <p>Contacting backend...</p>}

      {pingResult && (
        <div style={{ background: '#eef', padding: '1rem', borderRadius: '8px' }}>
          <p><strong>message:</strong> {pingResult.message}</p>
          <p><strong>timestamp:</strong> {pingResult.timestamp}</p>
          <p style={{ color: 'green' }}>✅ Wiring confirmed — move to Phase 1.</p>
        </div>
      )}
    </div>
  )
}
export default App;