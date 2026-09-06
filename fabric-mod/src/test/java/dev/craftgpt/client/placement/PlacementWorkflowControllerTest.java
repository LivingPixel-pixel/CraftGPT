package dev.craftgpt.client.placement;

import dev.craftgpt.network.PlacementStatusPayload;
import dev.craftgpt.placement.PlacementStatusCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlacementWorkflowControllerTest {
    private static final String PLACEMENT_ID = "55555555-5555-5555-5555-555555555555";
    private static final String BUILD_ID = "33333333-3333-3333-3333-333333333333";

    @Test
    void tracksPlacementProgressAndEnablesRecovery() {
        PlacementWorkflowController controller = new PlacementWorkflowController();

        controller.handle(status(PlacementStatusCodes.PLACING, 4, 10, 0));
        assertTrue(controller.busy());
        assertTrue(controller.placed());
        assertTrue(controller.undoAvailable());
        assertTrue(controller.placedForBuild(BUILD_ID));

        controller.handle(status(PlacementStatusCodes.PLACED, 10, 10, 0));
        assertFalse(controller.busy());
        assertTrue(controller.placed());
        assertTrue(controller.undoAvailable());
    }

    @Test
    void fullUndoRestoresPlaceabilityWhileConflictedUndoRemainsRecoverable() {
        PlacementWorkflowController controller = new PlacementWorkflowController();
        controller.handle(status(PlacementStatusCodes.PLACED, 10, 10, 0));
        controller.handle(status(PlacementStatusCodes.UNDOING, 5, 10, 0));
        assertTrue(controller.busy());

        controller.handle(status(PlacementStatusCodes.UNDONE, 10, 10, 0));
        assertFalse(controller.placed());
        assertFalse(controller.undoAvailable());

        controller.handle(status(PlacementStatusCodes.UNDONE_WITH_CONFLICTS, 10, 10, 2));
        assertFalse(controller.placed());
        assertTrue(controller.undoAvailable());
    }

    @Test
    void interruptedPlacementAlwaysOffersUndo() {
        PlacementWorkflowController controller = new PlacementWorkflowController();
        controller.handle(status(PlacementStatusCodes.INTERRUPTED, 3, 10, 0));

        assertFalse(controller.busy());
        assertTrue(controller.placed());
        assertTrue(controller.undoAvailable());
    }

    @Test
    void anOlderPlacementDoesNotMarkANewBuildAsPlaced() {
        PlacementWorkflowController controller = new PlacementWorkflowController();
        controller.handle(status(PlacementStatusCodes.PLACED, 10, 10, 0));

        assertTrue(controller.placedForBuild(BUILD_ID));
        assertTrue(controller.latestMatchesBuild(BUILD_ID));
        assertFalse(controller.placedForBuild("44444444-4444-4444-4444-444444444444"));
        assertFalse(controller.latestMatchesBuild("44444444-4444-4444-4444-444444444444"));
    }

    @Test
    void redoProgressReturnsPlacementToAnUndoableState() {
        PlacementWorkflowController controller = new PlacementWorkflowController();
        controller.handle(status(PlacementStatusCodes.UNDONE, 10, 10, 0));
        controller.handle(status(PlacementStatusCodes.REDOING, 4, 10, 0));
        assertTrue(controller.busy());
        assertTrue(controller.placed());

        controller.handle(status(PlacementStatusCodes.REDONE_WITH_CONFLICTS, 10, 10, 2));
        assertFalse(controller.busy());
        assertTrue(controller.placed());
        assertTrue(controller.undoAvailable());
    }

    private PlacementStatusPayload status(String status, int processed, int total, int conflicts) {
        return new PlacementStatusPayload(
            "request-1",
            PLACEMENT_ID,
            BUILD_ID,
            status,
            processed,
            total,
            conflicts
        );
    }
}
