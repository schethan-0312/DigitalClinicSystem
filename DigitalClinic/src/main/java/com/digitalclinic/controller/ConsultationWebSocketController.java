package com.digitalclinic.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class ConsultationWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    // roomId -> (principalName -> userDisplayName)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> roomParticipants = new ConcurrentHashMap<>();

    public ConsultationWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Join room
    @MessageMapping("/consultation.join")
    public void joinRoom(@Payload Map<String, Object> payload, Principal principal) {
        String roomId = (String) payload.get("roomId");
        String userName = payload.get("userName") != null ? payload.get("userName").toString() : principal.getName();
        String userType = payload.get("userType") != null ? payload.get("userType").toString() : "USER";

        roomParticipants.putIfAbsent(roomId, new ConcurrentHashMap<>());
        roomParticipants.get(roomId).put(principal.getName(), userName);

        // broadcast participants update
        messagingTemplate.convertAndSend("/topic/consultation." + roomId + ".participants",
                Map.of(
                        "type", "USER_JOINED",
                        "roomId", roomId,
                        "userId", principal.getName(),
                        "userName", userName,
                        "userType", userType,
                        "participants", roomParticipants.get(roomId)
                ));
    }

    // Leave room
    @MessageMapping("/consultation.leave")
    public void leaveRoom(@Payload Map<String, Object> payload, Principal principal) {
        String roomId = (String) payload.get("roomId");
        if (roomId == null) return;

        var m = roomParticipants.get(roomId);
        String userName = null;
        if (m != null) {
            userName = m.remove(principal.getName());
            if (m.isEmpty()) roomParticipants.remove(roomId);
        }

        messagingTemplate.convertAndSend("/topic/consultation." + roomId + ".participants",
                Map.of(
                        "type", "USER_LEFT",
                        "roomId", roomId,
                        "userId", principal.getName(),
                        "userName", userName,
                        "participants", roomParticipants.getOrDefault(roomId, new ConcurrentHashMap<>())
                ));
    }

    // Offer -> forward to target user's personal queue
    @MessageMapping("/consultation.webrtc.offer")
    public void webrtcOffer(@Payload Map<String, Object> payload, Principal principal) {
        String roomId = (String) payload.get("roomId");
        String targetUserId = payload.get("targetUserId").toString(); // target principal name (email or username)
        Object offer = payload.get("offer");

        messagingTemplate.convertAndSendToUser(
                targetUserId,
                "/queue/webrtc.offer",
                Map.of(
                        "roomId", roomId,
                        "fromUserId", principal.getName(),
                        "offer", offer
                )
        );
    }

    // Answer -> forward to caller
    @MessageMapping("/consultation.webrtc.answer")
    public void webrtcAnswer(@Payload Map<String, Object> payload, Principal principal) {
        String roomId = (String) payload.get("roomId");
        String targetUserId = payload.get("targetUserId").toString();
        Object answer = payload.get("answer");

        messagingTemplate.convertAndSendToUser(
                targetUserId,
                "/queue/webrtc.answer",
                Map.of(
                        "roomId", roomId,
                        "fromUserId", principal.getName(),
                        "answer", answer
                )
        );
    }

    // ICE candidate -> forward to target
    @MessageMapping("/consultation.webrtc.ice-candidate")
    public void webrtcIce(@Payload Map<String, Object> payload, Principal principal) {
        String roomId = (String) payload.get("roomId");
        String targetUserId = payload.get("targetUserId").toString();
        Object candidate = payload.get("candidate");

        messagingTemplate.convertAndSendToUser(
                targetUserId,
                "/queue/webrtc.ice-candidate",
                Map.of(
                        "roomId", roomId,
                        "fromUserId", principal.getName(),
                        "candidate", candidate
                )
        );
    }

    // Chat -> broadcast to room topic
    @MessageMapping("/consultation.chat")
    public void chat(@Payload Map<String, Object> payload, Principal principal) {
        String roomId = (String) payload.get("roomId");
        String content = (String) payload.get("content");
        String senderName = payload.get("senderName") != null ? payload.get("senderName").toString() : roomParticipants.getOrDefault(roomId, new ConcurrentHashMap<>()).get(principal.getName());

        messagingTemplate.convertAndSend("/topic/consultation." + roomId + ".chat",
                Map.of(
                        "roomId", roomId,
                        "senderId", principal.getName(),
                        "senderName", senderName,
                        "content", content,
                        "timestamp", System.currentTimeMillis(),
                        "senderType", payload.getOrDefault("senderType", "USER")
                ));
    }

    // Media toggle (video/audio) -> broadcast to room
    @MessageMapping("/consultation.media.toggle")
    public void mediaToggle(@Payload Map<String, Object> payload, Principal principal) {
        String roomId = (String) payload.get("roomId");
        messagingTemplate.convertAndSend("/topic/consultation." + roomId + ".media",
                Map.of(
                        "roomId", roomId,
                        "userId", principal.getName(),
                        "mediaType", payload.get("mediaType"),
                        "enabled", payload.get("enabled")
                ));
    }

    // Status update (IN_PROGRESS, COMPLETED etc.) -> broadcast
    @MessageMapping("/consultation.status.update")
    public void statusUpdate(@Payload Map<String, Object> payload, Principal principal) {
        String roomId = (String) payload.get("roomId");
        messagingTemplate.convertAndSend("/topic/consultation." + roomId + ".status",
                Map.of(
                        "roomId", roomId,
                        "status", payload.get("status"),
                        "updatedBy", principal.getName(),
                        "timestamp", System.currentTimeMillis()
                ));
    }

    // End consultation -> broadcast .end (and notify participants)
    @MessageMapping("/consultation.end")
    public void endConsultation(@Payload Map<String, Object> payload, Principal principal) {
        String roomId = (String) payload.get("roomId");
        messagingTemplate.convertAndSend("/topic/consultation." + roomId + ".end",
                Map.of(
                        "roomId", roomId,
                        "endedBy", principal.getName(),
                        "timestamp", System.currentTimeMillis()
                ));

        // Optionally remove participants map for that room
        roomParticipants.remove(roomId);
    }
}
