package com.scribble.backend.websocket;

import com.scribble.backend.dto.PlayerListMessage;
import com.scribble.backend.model.GameRoom;
import com.scribble.backend.service.GameService;
import com.scribble.backend.service.RoomService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class WebSocketEventListener {

    private static final int GRACE_PERIOD_SECONDS = 30;

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final GameService gameService;

    public WebSocketEventListener(RoomService roomService, SimpMessagingTemplate messagingTemplate, GameService gameService) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        RoomService.SessionInfo info = roomService.getSessionInfo(sessionId);
        roomService.removeSession(sessionId);
        if (info == null) return;

        GameRoom room = roomService.getRoom(info.roomCode());
        if (room == null) return;

        room.withLock(() -> {
            room.markDisconnected(info.playerId());


            gameService.handlePlayerDisconnected(info.roomCode(), info.playerId());

            ScheduledFuture<?> removalTask = scheduler.schedule(() -> {
                room.withLock(() -> {
                    if (room.isDisconnected(info.playerId())) {
                        room.getPlayers().remove(info.playerId());
                        room.getScores().remove(info.playerId());
                        room.getPendingRemovals().remove(info.playerId());
                        room.reassignHostIfNeeded(info.playerId());
                        broadcastPlayers(room);
                    }
                });
            }, GRACE_PERIOD_SECONDS, TimeUnit.SECONDS);

            room.getPendingRemovals().put(info.playerId(), removalTask);
        });

        broadcastPlayers(room);
    }

    private void broadcastPlayers(GameRoom room) {
        messagingTemplate.convertAndSend(
                "/topic/room/" + room.getRoomCode() + "/players",
                new PlayerListMessage(room.toPlayerDtos(), room.getHostPlayerId())
        );
    }
}