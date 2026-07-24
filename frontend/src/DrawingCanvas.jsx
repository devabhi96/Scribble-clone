import {useRef, useEffect, useState} from "react";

function DrawingCanvas(){
    const canvasRef = useRef(null)
    const isDrawingRef = useRef(false)
    const lastPointRef = useRef({x:0, y:0})

    const [color,setColor] = useState('#000000')
    const [brushSize,setBrushSize] = useState(4)
    const [strokeHistory,setStrokeHistory] = useState([])
    
    useEffect(() => {
        const canvas = canvasRef.current
        canvas.width = 800
        canvas.height = 500
        const ctx = canvas.getContext('2d')
        ctx.lineCap = 'round'
        ctx.lineJoin = 'round'
    },[])

    const getPos = (e) => {
        
        const canvas = canvasRef.current
        const rect = canvas.getBoundingClientRect()
        const clientX =e.touches ? e.touches[0].clientX : e.clientX
        const clientY = e.touches ? e.touches[0].clientY : e.clientY
        return {
            x: clientX - rect.left,
            y: clientY - rect.top
        }
    }

    const startDrawing = (e) => {
        isDrawingRef.current = true
        lastPointRef.current = getPos(e)
    }

    const draw = (e) => {
        if(!isDrawingRef.current) return
        
        const ctx = canvasRef.current.getContext('2d')
        const newPoint = getPos(e)

        ctx.strokeStyle = color
        ctx.lineWidth = brushSize
        ctx.beginPath()
        ctx.moveTo(lastPointRef.current.x,lastPointRef.current.y)
        ctx.lineTo(newPoint.x,newPoint.y)
        ctx.stroke()
    
        lastPointRef.current = newPoint
    }

    const stopDrawing = () => {
        if(isDrawingRef.current){
            const dataUrl = canvasRef.current.toDataURL()
            setStrokeHistory((prev) => [...prev,dataUrl])
        }
        isDrawingRef.current = false
    }
    
    const clearCanvas = () => {
        const canvas = canvasRef.current
        const ctx = canvas.getContext('2d')
        ctx.clearRect(0,0,canvas.width,canvas.height)
        setStrokeHistory([])
    }

    const undo = () => {
        if(strokeHistory.length === 0) return
        const newHistory = strokeHistory.slice(0,-1)
        setStrokeHistory(newHistory)

        const canvas = canvasRef.current
        const ctx = canvas.getContext('2d')
        ctx.clearRect(0,0,canvas.width,canvas.height)

        if(newHistory.length === 0) return

        const img = new Image()
        img.src = newHistory[newHistory.length-1]
        img.onload = () => ctx.drawImage(img,0,0)
    }
     return (
    <div>
      <div style={{ marginBottom: '0.5rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
        <input type="color" value={color} onChange={(e) => setColor(e.target.value)} />
        <input
          type="range" min="1" max="20" value={brushSize}
          onChange={(e) => setBrushSize(Number(e.target.value))}
        />
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
