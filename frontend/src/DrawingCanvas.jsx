import { useRef, useEffect, useState } from "react";

function DrawingCanvas({ stompClient, roomCode }) {
  const canvasRef = useRef(null)
  const isDrawingRef = useRef(false)
  const lastPointRef = useRef({ x: 0, y: 0 })
  const pointBufferRef = useRef([]) 

  const [color, setColor] = useState('#000000')
  const [brushSize, setBrushSize] = useState(4)
  const [strokeHistory, setStrokeHistory] = useState([])

  useEffect(() => {
    const canvas = canvasRef.current
    canvas.width = 800
    canvas.height = 500
    const ctx = canvas.getContext('2d')
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
  }, [])

  
  useEffect(() => {
    const interval = setInterval(() => {
      if (pointBufferRef.current.length === 0) return
      if (!stompClient || !stompClient.connected) return

      stompClient.publish({
        destination: `/app/room/${roomCode}/draw`,
        body: JSON.stringify({
          points: pointBufferRef.current,
          color,
          brushSize
        })
      })

      pointBufferRef.current = []
    }, 80)

    return () => clearInterval(interval)
  }, [stompClient, roomCode, color, brushSize])

  
  useEffect(() => {
    if (!stompClient) return

    const trySubscribe = () => {
      if (!stompClient.connected) return
      return stompClient.subscribe(`/topic/room/${roomCode}/draw`, (message) => {
        const data = JSON.parse(message.body)
        replayRemoteStroke(data)
      })
    }

    let subscription
    if (stompClient.connected) {
      subscription = trySubscribe()
    } else {
      const previousOnConnect = stompClient.onConnect
      stompClient.onConnect = (frame) => { 
        subscription = trySubscribe() }
    }

    return () => subscription?.unsubscribe()
  }, [stompClient, roomCode])

  const remoteLastPointRef = useRef(null) 

const replayRemoteStroke = ({ points, color, brushSize }) => {
  const ctx = canvasRef.current.getContext('2d')
  ctx.strokeStyle = color
  ctx.lineWidth = brushSize

  points.forEach((point) => {
    if (point.type === 'start') {
      remoteLastPointRef.current = { x: point.x, y: point.y }
      return
    }
    if (point.type === 'end') {
      remoteLastPointRef.current = null
      return
    }
    
    if (remoteLastPointRef.current) {
      ctx.beginPath()
      ctx.moveTo(remoteLastPointRef.current.x, remoteLastPointRef.current.y)
      ctx.lineTo(point.x, point.y)
      ctx.stroke()
    }
    remoteLastPointRef.current = { x: point.x, y: point.y }
  })
}

  const getPos = (e) => {
    const canvas = canvasRef.current
    const rect = canvas.getBoundingClientRect()
    const clientX = e.touches ? e.touches[0].clientX : e.clientX
    const clientY = e.touches ? e.touches[0].clientY : e.clientY
    return { x: Math.round(clientX - rect.left), y: Math.round(clientY - rect.top) }
  }

  const startDrawing = (e) => {
    isDrawingRef.current = true
    const pos = getPos(e)
    lastPointRef.current = pos
    pointBufferRef.current.push({ ...pos, type: 'start' })
  }

  const draw = (e) => {
    if (!isDrawingRef.current) return

    const ctx = canvasRef.current.getContext('2d')
    const newPoint = getPos(e)

    ctx.strokeStyle = color
    ctx.lineWidth = brushSize
    ctx.beginPath()
    ctx.moveTo(lastPointRef.current.x, lastPointRef.current.y)
    ctx.lineTo(newPoint.x, newPoint.y)
    ctx.stroke()

    lastPointRef.current = newPoint
    pointBufferRef.current.push({ ...newPoint, type: 'move' })
  }

  const stopDrawing = () => {
    if (isDrawingRef.current) {
      pointBufferRef.current.push({ ...lastPointRef.current, type: 'end' })
      const dataUrl = canvasRef.current.toDataURL()
      setStrokeHistory((prev) => [...prev, dataUrl])
    }
    isDrawingRef.current = false
  }

  const clearCanvas = () => {
    const canvas = canvasRef.current
    const ctx = canvas.getContext('2d')
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    setStrokeHistory([])
  }

  const undo = () => {
    if (strokeHistory.length === 0) return
    const newHistory = strokeHistory.slice(0, -1)
    setStrokeHistory(newHistory)

    const canvas = canvasRef.current
    const ctx = canvas.getContext('2d')
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    if (newHistory.length === 0) return
    const img = new Image()
    img.src = newHistory[newHistory.length - 1]
    img.onload = () => ctx.drawImage(img, 0, 0)
  }

  return (
    <div>
      <div style={{ marginBottom: '0.5rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
        <input type="color" value={color} onChange={(e) => setColor(e.target.value)} />
        <input type="range" min="1" max="20" value={brushSize}
          onChange={(e) => setBrushSize(Number(e.target.value))} />
        <span>{brushSize}px</span>
        <button onClick={undo}>Undo</button>
        <button onClick={clearCanvas}>Clear</button>
      </div>

      <canvas
        ref={canvasRef}
        style={{ border: '2px solid #333', touchAction: 'none', cursor: 'crosshair' }}
        onMouseDown={startDrawing}
        onMouseMove={draw}
        onMouseUp={stopDrawing}
        onMouseLeave={stopDrawing}
        onTouchStart={startDrawing}
        onTouchMove={draw}
        onTouchEnd={stopDrawing}
      />
    </div>
  )
}

export default DrawingCanvas;