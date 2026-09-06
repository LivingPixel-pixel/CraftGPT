package dev.craftgpt.client.portable;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReviewDecisionTest {
    private final String hash="a".repeat(64);
    @Test void keepNeedsNoInventedFindings() {
        assertDoesNotThrow(()->new ReviewDecision("keep",hash,List.of(),List.of()).validate(hash,8,8,8));
    }
    @Test void rejectsStaleBase() {
        assertThrows(PortableExchangeException.class,()->new ReviewDecision("repair",hash,List.of(),List.of()).validate("b".repeat(64),8,8,8));
    }
    @Test void nullDecisionIsDiagnosable() {
        assertThrows(PortableExchangeException.class,()->new ReviewDecision(null,hash,List.of(),List.of()).validate(hash,8,8,8));
    }
    @Test void nullSeverityIsDiagnosable() {
        var f=new ReviewDecision.Finding(null,"roof","hole","visible gap","fill gap");
        assertThrows(PortableExchangeException.class,()->new ReviewDecision("repair",hash,List.of(f),List.of()).validate(hash,8,8,8));
    }
    @Test void inspectRequiresViews() {
        assertThrows(PortableExchangeException.class,()->new ReviewDecision("inspect",hash,List.of(),List.of()).validate(hash,8,8,8));
    }
    @Test void validatesAllCameraBounds() {
        for(int axis=0;axis<3;axis++) {
            var to=new ArrayList<>(List.of(7,7,7)); to.set(axis,8);
            var c=new ReviewDecision.Camera("section",0,List.of(0,0,0),to);
            assertThrows(PortableExchangeException.class,()->new ReviewDecision("inspect",hash,List.of(),List.of(c)).validate(hash,8,8,8));
        }
    }
    @Test void supportsFourBoundedAngles() {
        var cameras=java.util.stream.IntStream.range(0,4).mapToObj(r->new ReviewDecision.Camera("angle "+r,r,List.of(0,0,0),List.of(7,7,7))).toList();
        assertDoesNotThrow(()->new ReviewDecision("inspect",hash,List.of(),cameras).validate(hash,8,8,8));
    }
    @Test void rejectsFifthCameraAndUnboundedText() {
        var c=new ReviewDecision.Camera("view",0,List.of(0,0,0),List.of(7,7,7));
        assertThrows(PortableExchangeException.class,()->new ReviewDecision("inspect",hash,List.of(),Collections.nCopies(5,c)).validate(hash,8,8,8));
        var f=new ReviewDecision.Finding("style","roof","x".repeat(501),"observed","fix");
        assertThrows(PortableExchangeException.class,()->new ReviewDecision("repair",hash,List.of(f),List.of()).validate(hash,8,8,8));
    }
}
