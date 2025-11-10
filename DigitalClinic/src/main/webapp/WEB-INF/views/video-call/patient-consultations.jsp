<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>My Consultations - DigitalClinic</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css" rel="stylesheet">
    <style>
        body {
            background: #f4f6f9;
        }
        .card {
            border-radius: 12px;
            box-shadow: 0 3px 6px rgba(0,0,0,0.1);
        }
        .status-badge {
            font-size: 0.85rem;
        }
        .btn-join {
            background-color: #28a745;
            color: white;
        }
        .btn-join:hover {
            background-color: #218838;
        }
        .btn-view {
            background-color: #007bff;
            color: white;
        }
    </style>
</head>
<body>
<div class="container py-5">
    <h2 class="mb-4"><i class="fas fa-calendar-check me-2 text-primary"></i>My Consultations</h2>

    <c:if test="${empty consultations}">
        <div class="alert alert-info text-center">
            <i class="fas fa-info-circle me-2"></i>No consultations found.
        </div>
    </c:if>

    <c:forEach var="consultation" items="${consultations}">
        <div class="card mb-3">
            <div class="card-body d-flex justify-content-between align-items-center">
                <div>
                    <h5 class="card-title mb-1">
                        <i class="fas fa-user-md text-primary me-2"></i>
                        Dr. ${consultation.appointment.doctor.user.fullName}
                    </h5>
                    <p class="mb-0 text-muted">
                        ${consultation.appointment.doctor.specialization}
                    </p>
                    <small class="text-secondary">
                        Scheduled on: ${consultation.scheduledStartTime}
                    </small>
                </div>

                <div class="text-end">
                    <span class="badge 
                        ${consultation.status eq 'COMPLETED' ? 'bg-success' : 
                           consultation.status eq 'IN_PROGRESS' ? 'bg-warning text-dark' : 
                           'bg-secondary'} status-badge">
                        ${consultation.status}
                    </span>
                    <div class="mt-2">
                        <c:choose>
                            <c:when test="${consultation.status eq 'SCHEDULED' || consultation.status eq 'IN_PROGRESS'}">
                                <a href="/video-call/patient/${consultation.id}" class="btn btn-sm btn-join">
                                    <i class="fas fa-video me-1"></i> Join Call
                                </a>
                            </c:when>
                            <c:otherwise>
                                <a href="/consultation/${consultation.id}/details" class="btn btn-sm btn-view">
                                    <i class="fas fa-file-alt me-1"></i> View Details
                                </a>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </div>
        </div>
    </c:forEach>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
