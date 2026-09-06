package dev.craftgpt.client.portable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;

/** Reviewer output is bounded data. Cameras cannot run commands or change the world. */
public record ReviewDecision(String decision, String baseBuildHash, List<Finding> findings, List<Camera> cameras) {
    public record Finding(String severity, String location, String problem, String evidence, String suggestion) { }
    public record Camera(String label, int rotation, List<Integer> from, List<Integer> to) { }
    public void validate(String expectedHash, int width, int height, int depth) {
        if(expectedHash==null || !expectedHash.equals(baseBuildHash)
            || decision==null || !List.of("keep","repair","inspect").contains(decision)
            || findings==null || findings.size()>24 || cameras==null || cameras.size()>4)
            throw new PortableExchangeException("invalid_review_decision");
        for(Finding f:findings) if(f==null || f.severity()==null || !List.of("error","warning","style").contains(f.severity())
            || !bounded(f.location(),160)||!bounded(f.problem(),500)||!bounded(f.evidence(),500)||!bounded(f.suggestion(),500))
            throw new PortableExchangeException("invalid_review_finding");
        if("inspect".equals(decision) != !cameras.isEmpty()) throw new PortableExchangeException("invalid_review_cameras");
        for(Camera c:cameras) {
            if(c==null||!bounded(c.label(),80)||c.rotation()<0||c.rotation()>3||c.from()==null||c.to()==null
                ||c.from().size()!=3||c.to().size()!=3) throw new PortableExchangeException("invalid_review_camera");
            int[] limits={width,height,depth};
            for(int a=0;a<3;a++) if(c.from().get(a)==null||c.to().get(a)==null||c.from().get(a)<0
                ||c.to().get(a)<c.from().get(a)||c.to().get(a)>=limits[a]) throw new PortableExchangeException("invalid_review_camera");
        }
    }
    private static boolean bounded(String s,int max) {return s!=null&&!s.isBlank()&&s.length()<=max;}
    public static JsonObject schema() {
        return JsonParser.parseString("""
        {"type":"object","additionalProperties":false,"properties":{
          "decision":{"type":"string","enum":["keep","repair","inspect"]},
          "baseBuildHash":{"type":"string"},
          "findings":{"type":"array","maxItems":24,"items":{"type":"object","additionalProperties":false,
            "properties":{"severity":{"type":"string","enum":["error","warning","style"]},"location":{"type":"string"},
            "problem":{"type":"string"},"evidence":{"type":"string"},"suggestion":{"type":"string"}},
            "required":["severity","location","problem","evidence","suggestion"]}},
          "cameras":{"type":"array","maxItems":4,"items":{"type":"object","additionalProperties":false,
            "properties":{"label":{"type":"string"},"rotation":{"type":"integer","enum":[0,1,2,3]},
            "from":{"type":"array","items":{"type":"integer"},"minItems":3,"maxItems":3},
            "to":{"type":"array","items":{"type":"integer"},"minItems":3,"maxItems":3}},
            "required":["label","rotation","from","to"]}}},
          "required":["decision","baseBuildHash","findings","cameras"]}
        """).getAsJsonObject();
    }
}
