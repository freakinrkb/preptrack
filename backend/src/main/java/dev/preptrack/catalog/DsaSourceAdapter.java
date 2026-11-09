package dev.preptrack.catalog;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DsaSourceAdapter {
    private static final String ARCHIVE = "https://github.com/liquidslr/leetcode-company-wise-problems/archive/refs/heads/main.zip";
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20)).build();

    public Snapshot fetch() {
        try {
            var request = HttpRequest.newBuilder(URI.create(ARCHIVE)).timeout(Duration.ofSeconds(90))
                .header("Accept", "application/zip").header("User-Agent", "PrepTrack-catalog-sync").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IllegalStateException("Source download returned HTTP " + response.statusCode());
            byte[] zip = response.body();
            if (zip.length > 100_000_000) throw new IllegalStateException("Source archive exceeds the 100 MB safety limit");
            List<Item> items = new ArrayList<>();
            try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().matches("[^/]+/[^/]+/5\\. All\\.csv$")) continue;
                    String[] path = entry.getName().split("/", 3);
                    String company = path[1];
                    byte[] csv = readBounded(in, 15_000_000);
                    parseCompanyCsv(company, new String(csv, StandardCharsets.UTF_8), items);
                }
            }
            if (items.isEmpty()) throw new IllegalStateException("No company ‘All’ CSV files were found in the source archive");
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));
            return new Snapshot(fingerprint, items);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the DSA source archive: " + e.getMessage(), e);
        }
    }

    private static byte[] readBounded(ZipInputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int total = 0, n;
        while ((n = in.read(buffer)) > 0) {
            total += n; if (total > limit) throw new IOException("A company data file exceeds the safety limit");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    static void parseCompanyCsv(String company, String csv, List<Item> result) {
        List<List<String>> rows = parseCsv(csv);
        if (rows.isEmpty()) return;
        Map<String,Integer> columns = new HashMap<>();
        for (int i=0;i<rows.getFirst().size();i++) columns.put(rows.getFirst().get(i).trim().toLowerCase(Locale.ROOT),i);
        // Titles and canonical problem URLs identify an importable item. The
        // upstream CSV omits optional fields for some companies, so treat
        // difficulty and topic tags as enrichment rather than schema requirements.
        for (String required : List.of("title","link"))
            if (!columns.containsKey(required)) throw new IllegalStateException("Unexpected CSV format for " + company + ": missing " + required);
        for (int row=1;row<rows.size();row++) {
            List<String> cells=rows.get(row); if(cells.size()<rows.getFirst().size()) continue;
            String title=value(cells,columns,"title").trim(), url=value(cells,columns,"link").trim();
            if(title.isBlank() || !url.matches("https://leetcode\\.com/problems/[a-z0-9-]+/?")) continue;
            List<String> topics=Arrays.stream(optionalValue(cells,columns,"topics").split(","))
                .map(String::trim).filter(s->!s.isBlank()).distinct().toList();
            Map<String,String> metadata=new LinkedHashMap<>();
            for(String field:List.of("difficulty","frequency","acceptance rate"))
                if(columns.containsKey(field)) metadata.put(field,optionalValue(cells,columns,field).trim());
            result.add(new Item(company,title,url.replaceAll("/$",""),topics,metadata));
        }
    }

    private static String value(List<String> row,Map<String,Integer> cols,String key){int i=cols.get(key);return i<row.size()?row.get(i):"";}
    private static String optionalValue(List<String> row,Map<String,Integer> cols,String key){Integer i=cols.get(key);return i==null||i>=row.size()?"":row.get(i);}

    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>(); StringBuilder field=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<csv.length();i++){
            char c=csv.charAt(i);
            if(quoted){if(c=='"'&&i+1<csv.length()&&csv.charAt(i+1)=='"'){field.append('"');i++;}else if(c=='"')quoted=false;else field.append(c);}
            else if(c=='"')quoted=true;
            else if(c==','){row.add(field.toString());field.setLength(0);}
            else if(c=='\n'||c=='\r'){if(c=='\r'&&i+1<csv.length()&&csv.charAt(i+1)=='\n')i++;row.add(field.toString());field.setLength(0);if(row.stream().anyMatch(s->!s.isBlank()))rows.add(row);row=new ArrayList<>();}
            else field.append(c);
        }
        if(field.length()>0||!row.isEmpty()){row.add(field.toString());rows.add(row);}
        return rows;
    }

    public record Item(String company,String title,String url,List<String> topics,Map<String,String> metadata){
        public String externalKey(){return company+"/5. All.csv/"+url;}
    }
    public record Snapshot(String fingerprint,List<Item> items){}
}
