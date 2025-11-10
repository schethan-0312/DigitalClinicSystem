package com.digitalclinic.controller;

import com.digitalclinic.model.*;
import com.digitalclinic.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Optional;

/**
 * Controller handling all Video Consultation workflows:
 * - WebRTC room join (patient & doctor)
 * - Consultation lifecycle (start, complete)
 * - Consultation dashboards (list views)
 */
@Controller
@RequestMapping("/video-call")
public class VideoCallController {

    @Autowired
    private VideoConsultationService videoConsultationService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private UserService userService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private DoctorService doctorService;

    /** WebSocket test endpoint */
    @GetMapping("/websocket-test")
    public String websocketTest() {
        return "video-call/websocket-test";
    }

    // ============================================================
    // 🧑‍🤝‍🧑 PATIENT JOIN VIDEO CALL
    // ============================================================
    @GetMapping("/patient/{roomId}")
    public String patientJoinVideoCall(
            @PathVariable String roomId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String email = authentication.getName();
        User user = userService.findByEmail(email).orElseThrow();

        if (!"PATIENT".equalsIgnoreCase(user.getRole())) {
            redirectAttributes.addFlashAttribute("error", "Access denied: Patients only.");
            return "redirect:/dashboard";
        }

        Optional<VideoConsultation> consultationOpt =
                videoConsultationService.getVideoConsultationByRoomId(roomId);

        if (consultationOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Video consultation not found.");
            return "redirect:/appointments";
        }

        VideoConsultation consultation = consultationOpt.get();

        // Verify access rights
        if (!consultation.getAppointment().getPatient().getUser().getEmail().equals(email)) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to join this consultation.");
            return "redirect:/appointments";
        }

        // Check join window
        if (!videoConsultationService.canJoinConsultation(consultation.getId(), "PATIENT")) {
            redirectAttributes.addFlashAttribute("error",
                    "Consultation cannot be joined right now. Please check the schedule.");
            return "redirect:/appointments/" + consultation.getAppointment().getId();
        }

        // Update consultation status
        videoConsultationService.patientJoined(consultation.getId());

        model.addAttribute("consultation", consultation);
        model.addAttribute("user", user);
        model.addAttribute("patient", patientService.getPatientByEmail(email).orElseThrow());
        model.addAttribute("title", "Video Consultation - Patient Waiting Room");

