import { useRef, useEffect, useState, forwardRef, useImperativeHandle } from "react";

const DrawingCanvas = forwardRef(function DrawingCanvas(
  { stompClient, roomCode, playerId, connected, canDraw },
  ref
) {
  const canvasRef = useRef(null)
  const isDrawingRef = useRef(false)
  const lastPointRef = useRef({ x: 0, y: 0 })
  const pointBufferRef = useRef([])
  const remoteLastPointRef = useRef(null)

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
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
  }, [])

  const drawRemoteBatch = (batch) => {
    const ctx = canvasRef.current.getContext('2d')
    ctx.strokeStyle = batch.color
    ctx.lineWidth = batch.brushSize

    for (const point of batch.points) {
      if (point.type === 'START') {
        remoteLastPointRef.current = point
        continue
      }
      if (point.type === 'END') {
        remoteLastPointRef.current = null
        continue
      }
      if (remoteLastPointRef.current) {
        ctx.beginPath()
        ctx.moveTo(remoteLastPointRef.current.x, remoteLastPointRef.current.y)
        ctx.lineTo(point.x, point.y)
        ctx.stroke()
      }
      remoteLastPointRef.current = point
    }
  }

  const clearBoard = () => {
    const ctx = canvasRef.current.getContext('2d')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvasRef.current.width, canvasRef.current.height)
  }

  const loadStrokeHistory = (strokes) => {
    remoteLastPointRef.current = null
    clearBoard()
    strokes.forEach((batch) => drawRemoteBatch(batch))
  }

 const resetCanvas = () => {
    remoteLastPointRef.current = null
    clearBoard()
    setStrokeHistory([])
  }

  useImperativeHandle(ref, () => ({
    drawRemoteBatch,
    loadStrokeHistory,
    resetCanvas
  }))

  useEffect(() => {
    if (!stompClient) return

    const interval = setInterval(() => {
      if (pointBufferRef.current.length === 0) return
      if (!stompClient.connected) return

      stompClient.publish({
        destination: `/app/room/${roomCode}/draw`,
        body: JSON.stringify({
          playerId,
          points: pointBufferRef.current,
          color,
          brushSize
        })
      })

      pointBufferRef.current = []
    }, 80)

    return () => clearInterval(interval)
  }, [stompClient, roomCode, color, brushSize, playerId])

  const getCanvasPoint = (e) => {
    const canvas = canvasRef.current
    const rect = canvas.getBoundingClientRect()
    return {
      x: Math.round(e.clientX - rect.left),
      y: Math.round(e.clientY - rect.top)
    }
  }

  const drawLocalSegment = (from, to) => {
    const ctx = canvasRef.current.getContext('2d')
    ctx.strokeStyle = color
    ctx.lineWidth = brushSize
    ctx.beginPath()
    ctx.moveTo(from.x, from.y)
    ctx.lineTo(to.x, to.y)
    ctx.stroke()
  }

  const handleMouseDown = (e) => {
    if (!canDraw) return
    isDrawingRef.current = true
    const point = getCanvasPoint(e)
    lastPointRef.current = point
    pointBufferRef.current.push({ x: point.x, y: point.y, type: 'START' })
  }

  const handleMouseMove = (e) => {
    if (!canDraw || !isDrawingRef.current) return
    const point = getCanvasPoint(e)
    drawLocalSegment(lastPointRef.current, point)
    pointBufferRef.current.push({ x: point.x, y: point.y, type: 'MOVE' })
    lastPointRef.current = point
  }

  const handleMouseUp = (e) => {
    if (!canDraw || !isDrawingRef.current) return
    isDrawingRef.current = false
    const point = getCanvasPoint(e)
    pointBufferRef.current.push({ x: point.x, y: point.y, type: 'END' })
    setStrokeHistory((prev) => [...prev, canvasRef.current.toDataURL()])
  }

  const handleClear = () => {
    clearBoard()
    setStrokeHistory([])
  }

  const handleUndo = () => {
    if (strokeHistory.length === 0) return
    const newHistory = strokeHistory.slice(0, -1)
    setStrokeHistory(newHistory)
    clearBoard()

    if (newHistory.length > 0) {
      const img = new Image()
      img.src = newHistory[newHistory.length - 1]
      img.onload = () => canvasRef.current.getContext('2d').drawImage(img, 0, 0)
    }
  }

  return (
    <div>
      <div style={{ marginBottom: '0.5rem' }}>
        <input type="color" value={color} onChange={(e) => setColor(e.target.value)} disabled={!canDraw} />
        <input
          type="range" min="1" max="20" value={brushSize}
          onChange={(e) => setBrushSize(Number(e.target.value))}
          disabled={!canDraw}
        />
        <button onClick={handleUndo} disabled={!canDraw}>Undo</button>
        <button onClick={handleClear} disabled={!canDraw}>Clear</button>
      </div>
      <canvas
        ref={canvasRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        style={{
          border: '1px solid black',
          touchAction: 'none',
          backgroundColor: '#ffffff',
          cursor: canDraw ? 'crosshair' : 'not-allowed'
        }}
      />
    </div>
  )
})

export default DrawingCanvas