import DrawingCanvas from "../DrawingCanvas.jsx";

const STATE_LABELS = {
  WAITING: 'Waiting',
  CHOOSING_WORD: 'Choosing Word',
  DRAWING: 'Drawing',
  ROUND_END: 'Round Over',
  GAME_OVER: 'Game Over'
};

const ROUND_OPTIONS = [1, 2, 3, 5, 8, 10];

export default function GamePage({
  roomCode,
  playerId,
  players,
  hostPlayerId,
  isHost,
  stompClient,
  connected,
  chatLog,
  chatLogRef,
  guessInput,
  setGuessInput,
  gameState,
  maskedWord,
  actualWord,
  timeRemaining,
  currentDrawerId,
  wordChoices,
  currentRound,
  totalRounds,
  infiniteRounds,
  revealedWord,
  autoResumeTimer,
  canvasRef,
  handleSubmitGuess,
  handleStartGame,
  handleChooseWord,
  handleRoundsChange,
  handleExitRoom,
}) {
  const isDrawer = currentDrawerId === playerId;
  const settingsLocked = gameState === 'DRAWING' || gameState === 'CHOOSING_WORD' || gameState === 'ROUND_END';

  const getStatusMessage = () => {
    switch (gameState) {
      case 'WAITING':
        if (timeRemaining > 0) {
          if (players.length < 2) {
            return { text: 'Game paused. Waiting for more players...', color: 'var(--chalk-white-dim)' };
          } else if (autoResumeTimer !== null && autoResumeTimer > 0) {
            return { text: `Game resuming in ${autoResumeTimer}s...`, color: 'var(--marker-yellow)' };
          }
        }
        return { text: 'Game has not started yet — click Start Game when everyone has joined.', color: 'var(--chalk-white-dim)' };
      case 'CHOOSING_WORD':
        return isDrawer
          ? { text: "It's your turn — pick a word above!", color: '#7cd68c' }
          : { text: `${players.find(p => p.id === currentDrawerId)?.name || 'A player'} is choosing a word...`, color: 'var(--chalk-white-dim)' };
      case 'DRAWING':
        return isDrawer
          ? { text: "It's your turn to draw!", color: '#7cd68c' }
          : { text: `${players.find(p => p.id === currentDrawerId)?.name || 'Someone'} is drawing...`, color: 'var(--chalk-white)' };
      case 'ROUND_END':
        return { text: 'Round over! Next turn starting soon...', color: 'var(--chalk-white-dim)' };
      case 'GAME_OVER':
        return { text: 'Game over! Check the final scores below.', color: 'var(--marker-yellow)' };
      default:
        return { text: '', color: 'var(--chalk-white)' };
    }
  };

  const status = getStatusMessage();
  const sortedPlayers = [...players].sort((a, b) => b.score - a.score);
  const roundLabel = infiniteRounds
    ? `${currentRound + 1}`
    : `${Math.min(currentRound + 1, totalRounds)}/${totalRounds}`;

  return (
    <div className="game-page">
      <div className="game-topbar">
        <div className="room-code-block">
          <h1>Room: {roomCode}</h1>
          <p className="share-hint">Share this code so friends can join!</p>
        </div>

        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <button
            className="marker-btn"
            style={{ width: 'auto', padding: '0.55rem 1.1rem' }}
            onClick={handleStartGame}
            disabled={!isHost || players.length < 2}
          >
            {gameState === 'GAME_OVER' ? 'Play Again' : (timeRemaining > 0 && gameState === 'WAITING' ? 'Resume Game' : 'Start Game')}
          </button>

          <button
            className="marker-btn secondary"
            style={{ width: 'auto', padding: '0.55rem 1.1rem', backgroundColor: '#e74c3c', color: 'white', borderColor: '#c0392b' }}
            onClick={handleExitRoom}
          >
            Exit Room
          </button>
        </div>

        {!isHost && <span className="host-hint">(Only the host can start)</span>}
        {isHost && players.length < 2 && <span className="host-hint">(Need 2+ players)</span>}

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
              <div key={entry.id || i} className={`chat-entry ${entry.wasCorrectGuess ? 'correct' : ''} ${entry.playerName === 'System' ? 'system-msg' : ''}`}>
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
  );
}