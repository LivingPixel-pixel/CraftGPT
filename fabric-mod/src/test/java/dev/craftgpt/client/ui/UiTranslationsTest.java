package dev.craftgpt.client.ui;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
class UiTranslationsTest {
    @Test void everyNewUiKeyExistsInEnglishAndGerman() throws Exception{
        var en=read("en_us");var de=read("de_de");
        var regex=Pattern.compile("\"(craftgpt\\.ui\\.[a-z_]+)\"");
        try(var files=Files.list(Path.of("src/client/java/dev/craftgpt/client/ui"))){
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList()){
                var matches=regex.matcher(Files.readString(file));
                while(matches.find()){
                    var key=matches.group(1);
                    assertTrue(en.has(key),"Missing English key: "+key);assertTrue(de.has(key),"Missing German key: "+key);
                    assertFalse(en.get(key).getAsString().isBlank());assertFalse(de.get(key).getAsString().isBlank());
                    assertEquals(count(en.get(key).getAsString()),count(de.get(key).getAsString()),key);
                }
            }
        }
    }
    @Test void newCopyHasNoEmDashes() throws Exception{
        for(String language:List.of("en_us","de_de"))
            for(var e:read(language).entrySet())if(e.getKey().startsWith("craftgpt.ui."))
                assertFalse(e.getValue().getAsString().contains("\u2014"),e.getKey());
    }
    private JsonObject read(String language)throws Exception{
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/craftgpt/lang/"+language+".json"))).getAsJsonObject();
    }
    private long count(String text){return Pattern.compile("%[sd]").matcher(text).results().count();}
}
