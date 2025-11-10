<%-- video-call/doctor-interface.jsp (Updated & Stable Version) --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>${title}</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <script src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/js/all.min.js"></script>
    <style>
        body { background: #f7f9fb; }
        .video-container { display: flex; gap: 20px; margin-bottom: 20px; }
        .video-wrapper { flex: 1; border: 2px solid #ddd; border-radius: 8px; padding: 10px; background: #fff; }
        video { width: 100%; max-width: 500px; height: auto; border-radius: 4px; background: #000; }
        .controls { margin: 20px 0; text-align: center; }
        .chat-container { border: 1px solid #ddd; border-radius: 8px; padding: 15px; max-height: 300px; overflow-y: auto; background: #fff; }
        .message { margin: 10px 0; padding: 8px; border-radius: 4px; }
        .message.system { background-color: #f0f0f0; font-style: italic; text-align: center; }
        .message.doctor { background-color: #d4edda; text-align: right; }
        .message.patient { background-color: #e2e3e5; }
        .status { margin-top: 10px; }
    </style>
</head>
<body>
<div class="container py-4">
    <h2 class="mb-3 text-primary">
        <i class="fas fa-video me-2"></i>Video Consultation - Dr. ${doctor.firstName} ${doctor.lastName}
    </h2>

    <!-- Patient Information -->
    <div class="patient-info card mb-4">
        <div class="card-body">
            <h5 class="card-title">Patient: ${patient.firstName} ${patient.lastName}</h5>
            <p class="card-text mb-0">
                <strong>Email:</strong> ${patient.user.email}<br>
                <strong>Phone:</strong> ${patient.phoneNumber}<br>
                <strong>Appointment Time:</strong> ${consultation.scheduledStartTime}
            </p>
        </div>
    </div>

    <!-- Video Container -->
    <div class="video-container">
        <div class="video-wrapper">
            <h6 class="fw-bold mb-2">Your Video</h6>
            <video id="localVideo" autoplay muted playsinline></video>
            <small id="localVideoStatus" class="text-success">● Live</small>
        </div>
        <div class="video-wrapper">
            <h6 class="fw-bold mb-2">Patient Video</h6>
            <video id="remoteVideo" autoplay playsinline></video>
            <small id="remoteVideoStatus" class="text-muted">Waiting for patient...</small>
        </div>
    </div>

    <!-- Controls -->
    <div class="controls">
        <button id="toggleVideo" class="btn btn-outline-primary me-2" onclick="toggleVideo()">
            <i class="fas fa-video"></i> Video On
        </button>
        <button id="toggleAudio" class="btn btn-outline-primary me-2" onclick="toggleAudio()">
            <i class="fas fa-microphone"></i> Audio On
        </button>
        <button id="startConsultation" class="btn btn-success me-2" onclick="startConsultation()">
            <i class="fas fa-play"></i> Start Consultation
        </button>
        <button id="endConsultation" class="btn btn-danger" onclick="endConsultation()">
            <i class="fas fa-stop"></i> End Consultation
        </button>
    </div>

    <!-- Status -->
    <div class="alert alert-info status">
        <strong>Status:</strong> <span id="consultationStatus">${consultation.status}</span>
        &nbsp; | &nbsp; <strong>Participants:</strong> <span id="participantCount">1</span>
    </div>

    <!-- Chat Section -->
    <div class="row">
        <div class="col-md-8">
            <div class="chat-container mb-2">
                <h5 class="fw-bold">Consultation Chat</h5>
                <div id="chatMessages"></div>
            </div>
            <div class="input-group">
                <input type="text" id="messageInput" class="form-control" placeholder="Type your message...">
                <button class="btn btn-primary" onclick="sendMessage()">Send</button>
            </div>
        </div>
        <div class="col-md-4">
            <div class="card">
                <div class="card-header bg-light"><strong>Quick Actions</strong></div>
                <div class="card-body">
                    <button class="btn btn-outline-secondary btn-sm mb-2 w-100" onclick="sendQuickMessage('Please describe your symptoms in detail.')">
                        Ask about symptoms
                    </button>
                    <button class="btn btn-outline-secondary btn-sm mb-2 w-100" onclick="sendQuickMessage('Do you have any allergies to medications?')">
                        Ask about allergies
                    </button>
                    <button class="btn btn-outline-secondary btn-sm mb-2 w-100" onclick="sendQuickMessage('I will prescribe you medication for your condition.')">
                        Prescription info
                    </button>
                </div>
            </div>
        </div>
    </div>

    <!-- Consultation Notes -->
    <div class="card mt-4">
        <div class="card-header"><strong>Consultation Notes</strong></div>
        <div class="card-body">
            <form id="consultationForm" action="/video-call/${consultation.id}/complete" method="post">
                <div class="mb-3">
                    <label for="prescription" class="form-label">Prescription</label>
                    <textarea class="form-control" id="prescription" name="prescription" rows="3"
                              placeholder="Enter prescription details..."></textarea>
                </div>
                <div class="mb-3">
                    <label for="notes" class="form-label">Clinical Notes</label>
                    <textarea class="form-control" id="notes" name="notes" rows="3"
                              placeholder="Enter clinical notes..."></textarea>
                </div>
            </form>
        </div>
    </div>
</div>

<!-- ====================== JavaScript Section ====================== -->
<script>
    const roomId = '${consultation.roomId}';
    const userId = '${user.email}';
    const userName = 'Dr. ${doctor.firstName} ${doctor.lastName}';
    const userType = 'DOCTOR';
    const patientUserId = '${patient.user.email}';

    let stompClient = null;
    let localStream = null;
    let peerConnection = null;
    let isVideoEnabled = true;
    let isAudioEnabled = true;

    // ====================== WebSocket Connection ======================
    function connect() {
        const socket = new SockJS('/ws-video-consultation');
        stompClient = Stomp.over(socket);

        stompClient.connect({}, function (frame) {
            console.log('Connected: ' + frame);
            stompClient.send("/app/consultation.join", {}, JSON.stringify({
                roomId, userType, userName
            }));
            subscribeToTopics();
            initializeWebRTC();
        }, function (error) {
            console.error('WebSocket error:', error);
            setTimeout(connect, 5000);
        });
    }

    function subscribeToTopics() {
        stompClient.subscribe('/topic/consultation.' + roomId + '.participants', msg => handleParticipantsUpdate(JSON.parse(msg.body)));
        stompClient.subscribe('/user/queue/webrtc.answer', msg => handleWebRTCAnswer(JSON.parse(msg.body)));
        stompClient.subscribe('/user/queue/webrtc.ice-candidate', msg => handleICECandidate(JSON.parse(msg.body)));
        stompClient.subscribe('/topic/consultation.' + roomId + '.chat', msg => handleChatMessage(JSON.parse(msg.body)));
        stompClient.subscribe('/topic/consultation.' + roomId + '.end', msg => handleConsultationEnd(JSON.parse(msg.body)));
    }

    // ====================== WebRTC ======================
    async function initializeWebRTC() {
        try {
            localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
            document.getElementById('localVideo').srcObject = localStream;
            createPeerConnection();
        } catch (err) {
            alert('Cannot access camera/mic: ' + err.message);
        }
    }

    function createPeerConnection() {
        const configuration = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'turn:your.turn.server:3478', username: 'turnuser', credential: 'turnpass' }
            ]
        };
        peerConnection = new RTCPeerConnection(configuration);

        peerConnection.oniceconnectionstatechange = () => console.log('ICE state:', peerConnection.iceConnectionState);

        localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));

        peerConnection.ontrack = e => {
            const remoteVideo = document.getElementById('remoteVideo');
            remoteVideo.srcObject = e.streams[0];
            document.getElementById('remoteVideoStatus').textContent = '● Live';
            document.getElementById('remoteVideoStatus').className = 'text-success';
        };

        peerConnection.onicecandidate = e => {
            if (e.candidate) {
                stompClient.send("/app/consultation.webrtc.ice-candidate", {}, JSON.stringify({
                    roomId, targetUserId: patientUserId, candidate: e.candidate
                }));
            }
        };

        // Auto-offer to patient after join
        setTimeout(createAndSendOffer, 2000);
    }

    async function createAndSendOffer() {
        const offer = await peerConnection.createOffer();
        await peerConnection.setLocalDescription(offer);
        stompClient.send("/app/consultation.webrtc.offer", {}, JSON.stringify({
            roomId, targetUserId: patientUserId, offer
        }));
    }

    async function handleWebRTCAnswer(data) {
        await peerConnection.setRemoteDescription(data.answer);
    }

    function handleICECandidate(data) {
        peerConnection.addIceCandidate(new RTCIceCandidate(data.candidate))
            .catch(e => console.error('ICE error:', e));
    }

    // ====================== Controls ======================
    function toggleVideo() {
        isVideoEnabled = !isVideoEnabled;
        localStream.getVideoTracks().forEach(t => t.enabled = isVideoEnabled);
        document.getElementById('toggleVideo').innerHTML = isVideoEnabled ?
            '<i class="fas fa-video"></i> Video On' :
            '<i class="fas fa-video-slash"></i> Video Off';
    }

    function toggleAudio() {
        isAudioEnabled = !isAudioEnabled;
        localStream.getAudioTracks().forEach(t => t.enabled = isAudioEnabled);
        document.getElementById('toggleAudio').innerHTML = isAudioEnabled ?
            '<i class="fas fa-microphone"></i> Audio On' :
            '<i class="fas fa-microphone-slash"></i> Audio Off';
    }

    function startConsultation() {
        document.getElementById('consultationStatus').textContent = 'IN_PROGRESS';
        stompClient.send("/app/consultation.status.update", {}, JSON.stringify({
            roomId, status: 'IN_PROGRESS'
        }));
    }

    function endConsultation() {
        if (confirm('End this consultation?')) {
            document.getElementById('consultationForm').submit();
        }
    }

    // ====================== Chat ======================
    function sendMessage() {
        const msg = document.getElementById('messageInput').value.trim();
        if (!msg || !stompClient) return;
        stompClient.send("/app/consultation.chat", {}, JSON.stringify({ roomId, content: msg }));
        document.getElementById('messageInput').value = '';
    }

    function sendQuickMessage(msg) {
        document.getElementById('messageInput').value = msg;
        sendMessage();
    }

    function handleChatMessage(msg) {
        const chatBox = document.getElementById('chatMessages');
        const div = document.createElement('div');
        div.className = 'message ' + msg.senderType.toLowerCase();
        div.textContent = msg.senderName + ': ' + msg.content;
        chatBox.appendChild(div);
        chatBox.scrollTop = chatBox.scrollHeight;
    }

    // ====================== Utilities ======================
    function handleParticipantsUpdate(data) {
        document.getElementById('participantCount').textContent = data.count || 1;
    }

    function handleConsultationEnd() {
        alert('Consultation ended by patient.');
        window.location.href = '/video-call/doctor/consultations';
    }

    // ====================== Init ======================
    window.onload = connect;
    window.onbeforeunload = function () {
        if (stompClient) stompClient.send("/app/consultation.leave", {}, JSON.stringify({ roomId }));
        if (localStream) localStream.getTracks().forEach(t => t.stop());
    };
</script>
</body>
</html>
