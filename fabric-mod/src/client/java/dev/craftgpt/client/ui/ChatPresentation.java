package dev.craftgpt.client.ui;
import com.google.gson.JsonParser;
/** Read-only presentation of structured output. The raw transcript remains unchanged. */
public final class ChatPresentation {
    private ChatPresentation(){}
    public static String readable(String text){
        if(text==null)return "";
        if(!text.stripLeading().startsWith("{"))return text;
        try{
            var root=JsonParser.parseString(text).getAsJsonObject();
            if(!root.has("build")&&!root.has("review"))return text;
            StringBuilder shown=new StringBuilder();
            if(root.has("plan")&&root.get("plan").isJsonObject()){
                var p=root.getAsJsonObject("plan");
                if(p.has("title"))shown.append(p.get("title").getAsString()).append("\n");
            }
            if(root.has("build")&&root.get("build").isJsonObject()){
                var b=root.getAsJsonObject("build");
                if(b.has("summary"))shown.append(b.get("summary").getAsString());
            }
            if(root.has("review")&&root.get("review").isJsonObject()){
                var review=root.getAsJsonObject("review");
                if(review.has("decision"))shown.append("\n[").append(review.get("decision").getAsString()).append("]");
                if(review.has("findings")&&review.get("findings").isJsonArray())
                    for(var finding:review.getAsJsonArray("findings")){
                        if(!finding.isJsonObject())continue;
                        var f=finding.getAsJsonObject();
                        if(f.has("problem"))shown.append("\n").append(f.get("problem").getAsString());
                        if(f.has("suggestion"))shown.append(" ").append(f.get("suggestion").getAsString());
                    }
            }
            return shown.toString().isBlank()?text:shown.toString();
        }catch(RuntimeException ignored){return text;}
    }
}
