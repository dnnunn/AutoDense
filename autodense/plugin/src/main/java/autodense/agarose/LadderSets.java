package autodense.agarose;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;

public final class LadderSets {
    static Map<String,List<Integer>> cache;
    @SuppressWarnings("unchecked")
    public static List<Integer> load(String name) throws Exception {
        if (cache==null){
            ObjectMapper om = new ObjectMapper();
            try (InputStream is = LadderSets.class.getResourceAsStream("/config/ladder_sets.json")) {
                cache = om.readValue(is, Map.class);
            }
        }
        List<Integer> list = cache.get(name);
        if (list==null) throw new IllegalArgumentException("Unknown ladder: "+name);
        return list;
    }
}