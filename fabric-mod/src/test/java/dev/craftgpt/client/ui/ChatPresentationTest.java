package dev.craftgpt.client.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatPresentationTest {
    @Test void structuredBuildShowsReadableTitleAndSummary(){
        String json="{\"plan\":{\"title\":\"Meadow cottage\"},\"build\":{\"summary\":\"Adds a porch.\",\"operations\":[\"0,0,0,0\"]}}";
        assertEquals("Meadow cottage\nAdds a porch.",ChatPresentation.readable(json));
        assertTrue(json.contains("operations"));
    }
    @Test void repairFindingsRemainVisible(){
        String json="{\"review\":{\"decision\":\"repair\",\"findings\":[{\"problem\":\"Blocked door.\",\"suggestion\":\"Clear the entrance.\"}]}}";
        assertEquals("\n[repair]\nBlocked door. Clear the entrance.",ChatPresentation.readable(json));
    }
    @Test void keepIsVisibleWithoutABuild(){assertEquals("\n[keep]",ChatPresentation.readable("{\"build\":null,\"review\":{\"decision\":\"keep\"}}"));}
    @Test void malformedJsonIsNeverLost(){String s="{invalid output";assertEquals(s,ChatPresentation.readable(s));}
    @Test void ordinaryTextIsUnchanged(){assertEquals("Working on the roof.",ChatPresentation.readable("Working on the roof."));}
    @Test void unrelatedJsonIsUnchanged(){String s="{\"message\":\"help\"}";assertEquals(s,ChatPresentation.readable(s));}
    @Test void invalidFieldTypesFallBackToRaw(){String s="{\"build\":{\"summary\":[]}}";assertEquals(s,ChatPresentation.readable(s));}
    @Test void nullIsSafe(){assertEquals("",ChatPresentation.readable(null));}
}
