package sg.edu.nus.facilityflow.requester;

import java.util.List;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;

/** Browser-controlled Smart Report fields; actor and catalogue data remain server-derived. */
public record RequesterSmartReportInput(
        String roughTitle,
        String roughDescription,
        String roughLocation,
        String selectedCategory,
        String selectedUrgency) {

    /** Applies the approved field bounds before protected context is loaded. */
    SmartReportContext validate() {
        return toContext(List.of());
    }

    SmartReportContext toContext(List<String> activeCategoryLabels) {
        return new SmartReportContext(
                roughTitle, roughDescription, roughLocation,
                selectedCategory, selectedUrgency, activeCategoryLabels);
    }
}
