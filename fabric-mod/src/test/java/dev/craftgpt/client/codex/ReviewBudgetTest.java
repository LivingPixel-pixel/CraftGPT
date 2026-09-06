package dev.craftgpt.client.codex;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReviewBudgetTest {
    @Test void effortFourRunsExactlyThreeReviews() {
        ReviewBudget b=new ReviewBudget(3); int calls=1;
        while(b.hasNext()) { calls++; b.completeRound(); }
        assertEquals(4,calls); assertThrows(IllegalStateException.class,b::completeRound);
    }
    @Test void continuationBypassesOnlyItsOwnBusyState() {
        assertFalse(ReviewBudget.exportBlocked(false,false,false,true,true));
        assertTrue(ReviewBudget.exportBlocked(false,false,false,true,false));
        assertTrue(ReviewBudget.exportBlocked(false,true,false,true,true));
        assertTrue(ReviewBudget.exportBlocked(false,false,true,true,true));
    }
    @Test void keepStopsAndAdditionalViewsAreBounded() {
        ReviewBudget b=new ReviewBudget(6);
        assertTrue(b.requestInspection()); assertTrue(b.requestInspection()); assertFalse(b.requestInspection());
        b.keep(); assertFalse(b.hasNext());
    }
    @Test void oneShotHasNoReview() { assertFalse(new ReviewBudget(0).hasNext()); }
}
