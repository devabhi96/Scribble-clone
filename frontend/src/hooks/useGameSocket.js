import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";

const WS_URL = import.meta.env.VITE_WS_URL || "ws://localhost:8080/ws";
// Caps how many chat/guess entries we keep in memory client-side, so a long
// game session doesn't grow this array (and the DOM list rendering it) forever.
const MAX_CHAT_LOG_ENTRIES = 200;

export function useGameSocket({ roomCode, authToken, playerName, playerId }) {
  const [players, setPlayers] = useState([]);
  const [hostPlayerId, setHostPlayerId] = useState(null);
  const [stompClient, setStompClient] = useState(null);
  const [connected, setConnected] = useState(false);
  const [chatLog, setChatLog] = useState([]);
  const [guessInput, setGuessInput] = useState("");

  const [gameState, setGameState] = useState("WAITING");
  const [maskedWord, setMaskedWord] = useState("");
  const [actualWord, setActualWord] = useState(null);
  const [timeRemaining, setTimeRemaining] = useState(0);
  const [currentDrawerId, setCurrentDrawerId] = useState(null);
  const [wordChoices, setWordChoices] = useState([]);
  const [currentRound, setCurrentRound] = useState(0);
  const [totalRounds, setTotalRounds] = useState(3);
  const [infiniteRounds, setInfiniteRounds] = useState(false);
  const [revealedWord, setRevealedWord] = useState(null);
  const [autoResumeTimer, setAutoResumeTimer] = useState(null);

  const canvasRef = useRef(null);
  const prevStateRef = useRef(null);
  const chatLogRef = useRef(null);

  const isHost = hostPlayerId === playerId;

  const applyGameState = (data) => {
    setGameState(data.state);
    setMaskedWord(data.maskedWord || "");
    setTimeRemaining(data.timeRemainingSeconds || 0);
    setCurrentDrawerId(data.currentDrawerId || null);
    setCurrentRound(data.currentRound || 0);
    setTotalRounds(data.totalRounds || 3);
    setInfiniteRounds(!!data.infiniteRounds);
    setRevealedWord(data.revealedWord || null);
    if (data.state !== "CHOOSING_WORD") setWordChoices([]);

    if (data.state === "CHOOSING_WORD" && prevStateRef.current !== "CHOOSING_WORD") {
      canvasRef.current?.resetCanvas();
      setActualWord(null);
    }
    prevStateRef.current = data.state;
  };

  useEffect(() => {
    if (!roomCode || !authToken) return;

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${authToken}` },
      onConnect: () => {
        setConnected(true);

        client.subscribe(`/topic/room/${roomCode}/players`, (message) => {
          const data = JSON.parse(message.body);
          setPlayers(data.players || []);
          setHostPlayerId(data.hostPlayerId || null);
        });

        client.subscribe(`/topic/room/${roomCode}/state`, (message) => {
          applyGameState(JSON.parse(message.body));
        });

        client.subscribe(`/user/queue/state-sync`, (message) => {
          applyGameState(JSON.parse(message.body));
        });

        client.subscribe(`/user/queue/word-choices`, (message) => {
          const data = JSON.parse(message.body);
          setWordChoices(data.options || []);
        });

        client.subscribe(`/user/queue/current-word`, (message) => {
          const data = JSON.parse(message.body);
          setActualWord(data.word || null);
        });

        client.subscribe(`/user/queue/sync`, (message) => {
          const data = JSON.parse(message.body);
          canvasRef.current?.loadStrokeHistory(data.strokes || []);
        });

        client.subscribe(`/topic/room/${roomCode}/chat`, (message) => {
          const data = JSON.parse(message.body);
          const msgWithId = { ...data, id: Date.now() + Math.random() };

          setChatLog((prev) => {
            const next = [...prev, msgWithId];
            // Trim from the front once we exceed the cap, oldest messages first.
            return next.length > MAX_CHAT_LOG_ENTRIES
              ? next.slice(next.length - MAX_CHAT_LOG_ENTRIES)
              : next;
          });

          if (data.playerName === "System") {
            setTimeout(() => {
              setChatLog((prev) => prev.filter((m) => m.id !== msgWithId.id));
            }, 12000);
          }
        });

        client.subscribe(`/topic/room/${roomCode}/draw`, (message) => {
          const data = JSON.parse(message.body);
          canvasRef.current?.drawRemoteBatch(data);
        });

        client.publish({
          destination: `/app/room/${roomCode}/join`,
          body: JSON.stringify({ roomCode, playerName: playerName.trim() }),
        });
      },
      onStompError: (frame) => console.error("STOMP error", frame),
    });

    client.activate();
    setStompClient(client);

    return () => {
      client.deactivate();
      setConnected(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomCode, authToken]);

  useEffect(() => {
    if (chatLogRef.current) {
      chatLogRef.current.scrollTop = chatLogRef.current.scrollHeight;
    }
  }, [chatLog]);

  useEffect(() => {
    let interval;
    if (gameState === "WAITING" && players.length >= 2 && timeRemaining > 0) {
      // A countdown-before-auto-resume has no external system to subscribe to —
      // the timer IS the state. Direct setState here is the correct approach;
      // the lint rule's "cascading renders" concern doesn't apply to a value
      // no other effect reads synchronously in the same render.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setAutoResumeTimer(5);
      interval = setInterval(() => {
        setAutoResumeTimer((prev) => {
          if (prev <= 1) {
            clearInterval(interval);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      setAutoResumeTimer(null);
    }
    return () => clearInterval(interval);
  }, [gameState, players.length, timeRemaining]);

  const resetRoomState = () => {
    setGameState("WAITING");
    setPlayers([]);
    setChatLog([]);
    setWordChoices([]);
    setActualWord(null);
  };

  const handleSubmitGuess = () => {
    if (!guessInput.trim()) return;
    stompClient.publish({
      destination: `/app/room/${roomCode}/guess`,
      body: JSON.stringify({ text: guessInput.trim() }),
    });
    setGuessInput("");
  };

  const handleStartGame = () => {
    if (!isHost) return;
    stompClient.publish({
      destination: `/app/room/${roomCode}/start`,
    });
  };

  useEffect(() => {
    if (autoResumeTimer === 0) {
      if (isHost) {
        handleStartGame();
      }
      // Resetting the timer that drove this very effect back to its idle
      // value — not a cascading update to unrelated state, just closing out
      // this effect's own countdown.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setAutoResumeTimer(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoResumeTimer, isHost]);

  const handleChooseWord = (word) => {
    stompClient.publish({
      destination: `/app/room/${roomCode}/choose-word`,
      body: JSON.stringify({ chosenWord: word }),
    });
    setWordChoices([]);
  };

  const publishSettings = (nextTotalRounds, nextInfinite) => {
    if (!isHost || !stompClient) return;
    stompClient.publish({
      destination: `/app/room/${roomCode}/settings`,
      body: JSON.stringify({ totalRounds: nextTotalRounds, infiniteRounds: nextInfinite }),
    });
  };

  const handleRoundsChange = (e) => {
    const val = e.target.value;
    if (val === "infinite") {
      publishSettings(totalRounds, true);
    } else {
      publishSettings(Number(val), false);
    }
  };

  return {
    players,
    hostPlayerId,
    isHost,
    stompClient,
    connected,
    chatLog,
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
    chatLogRef,
    resetRoomState,
    handleSubmitGuess,
    handleStartGame,
    handleChooseWord,
    handleRoundsChange,
  };
}