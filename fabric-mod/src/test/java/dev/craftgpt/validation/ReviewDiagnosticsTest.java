package dev.craftgpt.validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReviewDiagnosticsTest {
    @Test void scopedPatchFailureExplainsWhatMustBePreserved(){
        var p=ValidationProblemCatalog.problem("patch_outside_player_edit_scope","build.operations");
        assertTrue(p.cause().contains("outside"));
        assertTrue(p.suggestion().contains("materials unchanged"));
    }
    @Test void missingReviewExplainsTheBaseHash(){
        var p=ValidationProblemCatalog.problem("missing_review_decision","review");
        assertTrue(p.suggestion().contains("currentBuildHash"));
        assertTrue(p.suggestion().contains("keep, repair or inspect"));
    }
}
