<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <title>${title} - DigitalClinic</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css" rel="stylesheet">
    <script src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>
    <style>
        /* (your entire CSS stays identical — unchanged) */
        .video-container { background: #1a1a1a; min-height: 100vh; color: white; }
        .video-main { height: 70vh; background: #2d2d2d; border-radius: 10px; position: relative; }
        .video-remote { width: 100%; height: 100%; object-fit: cover; border-radius: 10px; }
        .video-local { position: absolute; bottom: 20px; right: 20px; width: 200px; height: 150px; border: 2px solid #007bff; border-radius: 8px; background: #000; }
        .controls-container { background: rgba(45,45,45,0.9); border-radius: 10px; padding: 1rem; margin-top: 1rem; }
        .control-btn { width: 60px; height: 60px; border-radius: 50%; border: none; margin: 0 10px; font-size: 1.2rem; transition: all 0.3s; }
        .control-btn:hover { transform: scale(1.1); }
        .btn-mute, .btn-video, .btn-call { background: #6c757d; color: white; }
        .btn-call.active { background: #28a745; }
        .chat-container { background: #2d2d2d; border-radius: 10px; height: 400px; display: flex; flex-direction: column; }
        .chat-messages { flex: 1; overflow-y: auto; padding: 1rem; }
        .chat-input { border-top: 1px solid #444; padding: 1rem; }
        .message.system { background-color: #e9ecef; color: #333; font-style: italic; text-align: center; }
        .message.doctor { background-color: #007bff; color: white; margin-left: auto; }
        .message.patient { background-color: #28a745; color: white; margin-right: auto; }
    </style>
</head>
<body class="video-container">
    <div class="container-fluid py-4">
        <!-- (All HTML markup and Bootstrap content remain identical) -->
        <!-- ... existing video layout, patient info, chat, and modals ... -->
    </div>

    <script>
        // ✅ WebSocket & WebRTC Identity Configuration
        const roomId = '${consultation.roomId}';
        const userId = '${user.email}'; // ✅ use email for signaling
        const userName = '${patient.user.fullName}';
        const userType = 'PATIENT';
        const doctorUserId = '${consultation.appointment.doctor.user.email}'; // ✅ doctor email for signaling

        let stompClient = null;
        let localStream = null;
        let peerConnection = null;
        let isMuted = false;
        let isVideoOn = true;
        let consultationStartTime = new Date();
        let timerInterval = null;

        // WebSocket Connection
        function connect() {
            const socket = new SockJS('/ws-video-consultation');
            stompClient = Stomp.over(socket);

            stompClient.connect({}, function (frame) {
                console.log('Connected to consultation: ' + roomId);
                updateConnectionStatus(true);

                // Join consultation room
                stompClient.send("/app/consultation.join", {}, JSON.stringify({
                    roomId: roomId,
                    userType: userType,
                    userName: userName
                }));

                // Subscribe & initialize
                subscribeToTopics();
                initializeWebRTC();
                startTimer();

            }, function (error) {
                console.error('WebSocket connection error:', error);
                updateConnectionStatus(false);
                setTimeout(connect, 5000);
            });
        }

        function subscribeToTopics() {
            stompClient.subscribe('/topic/consultation.' + roomId + '.participants', handleParticipantsUpdate);
            stompClient.subscribe('/user/queue/webrtc.offer', handleWebRTCOffer);
            stompClient.subscribe('/user/queue/webrtc.answer', handleWebRTCAnswer);
            stompClient.subscribe('/user/queue/webrtc.ice-candidate', handleICECandidate);
            stompClient.subscribe('/topic/consultation.' + roomId + '.chat', handleChatMessage);
            stompClient.subscribe('/topic/consultation.' + roomId + '.media', handleMediaUpdate);
            stompClient.subscribe('/topic/consultation.' + roomId + '.status', handleStatusUpdate);
            stompClient.subscribe('/topic/consultation.' + roomId + '.end', handleConsultationEnd);
            stompClient.subscribe('/user/queue/errors', handleError);
        }

        // ✅ Enhanced WebRTC with STUN + TURN configuration
        async function initializeWebRTC() {
            try {
                localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
                document.getElementById('localVideo').srcObject = localStream;
                createPeerConnection();
            } catch (error) {
                console.error('Media device access error:', error);
                showError('Unable to access camera/microphone.');
            }
        }

        function createPeerConnection() {
            const configuration = {
                iceServers: [
                    { urls: 'stun:stun.l.google.com:19302' },
                    {
                        urls: 'turn:your.turn.server:3478',
                        username: 'turnuser',
                        credential: 'turnpass'
                    }
                ]
            };

            peerConnection = new RTCPeerConnection(configuration);

            peerConnection.oniceconnectionstatechange = () => {
                console.log('ICE State:', peerConnection.iceConnectionState);
            };

            localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));

            peerConnection.ontrack = event => {
                const remoteVideo = document.getElementById('remoteVideo');
                const waitingMessage = document.getElementById('waitingMessage');
                remoteVideo.srcObject = event.streams[0];
                waitingMessage.style.display = 'none';
                addSystemMessage('Doctor connected');
            };

            peerConnection.onicecandidate = event => {
                if (event.candidate) {
                    stompClient.send("/app/consultation.webrtc.ice-candidate", {}, JSON.stringify({
                        roomId: roomId,
                        targetUserId: doctorUserId,
                        candidate: event.candidate
                    }));
                }
            };
        }

        // ✅ Handle Offer/Answer/Candidate exchange
        async function handleWebRTCOffer(message) {
            const data = JSON.parse(message.body);
            try {
                await peerConnection.setRemoteDescription(data.offer);
                const answer = await peerConnection.createAnswer();
                await peerConnection.setLocalDescription(answer);

                stompClient.send("/app/consultation.webrtc.answer", {}, JSON.stringify({
                    roomId: roomId,
                    targetUserId: data.fromUserId,
                    answer: answer
                }));
            } catch (error) {
                console.error('Error handling offer:', error);
            }
        }

        async function handleWebRTCAnswer(message) {
            const data = JSON.parse(message.body);
            try {
                await peerConnection.setRemoteDescription(data.answer);
            } catch (error) {
                console.error('Error handling answer:', error);
            }
        }

        function handleICECandidate(message) {
            const data = JSON.parse(message.body);
            peerConnection.addIceCandidate(new RTCIceCandidate(data.candidate))
                .catch(error => console.error('Error adding ICE candidate:', error));
        }

        // Remaining functions (chat, mute, video toggle, UI helpers) remain unchanged
        // ...

        // Init page
        window.onload = function() {
            connect();
            document.getElementById('startTime').textContent = new Date().toLocaleTimeString();
        };

        window.addEventListener('beforeunload', function() {
            if (stompClient) {
                stompClient.send("/app/consultation.leave", {}, JSON.stringify({ roomId: roomId }));
            }
        });
    </script>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
