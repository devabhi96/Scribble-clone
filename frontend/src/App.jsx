import "./Landing.css";
import "./Game.css";

import { useState } from "react";
import { authFetch } from "./auth.js";
import { useAuth } from "./hooks/useAuth.js";
import { useGameSocket } from "./hooks/useGameSocket.js";
import LandingPage from "./pages/LandingPage.jsx";
import GamePage from "./pages/GamePage.jsx";

function App() {
  const [roomCode, setRoomCode] = useState(null);
  const [playerName, setPlayerName] = useState("");
  const [joinCodeInput, setJoinCodeInput] = useState("");
  const [avatarIndex, setAvatarIndex] = useState(0);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const auth = useAuth();
  const {
    playerId,
    authToken,
    authRole,
    authReady,
    authInitError,
    showLogin,
    setShowLogin,
    loginUsername,
    setLoginUsername,
    loginPassword,
    setLoginPassword,
    authError,
    handleLogin,
    handleRegister,
    handleLogout,
  } = auth;

  const game = useGameSocket({ roomCode, authToken, playerName, playerId });

  const handleCreateRoom = () => {
    if (!playerName.trim()) { setError('Enter your name first'); return }
    setLoading(true); setError(null)

    authFetch(`/api/rooms`, { method: 'POST' })
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

  const handleExitRoom = () => {
    if (game.stompClient) {
      game.stompClient.deactivate()
    }
    setRoomCode(null)
    game.resetRoomState()
  }

  if (!roomCode) {
    return (
      <LandingPage
        authRole={authRole}
        authReady={authReady}
        showLogin={showLogin}
        setShowLogin={setShowLogin}
        loginUsername={loginUsername}
        setLoginUsername={setLoginUsername}
        loginPassword={loginPassword}
        setLoginPassword={setLoginPassword}
        authError={authError}
        handleLogin={handleLogin}
        handleRegister={handleRegister}
        handleLogout={handleLogout}
        avatarIndex={avatarIndex}
        setAvatarIndex={setAvatarIndex}
        playerName={playerName}
        setPlayerName={setPlayerName}
        joinCodeInput={joinCodeInput}
        setJoinCodeInput={setJoinCodeInput}
        loading={loading}
        error={error || authInitError}
        handleCreateRoom={handleCreateRoom}
        handleJoinRoom={handleJoinRoom}
      />
    );
  }

  return (
    <GamePage
      roomCode={roomCode}
      playerId={playerId}
      players={game.players}
      hostPlayerId={game.hostPlayerId}
      isHost={game.isHost}
      stompClient={game.stompClient}
      connected={game.connected}
      chatLog={game.chatLog}
      chatLogRef={game.chatLogRef}
      guessInput={game.guessInput}
      setGuessInput={game.setGuessInput}
      gameState={game.gameState}
      maskedWord={game.maskedWord}
      actualWord={game.actualWord}
      timeRemaining={game.timeRemaining}
      currentDrawerId={game.currentDrawerId}
      wordChoices={game.wordChoices}
      currentRound={game.currentRound}
      totalRounds={game.totalRounds}
      infiniteRounds={game.infiniteRounds}
      revealedWord={game.revealedWord}
      autoResumeTimer={game.autoResumeTimer}
      canvasRef={game.canvasRef}
      handleSubmitGuess={game.handleSubmitGuess}
      handleStartGame={game.handleStartGame}
      handleChooseWord={game.handleChooseWord}
      handleRoundsChange={game.handleRoundsChange}
      handleExitRoom={handleExitRoom}
    />
  );
}

export default App;