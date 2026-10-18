package sg.edu.nus.facilityflow.web;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.technician.TechnicianCommandException;
import sg.edu.nus.facilityflow.technician.TechnicianCommandService;
import sg.edu.nus.facilityflow.technician.TechnicianQueryException;
import sg.edu.nus.facilityflow.technician.TechnicianQueryService;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.RequestDetail;

/** Explicit Technician start-work, Work Log and completion commands. */
@Controller
@RequestMapping("/technician/requests")
public final class TechnicianCommandController {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Singapore");
    private static final String DETAIL_VIEW = "technician/request-detail";
    private static final String INVALID_NUMBER = "Enter a valid whole number.";

    private final TechnicianCommandService commands;
    private final TechnicianQueryService queries;

    public TechnicianCommandController(TechnicianCommandService commands, TechnicianQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping("/{displayId}/start")
    public Object startWork(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId,
            @RequestParam(name = "version", required = false) String version,
            Model model,
            HttpServletResponse response) {
        Map<String, String> errors = new LinkedHashMap<>();
        Long parsedVersion = parseVersion(version, errors);
        if (!errors.isEmpty()) {
            return renderFailure(principal, displayId, "start", version, new SubmittedWorkLog("", ""), errors,
                    List.of("Check the version and refresh this assigned request before trying again."),
                    HttpServletResponse.SC_BAD_REQUEST, model, response);
        }
        try {
            commands.startWork(displayId, parsedVersion);
            return redirect(displayId);
        } catch (TechnicianCommandException exception) {
            int status = switch (exception.code()) {
                case INVALID_INPUT -> HttpServletResponse.SC_BAD_REQUEST;
                case WORK_LOG_REQUIRED -> HttpServletResponse.SC_BAD_REQUEST;
                case NOT_AVAILABLE -> HttpServletResponse.SC_NOT_FOUND;
                case CONFLICT -> HttpServletResponse.SC_CONFLICT;
                case UNAVAILABLE -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            };
            List<String> messages = switch (exception.code()) {
                case INVALID_INPUT -> List.of("Check the version and refresh this assigned request before trying again.");
                case WORK_LOG_REQUIRED -> List.of("A required work record is missing. Review the request and try again.");
                case NOT_AVAILABLE -> List.of("The requested work is not available.");
                case CONFLICT -> List.of("This request changed before the command was saved. Review the current details and try again.");
                case UNAVAILABLE -> List.of("The command could not be saved right now. Please try again later.");
            };
            return renderFailure(principal, displayId, "start", version, new SubmittedWorkLog("", ""), errors,
                    messages, status, model, response);
        }
    }

    @PostMapping("/{displayId}/work-logs")
    public Object appendWorkLog(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId,
            @RequestParam(name = "version", required = false) String version,
            @RequestParam(name = "note", required = false) String note,
            @RequestParam(name = "minutes", required = false) String minutes,
            Model model,
            HttpServletResponse response) {
        String submittedNote = note == null ? "" : note;
        String submittedMinutes = minutes == null ? "" : minutes;
        Map<String, String> errors = new LinkedHashMap<>();
        Long parsedVersion = parseVersion(version, errors);
        Integer parsedMinutes = parseMinutes(minutes, errors);
        if (!errors.isEmpty()) {
            return renderFailure(principal, displayId, "log", version,
                    new SubmittedWorkLog(submittedNote, submittedMinutes), errors,
                    List.of("Correct the highlighted fields and refresh if this request has changed."),
                    HttpServletResponse.SC_BAD_REQUEST, model, response);
        }
        try {
            commands.appendWorkLog(displayId, parsedVersion, submittedNote, parsedMinutes);
            return redirect(displayId);
        } catch (TechnicianCommandException exception) {
            int status = switch (exception.code()) {
                case INVALID_INPUT -> HttpServletResponse.SC_BAD_REQUEST;
                case WORK_LOG_REQUIRED -> HttpServletResponse.SC_BAD_REQUEST;
                case NOT_AVAILABLE -> HttpServletResponse.SC_NOT_FOUND;
                case CONFLICT -> HttpServletResponse.SC_CONFLICT;
                case UNAVAILABLE -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            };
            List<String> messages = switch (exception.code()) {
                case INVALID_INPUT -> List.of("Correct the Work Log fields and try again.");
                case WORK_LOG_REQUIRED -> List.of("A required work record is missing. Review the request and try again.");
                case NOT_AVAILABLE -> List.of("The requested work is not available.");
                case CONFLICT -> List.of("This request changed before the Work Log was saved. Review the current details and try again.");
                case UNAVAILABLE -> List.of("The Work Log could not be saved right now. Please try again later.");
            };
            if (exception.code() == TechnicianCommandException.Code.INVALID_INPUT) {
                if (submittedNote.strip().isEmpty() || submittedNote.strip().codePointCount(0, submittedNote.strip().length()) > 1000) {
                    errors.putIfAbsent("note", "Enter a note containing 1 to 1,000 Unicode code points after trimming.");
                }
                if (parsedMinutes == null && !errors.containsKey("minutes")) {
                    errors.put("minutes", "Enter a whole number of minutes from 1 to 1,440.");
                }
            }
            return renderFailure(principal, displayId, "log", version,
                    new SubmittedWorkLog(submittedNote, submittedMinutes), errors, messages,
                    status, model, response);
        }
    }

    @PostMapping("/{displayId}/complete")
    public Object completeWork(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId,
            @RequestParam(name = "version", required = false) String version,
            @RequestParam(name = "resolutionSummary", required = false) String resolutionSummary,
            Model model,
            HttpServletResponse response) {
        String submittedResolution = resolutionSummary == null ? "" : resolutionSummary;
        Map<String, String> errors = new LinkedHashMap<>();
        Long parsedVersion = parseVersion(version, errors);
        if (!errors.isEmpty()) {
            return renderFailure(principal, displayId, "complete", version, new SubmittedWorkLog("", ""),
                    submittedResolution, errors,
                    List.of("Check the version and refresh this assigned request before trying again."),
                    HttpServletResponse.SC_BAD_REQUEST, model, response);
        }
        try {
            commands.completeWork(displayId, parsedVersion, submittedResolution);
            response.setHeader("Cache-Control", "no-store");
            return redirect(displayId);
        } catch (TechnicianCommandException exception) {
            int status = switch (exception.code()) {
                case INVALID_INPUT -> HttpServletResponse.SC_BAD_REQUEST;
                case WORK_LOG_REQUIRED -> HttpServletResponse.SC_BAD_REQUEST;
                case NOT_AVAILABLE -> HttpServletResponse.SC_NOT_FOUND;
                case CONFLICT -> HttpServletResponse.SC_CONFLICT;
                case UNAVAILABLE -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            };
            List<String> messages = switch (exception.code()) {
                case INVALID_INPUT -> List.of("Correct the Resolution Summary and try again.");
                case WORK_LOG_REQUIRED -> List.of("Add a Work Log before completion.");
                case NOT_AVAILABLE -> List.of("The requested work is not available.");
                case CONFLICT -> List.of("This request changed before completion. Review the current details and try again.");
                case UNAVAILABLE -> List.of("Completion could not be saved right now. Please try again later.");
            };
            if (exception.code() == TechnicianCommandException.Code.INVALID_INPUT) {
                String trimmed = submittedResolution.strip();
                int length = trimmed.codePointCount(0, trimmed.length());
                errors.put("resolutionSummary", length < 10 || length > 2000
                        ? "Enter a Resolution Summary containing 10 to 2,000 Unicode code points after trimming."
                        : "Enter a valid Resolution Summary.");
            }
            return renderFailure(principal, displayId, "complete", version, new SubmittedWorkLog("", ""),
                    submittedResolution, errors, messages, status, model, response);
        }
    }

    private Object renderFailure(CurrentAccountPrincipal principal, String displayId, String action,
                                 String version, SubmittedWorkLog workLog, Map<String, String> fieldErrors,
                                 List<String> messages, int status, Model model, HttpServletResponse response) {
        return renderFailure(principal, displayId, action, version, workLog, "", fieldErrors,
                messages, status, model, response);
    }

    private Object renderFailure(CurrentAccountPrincipal principal, String displayId, String action,
                                 String version, SubmittedWorkLog workLog, String submittedResolution,
                                 Map<String, String> fieldErrors, List<String> messages, int status,
                                 Model model, HttpServletResponse response) {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("title", "Request details");
        model.addAttribute("username", principal.username());
        model.addAttribute("displayName", principal.displayName());
        model.addAttribute("role", principal.role().name());
        model.addAttribute("displayZone", DISPLAY_ZONE);
        model.addAttribute("commandErrors", List.copyOf(messages));
        model.addAttribute("commandFieldErrors", Map.copyOf(fieldErrors));
        model.addAttribute("submittedWorkLog", workLog);
        model.addAttribute("submittedResolution", submittedResolution);
        model.addAttribute("submittedVersion", version == null ? "" : version);
        model.addAttribute("commandAction", action);

        try {
            RequestDetail current = queries.detail(displayId);
            model.addAttribute("detail", current);
            if (status == HttpServletResponse.SC_CONFLICT) {
                model.addAttribute("commandRefreshGuidance", "The current assigned request is shown below. Review its status and submit again only if the action is still available.");
            }
            return new ModelAndView(DETAIL_VIEW, model.asMap());
        } catch (TechnicianQueryException queryException) {
            if (queryException.code() == TechnicianQueryException.Code.UNAVAILABLE) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                model.addAttribute("detail", null);
                model.addAttribute("pageError", "Assigned work is temporarily unavailable. Please try again.");
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("detail", null);
                model.addAttribute("pageError", "The requested work is not available.");
            }
            return new ModelAndView(DETAIL_VIEW, model.asMap());
        }
    }

    private static RedirectView redirect(String displayId) {
        RedirectView redirect = new RedirectView("/technician/requests/" + displayId, true);
        redirect.setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER);
        return redirect;
    }

    private static Long parseVersion(String raw, Map<String, String> errors) {
        if (raw == null || !raw.matches("0|[1-9][0-9]*")) {
            errors.put("version", INVALID_NUMBER);
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException exception) {
            errors.put("version", INVALID_NUMBER);
            return null;
        }
    }

    private static Integer parseMinutes(String raw, Map<String, String> errors) {
        if (raw == null || !raw.matches("[0-9]+")) {
            errors.put("minutes", "Enter a whole number of minutes from 1 to 1,440.");
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 1 || value > 1440) {
                errors.put("minutes", "Enter a whole number of minutes from 1 to 1,440.");
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            errors.put("minutes", "Enter a whole number of minutes from 1 to 1,440.");
            return null;
        }
    }

    public record SubmittedWorkLog(String note, String minutes) {}
}
