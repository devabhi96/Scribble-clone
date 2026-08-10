import AvatarPicker from "../AvatarPicker.jsx";

export default function LandingPage({

  authRole,
  authReady,
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
  
  avatarIndex,
  setAvatarIndex,
  playerName,
  setPlayerName,
  joinCodeInput,
  setJoinCodeInput,
  loading,
  error,
  handleCreateRoom,
  handleJoinRoom,
}) {
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

        <div className="landing-auth" style={{ marginBottom: '1rem', textAlign: 'center' }}>
          {authRole === 'USER' ? (
            <p style={{ fontSize: '0.85rem' }}>
              Logged in as <strong>{loginUsername || 'you'}</strong> ·{' '}
              <button className="link-btn" onClick={handleLogout}>Log out</button>
            </p>
          ) : !showLogin ? (
            <p style={{ fontSize: '0.85rem' }}>
              Playing as guest ·{' '}
              <button className="link-btn" onClick={() => setShowLogin(true)}>Log in / Sign up</button>
            </p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', maxWidth: 260, margin: '0 auto' }}>
              <input
                type="text"
                className="landing-input"
                placeholder="Username"
                value={loginUsername}
                onChange={(e) => setLoginUsername(e.target.value)}
                maxLength={20}
              />
              <input
                type="password"
                className="landing-input"
                placeholder="Password (min 8 chars)"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                maxLength={72}
              />
              {authError && <p style={{ color: 'salmon', fontSize: '0.8rem' }}>{authError}</p>}
              <div style={{ display: 'flex', gap: '0.4rem' }}>
                <button className="marker-btn" onClick={handleLogin}>Log in</button>
                <button className="marker-btn" onClick={handleRegister}>Sign up</button>
                <button className="link-btn" onClick={() => setShowLogin(false)}>Cancel</button>
              </div>
            </div>
          )}
        </div>

        <AvatarPicker index={avatarIndex} onChange={setAvatarIndex} />

        <input
          type="text"
          className="landing-input"
          placeholder="Your name"
          value={playerName}
          onChange={(e) => setPlayerName(e.target.value)}
          maxLength={20}
        />

        <button className="marker-btn" onClick={handleCreateRoom} disabled={loading || !authReady}>
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
  );
}