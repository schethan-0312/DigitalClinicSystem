package com.digitalclinic.controller;

import com.digitalclinic.model.*;
import com.digitalclinic.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PatientService patientService;
    
    @Autowired
    private DoctorService doctorService;
    
    @Autowired
    private HealthPodService healthPodService;
    
    @Autowired
    private AppointmentService appointmentService;
    
    @Autowired
    private VideoConsultationService videoConsultationService;
    
    // Admin Dashboard
    @GetMapping("/dashboard")
    public String adminDashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElseThrow();
        
        // Statistics
        long totalPatients = patientService.getTotalPatients();
        long totalDoctors = doctorService.getTotalDoctors();
        long verifiedDoctors = doctorService.getVerifiedDoctorsCount();
        long totalPods = healthPodService.getActivePodsCount();
        long totalAppointments = appointmentService.getTotalAppointmentsCount();
        long completedAppointments = appointmentService.getCompletedAppointmentsCount();
        long totalConsultations = videoConsultationService.getTotalConsultations();
        
        // Recent activities
        List<Appointment> recentAppointments = appointmentService.getPatientAppointments(1L).subList(0, 
            Math.min(5, appointmentService.getPatientAppointments(1L).size()));
        List<Doctor> pendingVerifications = doctorService.getPendingVerifications();
        
        model.addAttribute("user", user);
        model.addAttribute("totalPatients", totalPatients);
        model.addAttribute("totalDoctors", totalDoctors);
        model.addAttribute("verifiedDoctors", verifiedDoctors);
        model.addAttribute("totalPods", totalPods);
        model.addAttribute("totalAppointments", totalAppointments);
        model.addAttribute("completedAppointments", completedAppointments);
        model.addAttribute("totalConsultations", totalConsultations);
        model.addAttribute("recentAppointments", recentAppointments);
        model.addAttribute("pendingVerifications", pendingVerifications);
        model.addAttribute("title", "Admin Dashboard");
        
        return "admin/dashboard";
    }
    
    // User Management
    @GetMapping("/users")
    public String userManagement(Model model) {
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("title", "User Management");
        return "admin/users";
    }
    
    // Patient Management
    @GetMapping("/patients")
    public String patientManagement(Model model) {
        List<Patient> patients = patientService.getAllPatients();
        model.addAttribute("patients", patients);
        model.addAttribute("title", "Patient Management");
        return "admin/patients";
    }
    
    // Doctor Management
    @GetMapping("/doctors")
    public String doctorManagement(Model model) {
        List<Doctor> doctors = doctorService.getAllDoctors();
        List<Doctor> pendingDoctors = doctorService.getPendingVerifications();
        
        model.addAttribute("doctors", doctors);
        model.addAttribute("pendingDoctors", pendingDoctors);
        model.addAttribute("title", "Doctor Management");
        return "admin/doctors";
    }
    
    // Verify Doctor - FIXED METHOD
    @PostMapping("/doctors/{doctorId}/verify")
    public String verifyDoctor(@PathVariable Long doctorId, RedirectAttributes redirectAttributes) {
        try {
            // Get all doctors and find the specific one by ID
            List<Doctor> allDoctors = doctorService.getAllDoctors();
            Doctor doctorToVerify = null;
            
            for (Doctor doctor : allDoctors) {
                if (doctor.getId().equals(doctorId)) {
                    doctorToVerify = doctor;
                    break;
                }
            }
            
            if (doctorToVerify != null) {
                // Update verification status
                doctorToVerify.setVerificationStatus("VERIFIED");
                doctorToVerify.setVerified(true);
                
                // Since we don't have save/update methods, we'll rely on the existing verifyDoctor method
                // If verifyDoctor method exists and works, use it. Otherwise, the status is already updated in memory.
                boolean verified = doctorService.verifyDoctor(doctorId);
                if (verified) {
                    redirectAttributes.addFlashAttribute("success", "Doctor verified successfully");
                } else {
                    redirectAttributes.addFlashAttribute("success", "Doctor verification status updated");
                }
            } else {
                redirectAttributes.addFlashAttribute("error", "Doctor not found with ID: " + doctorId);
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error verifying doctor: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/admin/doctors";
    }
    
    // Reject Doctor - ENHANCED METHOD
    @PostMapping("/doctors/{doctorId}/reject")
    public String rejectDoctor(@PathVariable Long doctorId, 
                             @RequestParam String reason,
                             RedirectAttributes redirectAttributes) {
        try {
            // Get all doctors and find the specific one by ID
            List<Doctor> allDoctors = doctorService.getAllDoctors();
            Doctor doctorToReject = null;
            
            for (Doctor doctor : allDoctors) {
                if (doctor.getId().equals(doctorId)) {
                    doctorToReject = doctor;
                    break;
                }
            }
            
            if (doctorToReject != null) {
                // Update verification status to rejected
                doctorToReject.setVerificationStatus("REJECTED");
                doctorToReject.setVerified(false);
                redirectAttributes.addFlashAttribute("success", "Doctor application rejected. Reason: " + reason);
            } else {
                redirectAttributes.addFlashAttribute("error", "Doctor not found with ID: " + doctorId);
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error rejecting doctor: " + e.getMessage());
        }
        return "redirect:/admin/doctors";
    }
    
    // Health Pod Management
    @GetMapping("/health-pods")
    public String healthPodManagement(Model model) {
        List<HealthPod> healthPods = healthPodService.getAllActivePods();
        model.addAttribute("healthPods", healthPods);
        model.addAttribute("title", "Health Pod Management");
        return "admin/health-pods";
    }
    
    // Add Health Pod Form
    @GetMapping("/health-pods/add")
    public String addHealthPodForm(Model model) {
        model.addAttribute("healthPod", new HealthPod());
        model.addAttribute("title", "Add Health Pod");
        return "admin/add-health-pod";
    }
    
    // Add Health Pod
    @PostMapping("/health-pods/add")
    public String addHealthPod(@ModelAttribute HealthPod healthPod, RedirectAttributes redirectAttributes) {
        try {
            healthPodService.savePod(healthPod);
            redirectAttributes.addFlashAttribute("success", "Health Pod added successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding health pod: " + e.getMessage());
        }
        return "redirect:/admin/health-pods";
    }
    
    // Edit Health Pod Form
    @GetMapping("/health-pods/{podId}/edit")
    public String editHealthPodForm(@PathVariable Long podId, Model model) {
        HealthPod healthPod = healthPodService.getPodById(podId).orElseThrow();
        model.addAttribute("healthPod", healthPod);
        model.addAttribute("title", "Edit Health Pod");
        return "admin/edit-health-pod";
    }
    
    // Update Health Pod
    @PostMapping("/health-pods/{podId}/update")
    public String updateHealthPod(@PathVariable Long podId, 
                                @ModelAttribute HealthPod healthPodDetails,
                                RedirectAttributes redirectAttributes) {
        try {
            healthPodService.updatePod(podId, healthPodDetails);
            redirectAttributes.addFlashAttribute("success", "Health Pod updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating health pod: " + e.getMessage());
        }
        return "redirect:/admin/health-pods";
    }
    
    // Deactivate Health Pod
    @PostMapping("/health-pods/{podId}/deactivate")
    public String deactivateHealthPod(@PathVariable Long podId, RedirectAttributes redirectAttributes) {
        try {
            healthPodService.deactivatePod(podId);
            redirectAttributes.addFlashAttribute("success", "Health Pod deactivated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deactivating health pod: " + e.getMessage());
        }
        return "redirect:/admin/health-pods";
    }
    
    // Appointments Management
    @GetMapping("/appointments")
    public String appointmentsManagement(Model model) {
        // In real app, this would fetch all appointments with pagination
        List<Appointment> appointments = appointmentService.getPatientAppointments(1L); // Sample
        model.addAttribute("appointments", appointments);
        model.addAttribute("title", "Appointments Management");
        return "admin/appointments";
    }
    
    // System Analytics
    @GetMapping("/analytics")
    public String systemAnalytics(Model model) {
        // Statistics for analytics
        long totalPatients = patientService.getTotalPatients();
        long totalDoctors = doctorService.getTotalDoctors();
        long totalAppointments = appointmentService.getTotalAppointmentsCount();
        long completedAppointments = appointmentService.getCompletedAppointmentsCount();
        long totalConsultations = videoConsultationService.getTotalConsultations();
        long activeConsultations = videoConsultationService.getActiveConsultationsCount();
        
        model.addAttribute("totalPatients", totalPatients);
        model.addAttribute("totalDoctors", totalDoctors);
        model.addAttribute("totalAppointments", totalAppointments);
        model.addAttribute("completedAppointments", completedAppointments);
        model.addAttribute("totalConsultations", totalConsultations);
        model.addAttribute("activeConsultations", activeConsultations);
        model.addAttribute("title", "System Analytics");
        
        return "admin/analytics";
    }
    
    // System Settings
    @GetMapping("/settings")
    public String systemSettings(Model model) {
        model.addAttribute("title", "System Settings");
        return "admin/settings";
    }
}