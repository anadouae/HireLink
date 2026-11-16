package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.BillingPromotionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Promotions catalogue — ADMIN uniquement (MFA déjà exigée par le rôle admin). */
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMINISTRATEUR')")
@RestController
@RequestMapping("/api/v1/admin/promotions")
public class AdminBillingPromotionController {

    private final BillingPromotionService promotions;

    public AdminBillingPromotionController(BillingPromotionService promotions) {
        this.promotions = promotions;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return promotions.list();
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history() {
        return promotions.history();
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal User admin, @RequestBody Map<String, Object> body) {
        return promotions.create(admin, body);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@AuthenticationPrincipal User admin,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        return promotions.update(admin, id, body);
    }

    @PostMapping("/{id}/activate")
    public Map<String, Object> activate(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        return promotions.setActive(admin, id, true);
    }

    @PostMapping("/{id}/deactivate")
    public Map<String, Object> deactivate(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        return promotions.setActive(admin, id, false);
    }

    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam String plan,
                                       @RequestParam(defaultValue = "month") String cycle,
                                       @RequestParam String promo) {
        return promotions.preview(plan, cycle, promo);
    }
}