        // Redirect to patient waiting interface
        return "video-call/patient-waiting";
    }

    // ============================================================
    // 🧑‍⚕️ DOCTOR JOIN VIDEO CALL
    // ============================================================
    @GetMapping("/doctor/{roomId}")
    public String doctorJoinVideoCall(
            @PathVariable String roomId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String email = authentication.getName();
        User user = userService.findByEmail(email).orElseThrow();

        if (!"DOCTOR".equalsIgnoreCase(user.getRole())) {
            redirectAttributes.addFlashAttribute("error", "Access denied: Doctors only.");
            return "redirect:/dashboard";
        }

        Optional<VideoConsultation> consultationOpt =
                videoConsultationService.getVideoConsultationByRoomId(roomId);

        if (consultationOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Video consultation not found.");
            return "redirect:/appointments";
        }

        VideoConsultation consultation = consultationOpt.get();

        // Verify doctor’s access
        if (consultation.getAppointment().getDoctor() == null ||
            !consultation.getAppointment().getDoctor().getUser().getEmail().equals(email)) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to join this consultation.");
            return "redirect:/appointments";
        }

        if (!videoConsultationService.canJoinConsultation(consultation.getId(), "DOCTOR")) {
            redirectAttributes.addFlashAttribute("error",
                    "Consultation cannot be joined right now. Please check the scheduled time.");
            return "redirect:/appointments/" + consultation.getAppointment().getId();
        }

        // Mark doctor as joined
        videoConsultationService.doctorJoined(consultation.getId());

        model.addAttribute("consultation", consultation);
        model.addAttribute("user", user);
        model.addAttribute("doctor", doctorService.getDoctorByEmail(email).orElseThrow());
        model.addAttribute("patient", consultation.getAppointment().getPatient());
        model.addAttribute("title", "Video Consultation - Doctor Panel");

        return "video-call/doctor-panel";
    }

    // ============================================================
    // 🎥 MAIN VIDEO CALL INTERFACE
    // ============================================================
    @GetMapping("/{roomId}")
    public String videoCallInterface(
            @PathVariable String roomId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String email = authentication.getName();
        User user = userService.findByEmail(email).orElseThrow();

        Optional<VideoConsultation> consultationOpt =
                videoConsultationService.getVideoConsultationByRoomId(roomId);

        if (consultationOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Video consultation not found.");
            return "redirect:/appointments";
        }

        VideoConsultation consultation = consultationOpt.get();

        // Access validation
        boolean hasAccess = false;
        if ("PATIENT".equalsIgnoreCase(user.getRole()) &&
            consultation.getAppointment().getPatient().getUser().getEmail().equals(email)) {
            hasAccess = true;
        } else if ("DOCTOR".equalsIgnoreCase(user.getRole()) &&
                   consultation.getAppointment().getDoctor() != null &&
                   consultation.getAppointment().getDoctor().getUser().getEmail().equals(email)) {
            hasAccess = true;
        }

        if (!hasAccess) {
            redirectAttributes.addFlashAttribute("error", "Access denied to this consultation.");
            return "redirect:/dashboard";
        }

        model.addAttribute("consultation", consultation);
        model.addAttribute("user", user);
        model.addAttribute("title", "Video Consultation");

        if ("PATIENT".equalsIgnoreCase(user.getRole())) {
            model.addAttribute("patient", patientService.getPatientByEmail(email).orElseThrow());
            return "video-call/patient-interface";
        } else {
            model.addAttribute("doctor", doctorService.getDoctorByEmail(email).orElseThrow());
            model.addAttribute("patient", consultation.getAppointment().getPatient());
            return "video-call/doctor-interface";
        }
    }

    // ============================================================
    // ▶️ START CONSULTATION (Doctor action)
    // ============================================================
    @PostMapping("/{consultationId}/start")
    public String startConsultation(
            @PathVariable Long consultationId,
            RedirectAttributes redirectAttributes) {

        try {
            VideoConsultation consultation = videoConsultationService.startConsultation(consultationId);
            redirectAttributes.addFlashAttribute("success", "Consultation started successfully.");
            return "redirect:/video-call/" + consultation.getRoomId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to start consultation: " + e.getMessage());
            return "redirect:/appointments";
        }
    }

    // ============================================================
    // ✅ COMPLETE CONSULTATION
    // ============================================================
    @PostMapping("/{consultationId}/complete")
    public String completeConsultation(
            @PathVariable Long consultationId,
            @RequestParam(required = false) String prescription,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            VideoConsultation consultation = videoConsultationService.completeConsultation(consultationId);

            if (prescription != null && !prescription.trim().isEmpty()) {
                appointmentService.completeAppointment(
                        consultation.getAppointment().getId(),
                        prescription,
                        notes != null ? notes : "Consultation completed via video call"
                );
            }

            redirectAttributes.addFlashAttribute("success", "Consultation completed successfully.");
            return "redirect:/appointments/" + consultation.getAppointment().getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to complete consultation: " + e.getMessage());
            return "redirect:/video-call/" + consultationId;
        }
    }

    // ============================================================
    // 🧑‍⚕️ VIDEO CONSULTATION LISTS (Patient & Doctor)
    // ============================================================
    @GetMapping("/patient/consultations")
    public String patientConsultations(Authentication authentication, Model model) {
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElseThrow();
        Patient patient = patientService.getPatientByEmail(email).orElseThrow();

        var consultations = videoConsultationService.getPatientConsultations(patient.getUser().getId());

        model.addAttribute("consultations", consultations);
        model.addAttribute("user", user);
        model.addAttribute("patient", patient);
        model.addAttribute("title", "My Video Consultations");

        return "video-call/patient-consultations";
    }

    @GetMapping("/doctor/consultations")
    public String doctorConsultations(Authentication authentication, Model model) {
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElseThrow();
        Doctor doctor = doctorService.getDoctorByEmail(email).orElseThrow();

        var consultations = videoConsultationService.getDoctorConsultations(doctor.getUser().getId());

        model.addAttribute("consultations", consultations);
        model.addAttribute("user", user);
        model.addAttribute("doctor", doctor);
        model.addAttribute("title", "My Video Consultations");

        return "video-call/doctor-consultations";
    }

    // ============================================================
    // 🧪 TEST PAGE
    // ============================================================
    @GetMapping("/test")
    public String videoTestPage(Authentication authentication, Model model) {
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElseThrow();

        model.addAttribute("user", user);
        model.addAttribute("title", "Video Call Test");

        return "video-call/test";
    }
}
